// Execute the installed, packaged application with an isolated Electron profile.
const { _electron } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const execute = promisify(execFile);
process.chdir(path.resolve(__dirname, "../.."));

(async () => {
  const executable = await fs.realpath(process.env.DESKTOP_TEST_EXE || "");
  const profile = path.resolve(process.env.DESKTOP_TEST_PROFILE || "");
  const boundary = path.resolve(".runtime") + path.sep;
  assert.ok(executable.startsWith(boundary) && profile.startsWith(boundary));
  const data = path.join(profile, "learning");
  const credentialFile = path.join(data, "secrets/installer-acceptance.json");
  const initial = process.env.DESKTOP_TEST_PHASE === "initial";
  const account = initial
    ? {
        email: "installer@example.test",
        password: crypto.randomBytes(24).toString("base64url"),
        skill: "D3 安装保留 " + crypto.randomUUID().slice(0, 8),
      }
    : JSON.parse(await fs.readFile(credentialFile, "utf8"));
  const environment = { ...process.env };
  delete environment.ELECTRON_RUN_AS_NODE;
  // Global tools cannot be found on PATH; child runtime has its own explicit paths.
  environment.Path = path.join(process.env.SystemRoot, "System32");
  delete environment.PATH;
  environment.JAVA_HOME = "C:\\scarlet-no-global-java";
  let application;
  const errors = [];
  try {
    application = await _electron.launch({
      executablePath: executable,
      args: ["--user-data-dir=" + profile],
      env: environment,
      timeout: 120000,
    });
    assert.equal(await application.evaluate(({ app }) => app.isPackaged), true);
    assert.equal(
      path.resolve(
        await application.evaluate(({ app }) => app.getPath("userData")),
      ),
      profile,
    );
    let page = await application.firstWindow({ timeout: 120000 });
    if (initial) {
      await page.getByRole("heading", { name: "创建你的学习空间" }).waitFor();
      await page.getByLabel("邮箱", { exact: true }).fill(account.email);
      await page.getByLabel("密码", { exact: true }).fill(account.password);
      await page.getByLabel("确认密码", { exact: true }).fill(account.password);
      const ready = application.waitForEvent("window", { timeout: 120000 });
      await page
        .getByRole("button", { name: "创建学习空间", exact: true })
        .click();
      page = await ready;
    }
    page.setDefaultTimeout(45000);
    page.on("pageerror", (error) => errors.push(error.message));
    await page.waitForURL(/http:\/\/127\.0\.0\.1:[0-9]+\//);
    await page.waitForLoadState("networkidle");
    if (await page.locator("#login-email").isVisible()) {
      await page.getByLabel("邮箱", { exact: true }).fill(account.email);
      await page.getByLabel("密码", { exact: true }).fill(account.password);
      await page.getByRole("button", { name: "登录", exact: true }).click();
      await page.waitForURL("**/skills");
    }
    await page.goto(new URL(page.url()).origin + "/skills");
    if (initial) {
      await page.getByRole("button", { name: "新建技能", exact: true }).click();
      await page.getByLabel("技能名称", { exact: true }).fill(account.skill);
      await page.getByLabel("分类", { exact: true }).fill("安装验收");
      await page.getByRole("button", { name: "创建技能", exact: true }).click();
      await fs.writeFile(credentialFile, JSON.stringify(account), {
        flag: "wx",
        mode: 0o600,
      });
    }
    await page
      .getByRole("link", { name: account.skill, exact: true })
      .waitFor();
    if (initial || process.env.DESKTOP_TEST_PHASE === "overwrite") {
      const installer = await fs.realpath(process.env.DESKTOP_TEST_INSTALLER);
      assert.ok(installer.startsWith(boundary));
      const options = { windowsHide: true, timeout: 30000 };
      await assert.rejects(
        execute(installer, ["/S", "/D=" + path.dirname(executable)], options),
        (error) => error.code === 2,
      );
      await assert.rejects(
        execute(
          path.join(path.dirname(executable), "Uninstall Scarlet Nexus.exe"),
          ["/S", "_?=" + path.dirname(executable)],
          options,
        ),
        (error) => error.code === 2,
      );
      assert.equal(page.isClosed(), false);
      await page.reload();
      await page
        .getByRole("link", { name: account.skill, exact: true })
        .waitFor();
    }
    if (initial || process.env.DESKTOP_TEST_PHASE === "overwrite")
      console.log("running_install_and_uninstall_refused=true");
    await page.screenshot({
      path: path.join(
        profile,
        "installed-" + process.env.DESKTOP_TEST_PHASE + ".png",
      ),
      fullPage: true,
    });
    await application.close();
    application = undefined;
    await assert.rejects(fs.access(path.join(data, "launcher.lock")));
    await assert.rejects(
      fs.access(path.join(data, "postgres-17/postmaster.pid")),
    );
    assert.deepEqual(errors, []);
    console.log(
      JSON.stringify({
        packaged: true,
        phase: process.env.DESKTOP_TEST_PHASE,
        isolatedProfile: true,
        actualSkill: true,
        globalToolsRemovedFromPath: true,
        ownedServicesStopped: true,
        pageErrors: errors,
      }),
    );
  } finally {
    if (application) await application.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
