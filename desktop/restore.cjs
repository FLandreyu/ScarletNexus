const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { Writable } = require("node:stream");
const { pipeline } = require("node:stream/promises");
const { spawn } = require("node:child_process");
const { digestFile } = require("./backup.cjs");
const checksum = (value) =>
  typeof value === "string" && /^[a-f0-9]{64}$/.test(value);

async function assertFreshTarget(restore, directory) {
  const source = await fsp.realpath(restore.backup);
  const parent = await fsp.realpath(path.dirname(path.resolve(directory)));
  const target = path.join(parent, path.basename(path.resolve(directory)));
  const inside = (a, b) =>
    a.toLowerCase() === b.toLowerCase() ||
    a.toLowerCase().startsWith(b.toLowerCase() + path.sep);
  if (inside(target, source) || inside(source, target))
    throw new Error("Restore target and original backup must be separate");
  try {
    if (
      (await fsp.lstat(target)).isSymbolicLink() ||
      (await fsp.readdir(target)).length
    )
      throw new Error("Restore requires a new empty learning directory");
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  for (let ancestor = parent; ; ancestor = path.dirname(ancestor)) {
    try {
      await fsp.access(path.join(ancestor, "owner.json"));
      throw new Error(
        "Restore target must not be inside an existing managed data directory",
      );
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    if (path.dirname(ancestor) === ancestor) break;
  }
}

function safeName(name) {
  if (
    typeof name !== "string" ||
    !name ||
    path.isAbsolute(name) ||
    name.includes("\\") ||
    /[<>:"|?*\x00-\x1f]/.test(name) ||
    name
      .split("/")
      .some(
        (part) =>
          !part ||
          part === "." ||
          part === ".." ||
          /[. ]$/.test(part) ||
          /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(part),
      )
  )
    throw new Error("Unsafe backup attachment path");
  return name;
}

async function decryptInto(file, key, destination) {
  const ignoreEarlyError = () => {};
  destination.on("error", ignoreEarlyError);
  try {
    const handle = await fsp.open(file, "r");
    const header = Buffer.alloc(36);
    try {
      const result = await handle.read(header, 0, 36, 0);
      if (
        result.bytesRead !== 36 ||
        header.subarray(0, 8).toString() !== "SNDUENC1"
      )
        throw new Error("Invalid encrypted backup envelope");
    } finally {
      await handle.close();
    }
    const decipher = crypto.createDecipheriv(
      "aes-256-gcm",
      Buffer.from(key, "base64url"),
      header.subarray(8, 20),
    );
    decipher.setAuthTag(header.subarray(20, 36));
    const digest = crypto.createHash("sha256");
    decipher.on("data", (data) => digest.update(data));
    await pipeline(
      fs.createReadStream(file, { start: 36 }),
      decipher,
      destination,
    );
    return digest.digest("hex");
  } catch (error) {
    destination.destroy();
    throw error;
  } finally {
    destination.removeListener("error", ignoreEarlyError);
  }
}

async function stageRestore({ backup, keyFile }, root) {
  const source = await fsp.realpath(backup);
  if (
    source === root ||
    source.startsWith(root + path.sep) ||
    root.startsWith(source + path.sep)
  )
    throw new Error("Backup must be separate from the new learning directory");
  const inventory = path.join(source, "manifest.json");
  if (
    (await fsp.lstat(inventory)).isSymbolicLink() ||
    !(await fsp.realpath(inventory)).startsWith(source + path.sep)
  )
    throw new Error("Backup manifest must remain inside its directory");
  if ((await fsp.stat(inventory)).size > 16 * 1024 * 1024)
    throw new Error("Backup manifest exceeds 16 MiB");
  const metadata = JSON.parse(await fsp.readFile(inventory, "utf8"));
  if ((await fsp.stat(keyFile)).size > 65536)
    throw new Error("Invalid protected key file size");
  const storedKey = JSON.parse(await fsp.readFile(keyFile, "utf8"));
  const key = storedKey.encryption;
  if (
    metadata.format !== "scarlet-desktop-upgrade-backup/v1" ||
    metadata.encrypted !== true ||
    metadata.cipher !== "AES-256-GCM" ||
    !checksum(metadata.databasePlainSha256) ||
    !checksum(metadata.databaseCipherSha256) ||
    !checksum(metadata.fromApplicationHash) ||
    !checksum(metadata.toApplicationHash) ||
    !metadata.attachments ||
    Array.isArray(metadata.attachments) ||
    typeof metadata.attachments !== "object" ||
    typeof key !== "string" ||
    !/^[A-Za-z0-9_-]{43}$/.test(key) ||
    Buffer.from(key, "base64url").length !== 32
  )
    throw new Error("Unsupported completed backup or key file");
  const staging = path.join(root, "restore-input");
  await fsp.mkdir(staging);
  const entries = [
    {
      source: "database.dump.enc",
      target: "database.dump.enc",
      plainSha256: metadata.databasePlainSha256,
      cipherSha256: metadata.databaseCipherSha256,
    },
  ];
  for (const [name, hashes] of Object.entries(metadata.attachments)) {
    safeName(name);
    if (
      !hashes ||
      !checksum(hashes.plainSha256) ||
      !checksum(hashes.cipherSha256)
    )
      throw new Error("Invalid attachment inventory");
    entries.push({
      source: "attachments/" + name + ".enc",
      target: "attachments/" + name + ".enc",
      attachment: name,
      plainSha256: hashes.plainSha256,
      cipherSha256: hashes.cipherSha256,
    });
  }
  for (const entry of entries) {
    const origin = path.join(source, entry.source);
    const canonical = await fsp.realpath(origin);
    if (
      !canonical.startsWith(source + path.sep) ||
      (await fsp.lstat(origin)).isSymbolicLink()
    )
      throw new Error("Backup files must remain inside their directory");
    entry.file = path.join(staging, entry.target);
    await fsp.mkdir(path.dirname(entry.file), { recursive: true });
    await fsp.copyFile(origin, entry.file, fs.constants.COPYFILE_EXCL);
    if ((await digestFile(entry.file)) !== entry.cipherSha256)
      throw new Error("Backup ciphertext checksum differs");
    try {
      const actual = await decryptInto(
        entry.file,
        key,
        new Writable({
          write(_data, _encoding, callback) {
            callback();
          },
        }),
      );
      if (actual !== entry.plainSha256)
        throw new Error("Plaintext checksum differs");
    } catch {
      throw new Error(
        "Backup authentication failed; verify the original key and backup",
      );
    }
  }
  await fsp.writeFile(
    path.join(staging, "manifest.json"),
    JSON.stringify(metadata),
    { flag: "wx", mode: 0o600 },
  );
  return { entries, key };
}

async function restoreDatabase(file, args, environment, staged) {
  const child = spawn(file, args, {
    env: environment,
    windowsHide: true,
    stdio: ["pipe", "ignore", "ignore"],
  });
  const closed = new Promise((resolve) => child.once("close", resolve));
  const exited = new Promise((resolve, reject) => {
    child.once("error", () =>
      reject(new Error("Restore process could not start")),
    );
    child.once("exit", (code) =>
      code === 0 ? resolve() : reject(new Error("Restore process failed")),
    );
  });
  const entry = staged.entries[0];
  const decrypted = decryptInto(entry.file, staged.key, child.stdin);
  const timer = setTimeout(() => child.kill(), 600000);
  try {
    const [actual] = await Promise.all([decrypted, exited]);
    if (actual !== entry.plainSha256)
      throw new Error("Restored dump checksum differs");
  } catch {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await Promise.allSettled([decrypted, exited, closed]);
    throw new Error("Database restore failed; original data was preserved");
  } finally {
    clearTimeout(timer);
  }
}

async function restoreAttachments(staged, root) {
  for (const entry of staged.entries.filter((entry) => entry.attachment)) {
    const destination = path.join(root, "attachments", entry.attachment);
    await fsp.mkdir(path.dirname(destination), { recursive: true });
    const actual = await decryptInto(
      entry.file,
      staged.key,
      fs.createWriteStream(destination, { flags: "wx", mode: 0o600 }),
    );
    if (actual !== entry.plainSha256)
      throw new Error("Restored attachment checksum differs");
  }
}

async function verifyRestoredReferences(records, staged, root) {
  for (const record of records) {
    if (
      typeof record.name !== "string" ||
      !/^[0-9a-f-]{36}\.png$/.test(record.name)
    )
      throw new Error("Invalid restored attachment reference");
    const entry = staged.entries.find(
      (entry) => entry.attachment === record.name,
    );
    if (
      !entry ||
      entry.plainSha256 !== record.checksum ||
      (await fsp.stat(path.join(root, "attachments", record.name))).size !==
        Number(record.bytes)
    )
      throw new Error(
        "Restored database attachment is missing or inconsistent",
      );
  }
}

module.exports = {
  assertFreshTarget,
  safeName,
  decryptInto,
  stageRestore,
  restoreDatabase,
  restoreAttachments,
  verifyRestoredReferences,
};
