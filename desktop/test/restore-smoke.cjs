const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const { startRuntime } = require("../runtime.cjs");
const { digestFile } = require("../backup.cjs");
const execute = promisify(execFile);
process.chdir(path.resolve(__dirname, "../.."));
(async () => {
  const bundle = await fs.realpath(process.env.DESKTOP_TEST_RUNTIME || "");
  assert.ok(bundle.startsWith(path.resolve(".runtime") + path.sep));
  const folder = path.resolve(
    ".runtime",
    "restore-native-" + crypto.randomUUID(),
  );
  await fs.mkdir(folder);
  const source = path.join(folder, "source"),
    restored = path.join(folder, "restored");
  const admin = {
    email: "restore@example.test",
    password: crypto.randomBytes(24).toString("base64url"),
  };
  let runtime, cookie;
  const options = { bundle, directory: source, admin };
  async function login() {
    const response = await fetch(runtime.origin + "/api/v1/auth/csrf");
    const csrf = await response.json();
    const cookies = response.headers
      .getSetCookie()
      .map((s) => s.split(";")[0])
      .join("; ");
    const logged = await fetch(runtime.origin + "/api/v1/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Cookie: cookies,
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(admin),
    });
    assert.equal(logged.status, 200);
    cookie = logged.headers
      .getSetCookie()
      .map((s) => s.split(";")[0])
      .join("; ");
    return cookie;
  }
  async function post(route, data, multipart = false) {
    const csrf = await (
      await fetch(runtime.origin + "/api/v1/auth/csrf", {
        headers: { Cookie: cookie },
      })
    ).json();
    const response = await fetch(runtime.origin + "/api/v1" + route, {
      method: "POST",
      headers: {
        Cookie: cookie,
        [csrf.headerName]: csrf.token,
        ...(multipart ? {} : { "Content-Type": "application/json" }),
      },
      body: multipart ? data : JSON.stringify(data),
    });
    assert.ok(response.ok, route + ": " + response.status);
    return response.json();
  }
  async function content(image) {
    const response = await fetch(
      runtime.origin + "/api/v1/attachments/" + image.id + "/content",
      { headers: { Cookie: cookie } },
    );
    assert.equal(response.status, 200);
    return crypto
      .createHash("sha256")
      .update(Buffer.from(await response.arrayBuffer()))
      .digest("hex");
  }
  async function stop() {
    if (runtime) await runtime.stop();
    runtime = undefined;
  }
  try {
    runtime = await startRuntime(options);
    const oldCookie = await login();
    const skill = await post("/skills", {
      name: "Restored original skill",
      category: "",
      description: "Private recovery fixture",
      status: "ACTIVE",
    });
    const node = await post("/knowledge/nodes", {
      title: "Restored original node",
      nodeType: "PRACTICE",
      description: "",
      status: "ACTIVE",
    });
    await post(`/skills/${skill.id}/nodes`, {
      nodeId: node.id,
      displayOrder: 0,
    });
    const task = await post("/learning/tasks", {
      skillId: skill.id,
      nodeId: node.id,
      title: "Restored original task",
      plannedMinutes: 25,
    });
    const form = new FormData();
    form.append(
      "file",
      new Blob([await fs.readFile("desktop/build/icon.png")], {
        type: "image/png",
      }),
      "proof.png",
    );
    const image = await post(
      `/learning/tasks/${task.id}/attachments`,
      form,
      true,
    );
    const imageSha = await content(image);
    const stateFile = path.join(source, "state.json");
    const state = JSON.parse(await fs.readFile(stateFile, "utf8"));
    await stop();
    state.release += "-before-recovery-test";
    await fs.writeFile(stateFile, JSON.stringify(state));
    runtime = await startRuntime(options);
    await stop();
    const backups = await fs.readdir(path.join(source, "backups"));
    assert.equal(backups.length, 1);
    const backup = path.join(source, "backups", backups[0]),
      keyFile = path.join(source, "secrets/database.json");
    const sourceKeys = JSON.parse(await fs.readFile(keyFile, "utf8"));
    const sourceKeySha = await digestFile(keyFile),
      sourceDumpSha = await digestFile(path.join(backup, "database.dump.enc"));
    const wrongKey = path.join(folder, "wrong-key.json");
    await fs.writeFile(
      wrongKey,
      JSON.stringify({
        encryption: crypto.randomBytes(32).toString("base64url"),
      }),
    );
    const failed = path.join(folder, "wrong-key-target");
    await assert.rejects(
      startRuntime({
        bundle,
        directory: failed,
        restore: { backup, keyFile: wrongKey },
      }),
      /authentication failed/,
    );
    await assert.rejects(
      fs.access(path.join(failed, "postgres-17/PG_VERSION")),
    );
    await assert.rejects(
      startRuntime({ bundle, directory: failed, admin }),
      /unfinished restore/,
    );
    const tampered = path.join(folder, "tampered-backup");
    await fs.cp(backup, tampered, {
      recursive: true,
      errorOnExist: true,
      force: false,
    });
    const bytes = await fs.readFile(path.join(tampered, "database.dump.enc"));
    bytes[bytes.length - 1] ^= 1;
    await fs.writeFile(path.join(tampered, "database.dump.enc"), bytes);
    await assert.rejects(
      startRuntime({
        bundle,
        directory: path.join(folder, "tampered-target"),
        restore: { backup: tampered, keyFile },
      }),
      /checksum differs/,
    );
    runtime = await startRuntime({
      bundle,
      directory: restored,
      restore: { backup, keyFile },
    });
    assert.equal(
      (
        await fetch(runtime.origin + "/api/v1/skills", {
          headers: { Cookie: oldCookie },
        })
      ).status,
      401,
    );
    await login();
    const result = await fetch(runtime.origin + `/api/v1/skills/${skill.id}`, {
      headers: { Cookie: cookie },
    });
    assert.equal((await result.json()).name, skill.name);
    assert.equal(await content(image), imageSha);
    const freshKeys = JSON.parse(
      await fs.readFile(path.join(restored, "secrets/database.json"), "utf8"),
    );
    for (const key of [
      "control",
      "runtime",
      "migrator",
      "backup",
      "encryption",
    ])
      assert.notEqual(freshKeys[key], sourceKeys[key]);
    const psql = path.join(bundle, "postgres/bin/psql.exe");
    for (const sql of [
      "CREATE TABLE forbidden_restore_probe(id int);",
      "DELETE FROM audit_events WHERE false;",
    ]) {
      await assert.rejects(
        execute(
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
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            sql,
          ],
          {
            windowsHide: true,
            env: { ...process.env, PGPASSWORD: freshKeys.runtime },
          },
        ),
      );
    }
    await assert.rejects(
      fs.access(path.join(restored, "restore.pending.json")),
    );
    assert.equal(
      JSON.parse(
        await fs.readFile(
          path.join(restored, "restore.completed.json"),
          "utf8",
        ),
      ).status,
      "complete",
    );
    await stop();
    await assert.rejects(
      startRuntime({
        bundle,
        directory: restored,
        restore: { backup, keyFile },
      }),
      /new empty/,
    );
    runtime = await startRuntime({ bundle, directory: restored });
    await login();
    assert.equal(await content(image), imageSha);
    await stop();
    runtime = await startRuntime(options);
    await login();
    assert.equal(await content(image), imageSha);
    await stop();
    assert.equal(await digestFile(keyFile), sourceKeySha);
    assert.equal(
      await digestFile(path.join(backup, "database.dump.enc")),
      sourceDumpSha,
    );
    const fixture = path.join(source, "secrets/restore-acceptance.json");
    await fs.writeFile(
      fixture,
      JSON.stringify({ admin, backup, keyFile, skill, image, imageSha }),
      { flag: "wx", mode: 0o600 },
    );
    console.log(
      JSON.stringify({
        actualPostgresRestore: true,
        originalAccountAndImageRestored: true,
        oldSessionsInvalidated: true,
        newDatabaseAndEncryptionKeys: true,
        runtimeHistoryPermissionsPreserved: true,
        badKeyAndTamperingRejectedBeforeDatabase: true,
        occupiedTargetRejected: true,
        incompleteTargetCannotOpen: true,
        originalSourceUnchanged: true,
        restoredRestarted: true,
        folder,
        fixture,
      }),
    );
  } finally {
    await stop();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
