const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const { startRuntime, verifyBundle, decryptBackup } = require("../runtime.cjs");
const { digestFile } = require("../backup.cjs");
const execute = promisify(execFile);
process.chdir(path.resolve(__dirname, "../.."));
(async () => {
  const bundle = await fs.realpath(process.env.DESKTOP_TEST_RUNTIME || "");
  assert.ok(bundle.startsWith(path.resolve(".runtime") + path.sep));
  const verified = await verifyBundle(bundle);
  const folder = path.resolve(
    ".runtime",
    "upgrade-failure-" + crypto.randomUUID(),
  );
  const candidate = path.join(folder, "candidate"),
    directory = path.join(folder, "data");
  await fs.mkdir(candidate, { recursive: true });
  // Hard-link immutable runtime assets; only the private candidate JAR differs.
  for (const name of Object.keys(verified.manifest.files).filter(
    (name) => name !== "backend.jar",
  )) {
    const target = path.join(candidate, name);
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.link(path.join(bundle, name), target);
  }
  await execute(
    "python",
    [
      "-c",
      "import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2],'x') as target:\n for item in source.infolist(): target.writestr(item,source.read(item.filename))\n target.writestr('BOOT-INF/classes/db/migration/V13__desktop_failure_probe.sql',\"CREATE TABLE desktop_failure_probe(id integer); DO $$ BEGIN RAISE EXCEPTION 'desktop_v13_expected_failure'; END $$;\")",
      path.join(bundle, "backend.jar"),
      path.join(candidate, "backend.jar"),
    ],
    { windowsHide: true },
  );
  const manifest = {
    ...verified.manifest,
    files: {
      ...verified.manifest.files,
      "backend.jar": await digestFile(path.join(candidate, "backend.jar")),
    },
    schemaVersion: "13",
    release: "test-only-failing-v13",
  };
  await fs.writeFile(
    path.join(candidate, "manifest.json"),
    JSON.stringify(manifest),
  );
  const admin = {
    email: "upgrade-failure@example.test",
    password: crypto.randomBytes(24).toString("base64url"),
  };
  let runtime;
  let credentials;
  const options = { bundle, directory, admin };
  async function query(sql) {
    return (
      await execute(
        path.join(bundle, "postgres/bin/psql.exe"),
        [
          "-X",
          "-w",
          "-h",
          "127.0.0.1",
          "-p",
          String(runtime.databasePort),
          "-U",
          "scarlet_control",
          "-d",
          "scarlet_nexus",
          "-Atc",
          sql,
        ],
        {
          windowsHide: true,
          env: { ...process.env, PGPASSWORD: credentials.control },
        },
      )
    ).stdout.trim();
  }
  async function authenticated() {
    const csrfResponse = await fetch(runtime.origin + "/api/v1/auth/csrf");
    const csrf = await csrfResponse.json();
    const response = await fetch(runtime.origin + "/api/v1/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Cookie: csrfResponse.headers
          .getSetCookie()
          .map((s) => s.split(";")[0])
          .join("; "),
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(admin),
    });
    assert.equal(response.status, 200);
    return response.headers
      .getSetCookie()
      .map((s) => s.split(";")[0])
      .join("; ");
  }
  async function stopped() {
    if (runtime) await runtime.stop();
    runtime = undefined;
    await assert.rejects(fs.access(path.join(directory, "launcher.lock")));
    await assert.rejects(
      fs.access(path.join(directory, "postgres-17/postmaster.pid")),
    );
  }
  try {
    runtime = await startRuntime(options);
    credentials = JSON.parse(
      await fs.readFile(path.join(directory, "secrets/database.json"), "utf8"),
    );
    const cookie = await authenticated();
    const csrfResponse = await fetch(runtime.origin + "/api/v1/auth/csrf", {
      headers: { Cookie: cookie },
    });
    const csrf = await csrfResponse.json();
    const response = await fetch(runtime.origin + "/api/v1/skills", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Cookie: cookie,
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify({
        name: "Upgrade failure preservation",
        category: "",
        description: "Isolated acceptance",
        status: "ACTIVE",
      }),
    });
    assert.equal(response.status, 201);
    const skill = await response.json();
    const attachment = path.join(directory, "attachments/preservation.bin");
    await fs.writeFile(attachment, "unchanged-private-attachment", {
      flag: "wx",
    });
    await query("REVOKE SELECT ON public.skills FROM scarlet_backup;");
    const previous = await fs.readFile(
      path.join(directory, "state.json"),
      "utf8",
    );
    await stopped();
    await assert.rejects(
      startRuntime({ ...options, bundle: candidate }),
      /backup failed; migration was blocked/,
    );
    assert.equal(
      await fs.readFile(path.join(directory, "state.json"), "utf8"),
      previous,
    );
    let backups = await fs.readdir(path.join(directory, "backups"));
    assert.equal(backups.length, 1);
    await assert.rejects(
      fs.access(path.join(directory, "backups", backups[0], "manifest.json")),
    );
    assert.ok(
      !(await fs.readdir(path.join(directory, "backups", backups[0]))).some(
        (name) => name.includes("pending.dump"),
      ),
    );
    runtime = await startRuntime(options);
    assert.equal(
      await query(`SELECT name FROM skills WHERE id='${skill.id}';`),
      "Upgrade failure preservation",
    );
    await query("GRANT SELECT ON public.skills TO scarlet_backup;");
    await stopped();
    await assert.rejects(
      startRuntime({ ...options, bundle: candidate }),
      /java.exe failed/,
    );
    assert.equal(
      await fs.readFile(path.join(directory, "state.json"), "utf8"),
      previous,
    );
    assert.ok(
      (
        await fs.readFile(path.join(directory, "logs/postgres.log"), "utf8")
      ).includes("desktop_v13_expected_failure"),
    );
    backups = await fs.readdir(path.join(directory, "backups"));
    const completed = [];
    for (const item of backups) {
      const root = path.join(directory, "backups", item);
      try {
        completed.push({
          root,
          metadata: JSON.parse(
            await fs.readFile(path.join(root, "manifest.json"), "utf8"),
          ),
        });
      } catch (error) {
        if (error.code !== "ENOENT") throw error;
      }
    }
    assert.equal(completed.length, 1);
    const beforeMigration = decryptBackup(
      await fs.readFile(path.join(completed[0].root, "database.dump.enc")),
      credentials.encryption,
    );
    assert.equal(beforeMigration.subarray(0, 5).toString(), "PGDMP");
    runtime = await startRuntime(options);
    await authenticated();
    assert.equal(
      await query(
        "SELECT max(version::int) FROM flyway_schema_history WHERE success;",
      ),
      "12",
    );
    assert.equal(
      await query("SELECT to_regclass('public.desktop_failure_probe');"),
      "",
    );
    assert.equal(
      await query(`SELECT name FROM skills WHERE id='${skill.id}';`),
      "Upgrade failure preservation",
    );
    assert.equal(
      await fs.readFile(attachment, "utf8"),
      "unchanged-private-attachment",
    );
    await stopped();
    console.log(
      JSON.stringify({
        backupFailureBlocksMigration: true,
        failedV13ActuallyExecuted: true,
        transactionRolledBack: true,
        completedBackupPrecedesMigration: true,
        oldV12ReopenedWithOriginalAccountAndData: true,
        noPlainDatabaseDump: true,
        ownedServicesStopped: true,
        folder,
      }),
    );
  } finally {
    if (runtime) await runtime.stop();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
