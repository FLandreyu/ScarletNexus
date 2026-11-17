// Reuse the repository's six canonical manual E2E bodies in a real Electron
// window. The adapter registers assertions; it does not replace any HTTP/API.
const { _electron } = require("playwright");
const { expect } = require("../../frontend/node_modules/@playwright/test");
const ts = require("../../frontend/node_modules/typescript");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const vm = require("node:vm");
const assert = require("node:assert/strict");
process.chdir(path.resolve(__dirname, "../.."));
(async () => {
  const executable = await fs.realpath(process.env.DESKTOP_TEST_EXE || "");
  assert.ok(executable.startsWith(path.resolve(".runtime") + path.sep));
  const folder = path.resolve(
    ".runtime",
    "desktop-business-" + crypto.randomUUID(),
  );
  const profile = path.join(folder, "profile");
  const account = {
    email: "desktop-business@example.test",
    password: crypto.randomBytes(24).toString("base64url"),
  };
  const environment = {
    ...process.env,
    Path: path.join(process.env.SystemRoot, "System32"),
    JAVA_HOME: "C:\\scarlet-no-global-java",
  };
  delete environment.PATH;
  delete environment.ELECTRON_RUN_AS_NODE;
  let application;
  const results = [];
  try {
    await fs.mkdir(folder, { recursive: true });
    application = await _electron.launch({
      acceptDownloads: true,
      artifactsDir: path.join(folder, "artifacts"),
      executablePath: executable,
      args: ["--user-data-dir=" + profile],
      env: environment,
      timeout: 120000,
    });
    assert.equal(await application.evaluate(({ app }) => app.isPackaged), true);
    const setup = await application.firstWindow({ timeout: 120000 });
    await setup.getByLabel("邮箱", { exact: true }).fill(account.email);
    await setup.getByLabel("密码", { exact: true }).fill(account.password);
    await setup.getByLabel("确认密码", { exact: true }).fill(account.password);
    const ready = application.waitForEvent("window", { timeout: 120000 });
    await setup
      .getByRole("button", { name: "创建学习空间", exact: true })
      .click();
    const page = await ready;
    await page.waitForURL(/http:\/\/127\.0\.0\.1:[0-9]+\//);
    const origin = new URL(page.url()).origin;
    const context = application.context();
    const goto = page.goto.bind(page),
      get = page.request.get.bind(page.request);
    page.goto = (url, options) => goto(new URL(url, origin).href, options);
    page.request.get = (url, options) =>
      get(new URL(url, origin).href, options);
    process.env.E2E_EMAIL = account.email;
    process.env.E2E_PASSWORD = account.password;
    await fs.writeFile(
      path.join(profile, "learning/secrets/business-acceptance.json"),
      JSON.stringify(account),
      { flag: "wx", mode: 0o600 },
    );
    for (const spec of [
      "system",
      "manual-learning",
      "study",
      "practice",
      "planning",
      "analytics-data",
    ]) {
      const registered = [];
      let timeout;
      const register = (name, callback) => registered.push({ name, callback });
      register.setTimeout = (milliseconds) => timeout?.reset(milliseconds);
      const source = path.resolve("frontend/tests/e2e", spec + ".spec.ts");
      const sourceText = await fs.readFile(source, "utf8");
      const code = ts.transpileModule(sourceText, {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2022,
        },
      }).outputText;
      const exports = {};
      vm.runInNewContext(
        code,
        {
          exports,
          module: { exports },
          require: (name) =>
            name === "@playwright/test"
              ? { test: register, expect }
              : require(name),
          process,
          Buffer,
          console,
          setTimeout,
          clearTimeout,
        },
        { filename: source },
      );
      assert.equal(
        registered.length,
        1,
        "Every canonical spec must register exactly one journey",
      );
      await context.clearCookies();
      await page.evaluate(() => {
        localStorage.clear();
        sessionStorage.clear();
      });
      const output = path.join(folder, spec);
      await fs.mkdir(output, { recursive: true });
      const info = {
        project: { name: "packaged-electron" },
        outputPath: (...parts) => path.join(output, ...parts),
      };
      let timer;
      try {
        await new Promise((resolve, reject) => {
          timeout = {
            reset(milliseconds) {
              clearTimeout(timer);
              timer = setTimeout(
                () => reject(new Error(spec + " timed out")),
                milliseconds,
              );
            },
          };
          timeout.reset(120000);
          Promise.resolve(
            registered[0].callback(
              { page, context, request: context.request },
              info,
            ),
          ).then(resolve, reject);
        });
        results.push({
          spec,
          sourceSha256: crypto
            .createHash("sha256")
            .update(sourceText)
            .digest("hex"),
          passed: true,
        });
        console.log("packaged_journey_passed=" + spec);
      } catch (error) {
        await page
          .screenshot({
            path: path.join(output, "failure.png"),
            fullPage: true,
          })
          .catch(() => {});
        throw error;
      } finally {
        clearTimeout(timer);
        timeout = undefined;
      }
    }
    await application.close();
    application = undefined;
    await assert.rejects(
      fs.access(path.join(profile, "learning/postgres-17/postmaster.pid")),
    );
    await assert.rejects(
      fs.access(path.join(profile, "learning/launcher.lock")),
    );
    await fs.writeFile(
      path.join(folder, "results.json"),
      JSON.stringify(
        { packagedElectron: true, results, ownedServicesStopped: true },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify({
        packagedElectron: true,
        canonicalManualJourneys: results.length,
        ownedServicesStopped: true,
        folder,
      }),
    );
  } finally {
    if (application) await application.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
