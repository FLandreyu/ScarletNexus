const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { Readable } = require("node:stream");
const { encryptStream, encryptCommand, digestFile } = require("../backup.cjs");
const { decryptBackup } = require("../runtime.cjs");
async function output() {
  const directory = path.resolve(
    __dirname,
    "../../.runtime",
    "backup-test-" + crypto.randomUUID(),
  );
  await fs.mkdir(directory, { recursive: true });
  return path.join(directory, "proof.enc");
}
test("streamed envelopes remain compatible and protect an existing destination", async () => {
  const target = await output();
  const key = crypto.randomBytes(32).toString("base64url");
  const original = crypto.randomBytes(150000);
  const result = await encryptStream(
    Readable.from([original.subarray(0, 100), original.subarray(100)]),
    target,
    key,
  );
  assert.deepEqual(decryptBackup(await fs.readFile(target), key), original);
  assert.equal(
    result.plainSha256,
    crypto.createHash("sha256").update(original).digest("hex"),
  );
  assert.equal(result.cipherSha256, await digestFile(target));
  await assert.rejects(
    encryptStream(Readable.from([Buffer.from("replacement")]), target, key),
    /EEXIST/,
  );
  assert.equal(await digestFile(target), result.cipherSha256);
});
test("source failure invalidates its envelope and exporter failure blocks completion", async () => {
  const target = await output();
  const key = crypto.randomBytes(32).toString("base64url");
  const source = Readable.from(
    (async function* () {
      yield Buffer.from("private-data");
      throw new Error("source failed");
    })(),
  );
  await assert.rejects(encryptStream(source, target, key), /source failed/);
  assert.throws(() =>
    decryptBackup(require("node:fs").readFileSync(target), key),
  );
  await assert.rejects(
    encryptCommand(
      process.execPath,
      ["-e", "process.stdout.write('partial');process.exitCode=9"],
      { destination: await output(), key, env: process.env },
    ),
    /migration was blocked/,
  );
  await assert.rejects(
    encryptCommand(
      path.join(path.dirname(target), "missing-exporter.exe"),
      [],
      { destination: await output(), key, env: process.env },
    ),
    /migration was blocked/,
  );
});
