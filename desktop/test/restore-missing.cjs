const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { startRuntime } = require("../runtime.cjs");
process.chdir(path.resolve(__dirname, "../.."));
(async () => {
  const bundle = await fs.realpath(process.env.DESKTOP_TEST_RUNTIME || "");
  const fixtureFile = await fs.realpath(
    process.env.DESKTOP_RESTORE_FIXTURE || "",
  );
  const boundary = path.resolve(".runtime") + path.sep;
  assert.ok(bundle.startsWith(boundary) && fixtureFile.startsWith(boundary));
  const fixture = JSON.parse(await fs.readFile(fixtureFile, "utf8"));
  const folder = path.resolve(
    ".runtime",
    "restore-missing-" + crypto.randomUUID(),
  );
  await fs.mkdir(folder);
  const backup = path.join(folder, "inconsistent-backup");
  await fs.cp(fixture.backup, backup, {
    recursive: true,
    errorOnExist: true,
    force: false,
  });
  const inventory = path.join(backup, "manifest.json");
  const metadata = JSON.parse(await fs.readFile(inventory, "utf8"));
  assert.ok(Object.keys(metadata.attachments).length > 0);
  metadata.attachments = {};
  await fs.writeFile(inventory, JSON.stringify(metadata));
  const directory = path.join(folder, "candidate");
  await assert.rejects(
    startRuntime({
      bundle,
      directory,
      restore: { backup, keyFile: fixture.keyFile },
    }),
    /attachment is missing or inconsistent/,
  );
  await assert.rejects(
    startRuntime({ bundle, directory }),
    /unfinished restore/,
  );
  await assert.rejects(
    fs.access(path.join(directory, "postgres-17/postmaster.pid")),
  );
  await assert.rejects(
    fs.access(path.join(directory, "restore.completed.json")),
  );
  console.log(
    JSON.stringify({
      authenticatedDumpWithMissingImageRejected: true,
      incompleteTargetCannotOpen: true,
      ownedDatabaseStopped: true,
      folder,
    }),
  );
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
