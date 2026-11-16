// Real parent loss and owned-cluster recovery, isolated from personal data.
const assert = require("node:assert/strict");
const path = require("node:path");
const crypto = require("node:crypto");
const { fork, spawn } = require("node:child_process");
const { once } = require("node:events");
const { setTimeout: sleep } = require("node:timers/promises");
const { startRuntime, cleanEnvironment } = require("../runtime.cjs");
process.chdir(path.resolve(__dirname, "../.."));
const bundle = path.resolve(process.env.DESKTOP_TEST_RUNTIME || "");
const alive = (pid) => {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
};
async function until(check, timeout = 45000) {
  const deadline = Date.now() + timeout;
  while (!(await check())) {
    assert.ok(Date.now() < deadline, "owned process reached expected state");
    await sleep(200);
  }
}
async function main() {
  assert.ok(bundle.startsWith(path.resolve(".runtime") + path.sep));
  if (process.argv.includes("--owned-child")) {
    const runtime = await startRuntime({
      bundle,
      directory: process.env.DESKTOP_LIFECYCLE_DATA,
      admin: {
        email: "lifecycle@example.test",
        password: crypto.randomBytes(24).toString("base64url"),
      },
    });
    process.send({ backendPid: runtime.backendPid });
    setInterval(() => {}, 1000);
    return;
  }
  // EOF must stop Java even while Spring is still starting.
  const java = spawn(
    path.join(bundle, "java/bin/java.exe"),
    [
      "-Dloader.main=dev.scarletnexus.desktop.DesktopApplication",
      "-cp",
      path.join(bundle, "backend.jar"),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
    ],
    {
      windowsHide: true,
      stdio: ["pipe", "ignore", "ignore"],
      env: {
        ...cleanEnvironment(bundle),
        DESKTOP_MANAGED: "true",
        DATABASE_USERNAME: "scarlet_runtime",
        SPRING_FLYWAY_ENABLED: "false",
      },
    },
  );
  const earlyExit = once(java, "exit");
  java.stdin.end();
  await until(() => java.exitCode !== null, 10000).catch((error) => {
    java.kill();
    throw error;
  });
  assert.equal((await earlyExit)[0], 0);
  const directory = path.resolve(
    ".runtime",
    "desktop-lifecycle-" + crypto.randomUUID(),
    "data",
  );
  const child = fork(__filename, ["--owned-child"], {
    env: { ...process.env, DESKTOP_LIFECYCLE_DATA: directory },
    windowsHide: true,
    stdio: ["ignore", "ignore", "inherit", "ipc"],
  });
  let runtime;
  try {
    let result, childError;
    child.once("message", (message) => {
      result = message;
    });
    child.once("error", (error) => {
      childError = error;
    });
    await until(() => {
      if (childError) throw childError;
      assert.equal(
        child.exitCode,
        null,
        "managed parent remains alive before fault",
      );
      return !!result;
    }, 180000);
    assert.ok(alive(result.backendPid));
    child.kill();
    await until(() => child.exitCode !== null || child.signalCode !== null);
    await until(() => !alive(result.backendPid));
    runtime = await startRuntime({ bundle, directory });
    assert.equal(runtime.recoveredDatabase, true);
    const response = await fetch(runtime.origin + "/api/v1/system/status");
    assert.equal((await response.json()).database, "UP");
    await runtime.stop();
    runtime = undefined;
    console.log(
      JSON.stringify({
        earlyParentEofStopsJava: true,
        parentLossStopsOwnedJava: true,
        recoveredOwnedPostgres: true,
        recoveryStoppedServices: true,
        directory,
      }),
    );
  } finally {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    if (runtime) await runtime.stop();
  }
}
main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
