const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { Readable } = require("node:stream");
const { encryptStream } = require("../backup.cjs");
const { assertFreshTarget, safeName, stageRestore } = require("../restore.cjs");
async function fixture() {
  const root = path.resolve(
    __dirname,
    "../../.runtime",
    "restore-unit-" + crypto.randomUUID(),
  );
  const backup = path.join(root, "backup");
  await fs.mkdir(backup, { recursive: true });
  const key = crypto.randomBytes(32).toString("base64url");
  const hashes = await encryptStream(
    Readable.from([Buffer.from("PGDMP-test-envelope")]),
    path.join(backup, "database.dump.enc"),
    key,
  );
  const metadata = {
    format: "scarlet-desktop-upgrade-backup/v1",
    encrypted: true,
    cipher: "AES-256-GCM",
    fromApplicationHash: "a".repeat(64),
    toApplicationHash: "b".repeat(64),
    databasePlainSha256: hashes.plainSha256,
    databaseCipherSha256: hashes.cipherSha256,
    attachments: {},
  };
  await fs.writeFile(
    path.join(backup, "manifest.json"),
    JSON.stringify(metadata),
  );
  const keyFile = path.join(root, "key.json");
  await fs.writeFile(keyFile, JSON.stringify({ encryption: key }));
  const target = path.join(root, "target");
  await fs.mkdir(target);
  return { root, backup, keyFile, metadata, target };
}
test("recovery rejects traversal, occupied targets and source/target overlap", async () => {
  for (const name of [
    "../secret",
    "a/../x",
    "/x",
    "x\\y",
    "x:stream",
    "x/",
    "x.",
    "x ",
    "./x",
    "NUL.txt",
    "CON",
    "folder/LPT1.enc",
  ])
    assert.throws(() => safeName(name));
  const f = await fixture();
  await assertFreshTarget(f, f.target);
  await fs.writeFile(path.join(f.target, "keep"), "original");
  await assert.rejects(assertFreshTarget(f, f.target), /new empty/);
  await assert.rejects(
    assertFreshTarget(f, path.join(f.backup, "nested")),
    /separate/,
  );
  assert.equal(
    await fs.readFile(path.join(f.target, "keep"), "utf8"),
    "original",
  );
});
test("recovery authenticates all staged files and cannot trust extra descriptor fields", async () => {
  const f = await fixture();
  const staged = await stageRestore(f, f.target);
  assert.equal(staged.entries.length, 1);
  const second = await fixture();
  await fs.writeFile(
    second.keyFile,
    JSON.stringify({
      encryption: crypto.randomBytes(32).toString("base64url"),
    }),
  );
  await assert.rejects(
    stageRestore(second, second.target),
    /authentication failed/,
  );
  const third = await fixture();
  third.metadata.attachments["proof.bin"] = {
    plainSha256: "a".repeat(64),
    cipherSha256: "b".repeat(64),
    target: "../../escaped",
    source: third.keyFile,
  };
  await fs.writeFile(
    path.join(third.backup, "manifest.json"),
    JSON.stringify(third.metadata),
  );
  await assert.rejects(stageRestore(third, third.target), /ENOENT/);
  await assert.rejects(fs.access(path.join(third.root, "escaped")));
});
