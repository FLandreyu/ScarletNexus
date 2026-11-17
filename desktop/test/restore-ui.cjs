const { _electron } = require("playwright");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
process.chdir(path.resolve(__dirname, "../.."));
(async () => {
  const executable = await fs.realpath(process.env.DESKTOP_TEST_EXE || "");
  const fixturePath = await fs.realpath(
    process.env.DESKTOP_RESTORE_FIXTURE || "",
  );
  const boundary = path.resolve(".runtime") + path.sep;
  assert.ok(
    executable.startsWith(boundary) && fixturePath.startsWith(boundary),
  );
  const fixture = JSON.parse(await fs.readFile(fixturePath, "utf8"));
  const profile = path.resolve(
    ".runtime",
    "restore-ui-" + crypto.randomUUID(),
    "恢复配置",
  );
  const environment = {
    ...process.env,
    Path: path.join(process.env.SystemRoot, "System32"),
    JAVA_HOME: "C:\\scarlet-no-global-java",
  };
  delete environment.PATH;
  delete environment.ELECTRON_RUN_AS_NODE;
  let application;
  const errors = [];
  async function launch(restore) {
    return _electron.launch({
      executablePath: executable,
      env: environment,
      timeout: 120000,
      args: [
        "--user-data-dir=" + profile,
        ...(restore
          ? [
              "--restore-backup=" + fixture.backup,
              "--restore-key-file=" + fixture.keyFile,
            ]
          : []),
      ],
    });
  }
  async function login(page) {
    page.setDefaultTimeout(45000);
    page.on("pageerror", (error) => errors.push(error.message));
    await page.waitForURL(/http:\/\/127\.0\.0\.1:[0-9]+\//);
    await page.waitForLoadState("networkidle");
    if (await page.locator("#login-email").isVisible()) {
      await page.getByLabel("邮箱", { exact: true }).fill(fixture.admin.email);
      await page
        .getByLabel("密码", { exact: true })
        .fill(fixture.admin.password);
      await page.getByRole("button", { name: "登录", exact: true }).click();
      await page.waitForURL("**/skills");
    }
    await page.goto(new URL(page.url()).origin + "/skills");
    await page
      .getByRole("link", { name: fixture.skill.name, exact: true })
      .waitFor();
    const image = await page.evaluate(async (id) => {
      const response = await fetch("/api/v1/attachments/" + id + "/content");
      return {
        status: response.status,
        bytes: Array.from(new Uint8Array(await response.arrayBuffer())),
      };
    }, fixture.image.id);
    assert.equal(image.status, 200);
    assert.equal(
      crypto
        .createHash("sha256")
        .update(Buffer.from(image.bytes))
        .digest("hex"),
      fixture.imageSha,
    );
  }
  try {
    application = await launch(true);
    assert.equal(await application.evaluate(({ app }) => app.isPackaged), true);
    const progress = await application.firstWindow({ timeout: 120000 });
    await progress.getByRole("heading", { name: "恢复你的学习空间" }).waitFor();
    assert.equal(
      await progress.evaluate(() => typeof window.require),
      "undefined",
    );
    const preferences = await application.evaluate(({ BrowserWindow }) => {
      const window = BrowserWindow.getAllWindows().find((window) =>
        window.webContents.getURL().endsWith("progress.html"),
      );
      const options = window.webContents.getLastWebPreferences();
      return {
        sandbox: options.sandbox,
        contextIsolation: options.contextIsolation,
        nodeIntegration: options.nodeIntegration,
      };
    });
    assert.deepEqual(preferences, {
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
    });
    await progress.screenshot({
      path: ".runtime/desktop-d3-restore-progress.png",
    });
    const page = await application.waitForEvent("window", { timeout: 120000 });
    await login(page);
    await page.screenshot({
      path: ".runtime/desktop-d3-restored-library.png",
      fullPage: true,
    });
    await application.close();
    application = undefined;
    const data = path.join(profile, "learning");
    await assert.rejects(fs.access(path.join(data, "restore.pending.json")));
    await assert.rejects(
      fs.access(path.join(data, "postgres-17/postmaster.pid")),
    );
    application = await launch(false);
    await login(await application.firstWindow({ timeout: 120000 }));
    await application.close();
    application = undefined;
    await assert.rejects(fs.access(path.join(data, "launcher.lock")));
    await assert.rejects(
      fs.access(path.join(data, "postgres-17/postmaster.pid")),
    );
    assert.deepEqual(errors, []);
    console.log(
      JSON.stringify({
        packagedRestoreEntry: true,
        sandboxedProgress: true,
        originalAccountAndSkillAndImage: true,
        reopenedNormally: true,
        ownedServicesStopped: true,
        pageErrors: errors,
        profile,
      }),
    );
  } finally {
    if (application) await application.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
