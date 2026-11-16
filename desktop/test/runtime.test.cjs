const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");
const {
  verifyBundle,
  prepareData,
  acquireLock,
  validateAdmin,
  cleanEnvironment,
  encryptBackup,
  decryptBackup,
} = require("../runtime.cjs");

async function temporary(t) {
  const directory = await fs.mkdtemp(
    path.join(os.tmpdir(), "scarlet-runtime-test-"),
  );
  t.after(async () => {
    const absolute = await fs.realpath(directory);
    assert.ok(absolute.startsWith((await fs.realpath(os.tmpdir())) + path.sep));
    assert.ok(path.basename(absolute).startsWith("scarlet-runtime-test-"));
    await fs.rm(absolute, { recursive: true, force: true });
  });
  return directory;
}

test("bundle hash coverage rejects missing, modified and escaping assets", async (t) => {
  const root = await temporary(t);
  const names = [
    "backend.jar",
    "roles.sql",
    "assets/index.html",
    "java/bin/java.exe",
    "postgres/bin/initdb.exe",
    "postgres/bin/pg_ctl.exe",
    "postgres/bin/psql.exe",
    "postgres/bin/pg_dump.exe",
  ];
  const files = {};
  for (const name of names) {
    await fs.mkdir(path.dirname(path.join(root, name)), { recursive: true });
    await fs.writeFile(
      path.join(root, name),
      "unit fixture, not a native runtime",
    );
    files[name] = crypto
      .createHash("sha256")
      .update("unit fixture, not a native runtime")
      .digest("hex");
  }
  const manifest = {
    schema: 1,
    platform: "win32-x64",
    postgresMajor: "17",
    schemaVersion: "12",
    release: "test",
    files,
  };
  const save = () =>
    fs.writeFile(path.join(root, "manifest.json"), JSON.stringify(manifest));
  await save();
  await verifyBundle(root);
  await fs.writeFile(path.join(root, "backend.jar"), "changed");
  await assert.rejects(verifyBundle(root), /integrity/);
  await fs.writeFile(
    path.join(root, "backend.jar"),
    "unit fixture, not a native runtime",
  );
  files["../escape"] = "a".repeat(64);
  await save();
  await assert.rejects(verifyBundle(root), /Unsafe/);
  delete files["../escape"];
  delete files["postgres/bin/initdb.exe"];
  await save();
  await assert.rejects(verifyBundle(root), /incomplete/);
});

test("unknown storage is preserved and concurrent owners cannot share a data root", async (t) => {
  const base = await temporary(t);
  const unknown = path.join(base, "unknown");
  await fs.mkdir(unknown);
  await fs.writeFile(path.join(unknown, "keep.txt"), "preserve");
  await assert.rejects(prepareData(unknown, cleanEnvironment(base)), /unknown/);
  assert.equal(
    await fs.readFile(path.join(unknown, "keep.txt"), "utf8"),
    "preserve",
  );
  const managed = await prepareData(
    path.join(base, "managed"),
    cleanEnvironment(base),
  );
  const unlock = await acquireLock(managed);
  await assert.rejects(acquireLock(managed), /in use/);
  await unlock();
  await (
    await acquireLock(managed)
  )();
});

test("administrator validation enforces byte limits and line boundaries", () => {
  validateAdmin({
    email: "test@example.test",
    password: "correct-horse-battery",
  });
  for (const account of [
    undefined,
    { email: "invalid", password: "correct-horse-battery" },
    { email: "a@example.test", password: "short" },
    { email: "a@example.test", password: "密".repeat(25) },
    { email: "a@example.test", password: "password-with\nnewline" },
  ]) {
    assert.throws(() => validateAdmin(account));
  }
});

test("backup envelopes authenticate bytes and refuse modified content or wrong keys", () => {
  const key = crypto.randomBytes(32).toString("base64url");
  const data = Buffer.from("private learning content");
  const encrypted = encryptBackup(data, key);
  assert.equal(encrypted.includes(data), false);
  assert.deepEqual(decryptBackup(encrypted, key), data);
  encrypted[encrypted.length - 1] ^= 1;
  assert.throws(() => decryptBackup(encrypted, key));
  assert.throws(() =>
    decryptBackup(
      encryptBackup(data, key),
      crypto.randomBytes(32).toString("base64url"),
    ),
  );
  assert.throws(() => decryptBackup(Buffer.from("invalid"), key));
});

test("child environment does not inherit AI keys, Node options or Java injection", () => {
  const environment = cleanEnvironment(path.resolve("runtime-fixture"));
  for (const key of [
    "AI_API_KEY",
    "OPENAI_API_KEY",
    "NODE_OPTIONS",
    "JAVA_TOOL_OPTIONS",
    "ELECTRON_RUN_AS_NODE",
    "PGOPTIONS",
  ]) {
    assert.equal(environment[key], undefined);
  }
  assert.match(environment.JAVA_HOME, /runtime-fixture/);
});
