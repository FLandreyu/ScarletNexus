// Native Windows integration: no Docker, external Java, temporary trust, or fake APIs.
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const { startRuntime, decryptBackup } = require("../runtime.cjs");
process.chdir(path.resolve(__dirname, "../.."));
const run = promisify(execFile);

async function login(origin, email, password) {
  const csrfResponse = await fetch(origin + "/api/v1/auth/csrf");
  const csrf = await csrfResponse.json();
  const cookies = csrfResponse.headers
    .getSetCookie()
    .map((cookie) => cookie.split(";")[0])
    .join("; ");
  const response = await fetch(origin + "/api/v1/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookies,
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ email, password }),
  });
  assert.equal(response.status, 200, "real administrator login");
  return (
    response.headers
      .getSetCookie()
      .map((cookie) => cookie.split(";")[0])
      .join("; ") || cookies
  );
}

(async () => {
  const bundle = path.resolve(process.env.DESKTOP_TEST_RUNTIME || "");
  assert.ok(
    bundle.startsWith(path.resolve(".runtime") + path.sep),
    "Use an isolated assembled test runtime",
  );
  const directory = process.env.DESKTOP_TEST_DATA
    ? path.resolve(process.env.DESKTOP_TEST_DATA)
    : path.resolve(
        ".runtime",
        "desktop-d2-native-" + crypto.randomUUID(),
        "data",
      );
  assert.ok(directory.startsWith(path.resolve(".runtime") + path.sep));
  const admin = {
    email: "desktop-runtime@example.test",
    password: crypto.randomBytes(24).toString("base64url"),
  };
  const options = {
    bundle,
    directory,
    admin,
    onStatus: (message) => console.log(message),
  };
  let runtime;
  try {
    runtime = await startRuntime(options);
    const credentials = JSON.parse(
      await fs.readFile(path.join(directory, "secrets/database.json"), "utf8"),
    );
    const psql = path.join(bundle, "postgres/bin/psql.exe");
    async function query(sql, role = "control", database = "scarlet_nexus") {
      return (
        await run(
          psql,
          [
            "-X",
            "-w",
            "-h",
            "127.0.0.1",
            "-p",
            String(runtime.databasePort),
            "-U",
            role === "control" ? "scarlet_control" : "scarlet_" + role,
            "-d",
            database,
            "-Atc",
            sql,
          ],
          {
            windowsHide: true,
            env: { ...process.env, PGPASSWORD: credentials[role] },
          },
        )
      ).stdout.trim();
    }
    assert.equal(
      await query("SELECT current_user;", "runtime"),
      "scarlet_runtime",
    );
    assert.equal(
      await query("SELECT count(*) FROM flyway_schema_history WHERE success;"),
      "12",
    );
    assert.equal(await query("SELECT count(*) FROM users;"), "1");
    assert.equal(
      await query(
        "SELECT count(*) FROM pg_hba_file_rules WHERE auth_method IS NOT NULL AND auth_method <> 'scram-sha-256';",
      ),
      "0",
    );
    assert.equal(
      await query(
        "SELECT count(*) FROM pg_roles WHERE rolname IN ('scarlet_runtime','scarlet_migrator','scarlet_backup') AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls);",
      ),
      "0",
    );
    await assert.rejects(
      run(
        psql,
        [
          "-X",
          "-w",
          "-h",
          "127.0.0.1",
          "-p",
          String(runtime.databasePort),
          "-U",
          "scarlet_runtime",
          "-d",
          "scarlet_nexus",
          "-Atc",
          "SELECT 1;",
        ],
        {
          windowsHide: true,
          env: { ...process.env, PGPASSWORD: "wrong-test-only-password" },
        },
      ),
    );
    await login(runtime.origin, admin.email, admin.password);
    await assert.rejects(query("CREATE TABLE forbidden(id int);", "runtime"));
    await assert.rejects(query("DELETE FROM users;", "backup"));
    await assert.rejects(startRuntime(options), /in use/);
    await query(
      "CREATE TABLE desktop_restart_proof(value text); INSERT INTO desktop_restart_proof VALUES ('persisted');",
      "migrator",
    );
    await fs.writeFile(
      path.join(directory, "attachments", "upgrade-proof.bin"),
      "private-attachment-proof",
    );
    const oldOrigin = runtime.origin;
    await runtime.stop();
    runtime = undefined;
    await assert.rejects(fetch(oldOrigin));
    options.admin = undefined;
    runtime = await startRuntime(options);
    await login(runtime.origin, admin.email, admin.password);
    assert.equal(
      await query("SELECT value FROM desktop_restart_proof;"),
      "persisted",
    );
    await runtime.stop();
    runtime = undefined;
    const manifestFile = path.join(bundle, "manifest.json");
    const originalManifest = await fs.readFile(manifestFile, "utf8");
    try {
      const manifest = JSON.parse(originalManifest);
      manifest.release += "-upgrade-smoke";
      await fs.writeFile(manifestFile, JSON.stringify(manifest));
      runtime = await startRuntime(options);
      const backups = await fs.readdir(path.join(directory, "backups"));
      assert.equal(backups.length, 1);
      const backup = path.join(directory, "backups", backups[0]);
      const descriptor = JSON.parse(
        await fs.readFile(path.join(backup, "manifest.json"), "utf8"),
      );
      assert.equal(descriptor.encrypted, true);
      const dump = decryptBackup(
        await fs.readFile(path.join(backup, "database.dump.enc")),
        credentials.encryption,
      );
      assert.equal(dump.subarray(0, 5).toString(), "PGDMP");
      const restoredDatabase =
        "desktop_restore_" + crypto.randomUUID().replaceAll("-", "");
      await query(
        `CREATE DATABASE ${restoredDatabase} OWNER scarlet_migrator;`,
      );
      const restoredDump = path.join(directory, "logs/restore-check.dump");
      await fs.writeFile(restoredDump, dump, { flag: "wx", mode: 0o600 });
      try {
        await run(
          path.join(bundle, "postgres/bin/pg_restore.exe"),
          [
            "--exit-on-error",
            "--clean",
            "--if-exists",
            "-w",
            "-h",
            "127.0.0.1",
            "-p",
            String(runtime.databasePort),
            "-U",
            "scarlet_migrator",
            "-d",
            restoredDatabase,
            restoredDump,
          ],
          {
            windowsHide: true,
            env: { ...process.env, PGPASSWORD: credentials.migrator },
          },
        );
        assert.equal(
          await query(
            "SELECT value FROM desktop_restart_proof;",
            "migrator",
            restoredDatabase,
          ),
          "persisted",
        );
        assert.equal(
          await query(
            "SELECT count(*) FROM pg_tables WHERE schemaname='public';",
            "migrator",
            restoredDatabase,
          ),
          await query(
            "SELECT count(*) FROM pg_tables WHERE schemaname='public';",
          ),
        );
      } finally {
        await fs.unlink(restoredDump);
      }
      const attachment = decryptBackup(
        await fs.readFile(
          path.join(backup, "attachments/upgrade-proof.bin.enc"),
        ),
        credentials.encryption,
      );
      assert.equal(attachment.toString(), "private-attachment-proof");
      assert.equal(
        await query("SELECT value FROM desktop_restart_proof;"),
        "persisted",
      );
    } finally {
      if (runtime) {
        await runtime.stop();
        runtime = undefined;
      }
      await fs.writeFile(manifestFile, originalManifest);
    }
    console.log(
      JSON.stringify({
        nativeWindowsRuntime: true,
        directory,
        authenticated: true,
        scramRoles: true,
        persistedAfterRestart: true,
        encryptedUpgradeBackup: true,
        backupRestoredIntoIsolatedDatabase: true,
        ownedServicesStopped: true,
      }),
    );
  } finally {
    if (runtime) await runtime.stop();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
