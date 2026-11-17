const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { build, Platform, Arch } = require("electron-builder");
const { verifyBundle } = require("./runtime.cjs");
const configuration = require("./builder.config.cjs");

(async () => {
  if (process.platform !== "win32" || process.arch !== "x64")
    throw new Error("Windows x64 build host required");
  try {
    await fs.access(
      path.join(__dirname, "node_modules/electron/dist/electron.exe"),
    );
  } catch {
    throw new Error(
      "Install the pinned Electron binary first: node node_modules/electron/install.js",
    );
  }
  const workspace = path.resolve(__dirname, "..");
  const boundary = path.join(workspace, ".runtime") + path.sep;
  const source = await fs.realpath(process.env.DESKTOP_RUNTIME_DIR || "");
  if (!source.startsWith(boundary))
    throw new Error("Choose a verified workspace .runtime bundle");
  const verified = await verifyBundle(source);
  const output = path.resolve(
    process.env.DESKTOP_DIST_DIR ||
      path.join(boundary, "desktop-d3-dist-" + crypto.randomUUID()),
  );
  if (!output.startsWith(boundary))
    throw new Error("Build output must be inside workspace .runtime");
  await fs.mkdir(output); // Refuse existing output; never delete a previous build.
  const payload = path.join(output, "payload");
  await fs.mkdir(payload);
  // Copy only verified files, so extra files in a staging directory cannot ship.
  for (const name of Object.keys(verified.manifest.files)) {
    const destination = path.join(payload, name);
    await fs.mkdir(path.dirname(destination), { recursive: true });
    await fs.copyFile(path.join(source, name), destination);
  }
  const manifest = {
    ...verified.manifest,
    release: require("./package.json").version,
  };
  await fs.writeFile(
    path.join(payload, "manifest.json"),
    JSON.stringify(manifest, null, 2),
  );
  await verifyBundle(payload);
  const artifacts = await build({
    projectDir: __dirname,
    targets: Platform.WINDOWS.createTarget("nsis", Arch.x64),
    config: configuration({ runtime: payload, output }),
    publish: "never",
  });
  const checksums = [];
  for (const file of artifacts.filter((file) => file.endsWith(".exe"))) {
    checksums.push(
      crypto
        .createHash("sha256")
        .update(await fs.readFile(file))
        .digest("hex") +
        "  " +
        path.basename(file),
    );
  }
  if (!checksums.length) throw new Error("No Windows installer was produced");
  await fs.writeFile(
    path.join(output, "SHA256SUMS.txt"),
    checksums.join("\n") + "\n",
  );
  console.log(JSON.stringify({ output, artifacts, checksums, signed: false }));
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
