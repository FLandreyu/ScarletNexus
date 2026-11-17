const fs = require("node:fs");
const fsp = require("node:fs/promises");
const crypto = require("node:crypto");
const { Transform } = require("node:stream");
const { pipeline } = require("node:stream/promises");
const { spawn } = require("node:child_process");

async function digestFile(file) {
  const digest = crypto.createHash("sha256");
  for await (const chunk of fs.createReadStream(file)) digest.update(chunk);
  return digest.digest("hex");
}

async function encryptStream(input, destination, key) {
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv(
    "aes-256-gcm",
    Buffer.from(key, "base64url"),
    nonce,
  );
  const digest = crypto.createHash("sha256");
  const ignoreEarlyError = () => {};
  input.on("error", ignoreEarlyError);
  try {
    // The tag is patched only after a successful pipeline. Partial files are
    // ciphertext with an invalid tag, never plaintext or a completed backup.
    await fsp.writeFile(
      destination,
      Buffer.concat([Buffer.from("SNDUENC1"), nonce, Buffer.alloc(16)]),
      { flag: "wx", mode: 0o600 },
    );
    await pipeline(
      input,
      new Transform({
        transform(chunk, _encoding, callback) {
          digest.update(chunk);
          callback(null, chunk);
        },
      }),
      cipher,
      fs.createWriteStream(destination, { flags: "r+", start: 36 }),
    );
    const output = await fsp.open(destination, "r+");
    try {
      await output.write(cipher.getAuthTag(), 0, 16, 20);
      await output.sync();
    } finally {
      await output.close();
    }
    return {
      plainSha256: digest.digest("hex"),
      cipherSha256: await digestFile(destination),
    };
  } catch (error) {
    input.destroy();
    throw error;
  } finally {
    input.removeListener("error", ignoreEarlyError);
  }
}

async function encryptCommand(file, args, { env, destination, key }) {
  const child = spawn(file, args, {
    env,
    windowsHide: true,
    stdio: ["ignore", "pipe", "ignore"],
  });
  const closed = new Promise((resolve) => child.once("close", resolve));
  const exited = new Promise((resolve, reject) => {
    child.once("error", () =>
      reject(new Error("Backup exporter could not start")),
    );
    child.once("exit", (code) =>
      code === 0
        ? resolve()
        : reject(new Error("Backup exporter failed; migration was blocked")),
    );
  });
  const timer = setTimeout(() => child.kill(), 600000);
  const encrypted = encryptStream(child.stdout, destination, key);
  try {
    const [result] = await Promise.all([encrypted, exited]);
    return result;
  } catch {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await Promise.allSettled([encrypted, exited, closed]);
    throw new Error("Encrypted database backup failed; migration was blocked");
  } finally {
    clearTimeout(timer);
  }
}

module.exports = { digestFile, encryptStream, encryptCommand };
