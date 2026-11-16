const { _electron } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
process.chdir(path.resolve(__dirname, "../.."));

(async () => {
  const bundle = path.resolve(process.env.DESKTOP_TEST_RUNTIME || "");
  assert.ok(bundle.startsWith(path.resolve(".runtime") + path.sep));
  const folder = path.resolve(
    ".runtime",
    "desktop-managed-" + crypto.randomUUID(),
  );
  const directory = path.join(folder, "data");
  const profile = path.join(folder, "browser");
  const account = {
    email: "desktop-ui@example.test",
    password: crypto.randomBytes(24).toString("base64url"),
  };
  const skill = "D2 持久化技能 " + crypto.randomUUID().slice(0, 8);
  const environment = { ...process.env };
  delete environment.ELECTRON_RUN_AS_NODE;
  const launch = () =>
    _electron.launch({
      executablePath: require("electron"),
      args: [
        path.resolve("desktop"),
        "--runtime-dir=" + bundle,
        "--data-dir=" + directory,
        "--user-data-dir=" + profile,
      ],
      env: environment,
      timeout: 120000,
    });
  let application;
  const errors = [];
  async function authenticate(page) {
    page.setDefaultTimeout(30000);
    page.on("pageerror", (error) => errors.push(error.message));
    await page.waitForLoadState("networkidle");
    if (await page.locator("#login-email").isVisible()) {
      await page.getByLabel("邮箱", { exact: true }).fill(account.email);
      await page.getByLabel("密码", { exact: true }).fill(account.password);
      await page.getByRole("button", { name: "登录", exact: true }).click();
      await page.waitForURL("**/skills");
    }
  }
  try {
    application = await launch();
    const setup = await application.firstWindow();
    await setup.getByRole("heading", { name: "创建你的学习空间" }).waitFor();
    await setup.screenshot({ path: ".runtime/desktop-d2-setup.png" });
    await setup.getByLabel("邮箱", { exact: true }).fill(account.email);
    await setup.getByLabel("密码", { exact: true }).fill(account.password);
    await setup.getByLabel("确认密码", { exact: true }).fill(account.password);
    const nextWindow = application.waitForEvent("window", { timeout: 120000 });
    await setup
      .getByRole("button", { name: "创建学习空间", exact: true })
      .click();
    let page = await nextWindow;
    await page.waitForURL(/http:\/\/127\.0\.0\.1:[0-9]+\//);
    await authenticate(page);
    const origin = new URL(page.url()).origin;
    await page.goto(origin + "/skills");
    await page.getByRole("button", { name: "新建技能", exact: true }).click();
    await page.getByLabel("技能名称", { exact: true }).fill(skill);
    await page.getByLabel("分类", { exact: true }).fill("桌面验收");
    await page.getByRole("button", { name: "创建技能", exact: true }).click();
    await page.getByRole("link", { name: skill, exact: true }).waitFor();
    await assert.rejects(
      fs.access(path.join(directory, "secrets/admin.password")),
    );
    await page.screenshot({
      path: ".runtime/desktop-d2-skills.png",
      fullPage: true,
    });

    await application.close();
    application = undefined;
    await assert.rejects(fs.access(path.join(directory, "launcher.lock")));
    await assert.rejects(
      fs.access(path.join(directory, "postgres-17/postmaster.pid")),
    );
    application = await launch();
    page = await application.firstWindow();
    await page.waitForURL(/http:\/\/127\.0\.0\.1:[0-9]+\//, {
      timeout: 120000,
    });
    await authenticate(page);
    await page.goto(new URL(page.url()).origin + "/skills");
    await page.getByRole("link", { name: skill, exact: true }).waitFor();
    assert.deepEqual(errors, []);
    await application.close();
    application = undefined;
    await assert.rejects(fs.access(path.join(directory, "launcher.lock")));
    await assert.rejects(
      fs.access(path.join(directory, "postgres-17/postmaster.pid")),
    );
    console.log(
      JSON.stringify({
        managedDesktop: true,
        directory,
        firstRunSetup: true,
        realSkillCreated: true,
        retainedAfterRestart: true,
        nativePostgres: true,
        normalExitStoppedOwnedServices: true,
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
