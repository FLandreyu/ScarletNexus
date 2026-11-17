const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { Readable, Writable } = require("node:stream");
const { pipeline } = require("node:stream/promises");
const { encryptStream, digestFile } = require("../backup.cjs");
const { decryptInto } = require("../restore.cjs");
(async () => {
  const root = path.resolve(
    __dirname,
    "../../.runtime",
    "backup-large-" + crypto.randomUUID(),
  );
  await fsp.mkdir(root, { recursive: true });
  const source = path.join(root, "source.bin"),
    target = path.join(root, "cipher.enc");
  const block = crypto.randomBytes(1024 * 1024);
  await pipeline(
    Readable.from(
      (async function* () {
        for (let i = 0; i < 512; i++) yield block;
      })(),
    ),
    fs.createWriteStream(source, { flags: "wx" }),
  );
  const baseline = process.memoryUsage().rss;
  let peak = baseline;
  const sample = () => {
    peak = Math.max(peak, process.memoryUsage().rss);
  };
  const timer = setInterval(sample, 5);
  try {
    const key = crypto.randomBytes(32).toString("base64url");
    const result = await encryptStream(
      fs.createReadStream(source),
      target,
      key,
    );
    const plain = await decryptInto(
      target,
      key,
      new Writable({
        write(_chunk, _encoding, callback) {
          callback();
        },
      }),
    );
    assert.equal(plain, result.plainSha256);
    assert.equal(result.plainSha256, await digestFile(source));
    sample();
    assert.ok(
      peak - baseline < 160 * 1024 * 1024,
      "512MiB backup must not require a file-sized allocation",
    );
    console.log(
      JSON.stringify({
        sourceBytes: 512 * 1024 * 1024,
        authenticatedRoundTrip: true,
        extraPeakRssBytes: peak - baseline,
        directory: root,
      }),
    );
  } finally {
    clearInterval(timer);
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
