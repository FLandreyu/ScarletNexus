const fs = require("node:fs/promises");
const syncFs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const net = require("node:net");
const { spawn, execFile } = require("node:child_process");
const { digestFile, encryptStream, encryptCommand } = require("./backup.cjs");

const identifier = "scarlet-nexus-desktop-data/v1";
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const hash = (data) => crypto.createHash("sha256").update(data).digest("hex");
const secret = () => crypto.randomBytes(36).toString("base64url");
const exists = async (file) => {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
};
const readJson = async (file) => JSON.parse(await fs.readFile(file, "utf8"));

async function writeJson(file, data) {
  const temporary = file + "." + crypto.randomUUID() + ".tmp";
  await fs.writeFile(temporary, JSON.stringify(data, null, 2), {
    flag: "wx",
    mode: 0o600,
  });
  await fs.rename(temporary, file);
}

function cleanEnvironment(bundle) {
  const environment = {};
  for (const key of [
    "SystemRoot",
    "WINDIR",
    "COMSPEC",
    "TEMP",
    "TMP",
    "USERPROFILE",
    "LOCALAPPDATA",
    "APPDATA",
  ]) {
    if (process.env[key]) environment[key] = process.env[key];
  }
  environment.Path = [
    path.join(bundle, "java/bin"),
    path.join(bundle, "postgres/bin"),
    path.join(process.env.SystemRoot || "C:\\Windows", "System32"),
  ].join(path.delimiter);
  environment.JAVA_HOME = path.join(bundle, "java");
  environment.TZ = "UTC";
  environment.LC_ALL = "C";
  environment.PGCLIENTENCODING = "UTF8";
  return environment;
}

function command(file, args, options = {}) {
  if (options.stdio === "ignore") {
    // pg_ctl starts a background server. Do not give it pipes that the server
    // could inherit and keep open after pg_ctl itself has exited on Windows.
    return new Promise((resolve, reject) => {
      const child = spawn(file, args, {
        env: options.env,
        windowsHide: true,
        stdio: "ignore",
      });
      const timer = setTimeout(() => {
        child.kill();
        reject(new Error(path.basename(file) + " timed out"));
      }, 120000);
      child.once("error", () => {
        clearTimeout(timer);
        reject(new Error(path.basename(file) + " could not start"));
      });
      child.once("exit", (code) => {
        clearTimeout(timer);
        code === 0
          ? resolve("")
          : reject(
              new Error(path.basename(file) + " failed (code " + code + ")"),
            );
      });
    });
  }
  return new Promise((resolve, reject) => {
    const child = execFile(
      file,
      args,
      {
        windowsHide: true,
        timeout: 120000,
        maxBuffer: 8 * 1024 * 1024,
        ...options,
        encoding: "utf8",
      },
      (error, stdout) => {
        if (error)
          reject(
            new Error(
              `${path.basename(file)} failed (code ${error.code ?? "unknown"}); no credentials are included in this error`,
            ),
          );
        else resolve(stdout.trim());
      },
    );
    child.stdin?.on("error", () => {});
    child.stdin?.end(options.input || "");
  });
}

async function verifyBundle(directory) {
  const root = await fs.realpath(directory);
  const manifest = await readJson(path.join(root, "manifest.json"));
  if (
    manifest.schema !== 1 ||
    manifest.platform !== "win32-x64" ||
    manifest.postgresMajor !== "17" ||
    typeof manifest.release !== "string" ||
    !manifest.release ||
    typeof manifest.files !== "object"
  )
    throw new Error("Unsupported desktop bundle");
  for (const name of [
    "backend.jar",
    "roles.sql",
    "assets/index.html",
    "java/bin/java.exe",
    "postgres/bin/initdb.exe",
    "postgres/bin/pg_ctl.exe",
    "postgres/bin/psql.exe",
    "postgres/bin/pg_dump.exe",
  ]) {
    if (!manifest.files[name])
      throw new Error("Desktop bundle inventory is incomplete");
  }
  for (const [name, checksum] of Object.entries(manifest.files)) {
    if (
      path.isAbsolute(name) ||
      name.includes("\\") ||
      name.split("/").some((part) => !part || part === "..") ||
      !/^[a-f0-9]{64}$/.test(checksum)
    )
      throw new Error("Unsafe desktop bundle inventory");
    const file = await fs.realpath(path.join(root, name));
    if (
      !file.startsWith(root + path.sep) ||
      (await fs.lstat(path.join(root, name))).isSymbolicLink() ||
      (await digestFile(file)) !== checksum
    )
      throw new Error("Desktop runtime integrity check failed: " + name);
  }
  return { root, manifest };
}

async function reservePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function prepareData(directory, environment) {
  await fs.mkdir(directory, { recursive: true });
  if ((await fs.lstat(directory)).isSymbolicLink())
    throw new Error("Data root must not be a link");
  const root = await fs.realpath(directory);
  const marker = path.join(root, "owner.json");
  if (!(await exists(marker))) {
    if ((await fs.readdir(root)).length)
      throw new Error("Refusing to adopt a nonempty unknown data directory");
    // Protect newly owned storage before placing any credential in it.
    if (process.platform === "win32") {
      const user = await command("whoami.exe", ["/user", "/fo", "csv", "/nh"], {
        env: environment,
      });
      const sid = user.match(/S-1-[0-9-]+/)?.[0];
      if (!sid) throw new Error("Cannot establish private data owner");
      await command("icacls.exe", [root, "/grant:r", `*${sid}:(OI)(CI)F`], {
        env: environment,
      });
      await command("icacls.exe", [root, "/inheritance:r"], {
        env: environment,
      });
    }
    await fs.writeFile(
      marker,
      JSON.stringify({ format: identifier, id: crypto.randomUUID() }),
      { flag: "wx", mode: 0o600 },
    );
  }
  if ((await readJson(marker)).format !== identifier)
    throw new Error("Unknown data directory owner");
  for (const name of ["secrets", "logs", "attachments", "backups"]) {
    const folder = path.join(root, name);
    await fs.mkdir(folder, { recursive: true });
    if ((await fs.lstat(folder)).isSymbolicLink())
      throw new Error("Managed data folders must not be links");
  }
  return root;
}

async function acquireLock(root) {
  const file = path.join(root, "launcher.lock");
  if (await exists(file)) {
    const old = await readJson(file);
    if (!Number.isSafeInteger(old.pid) || old.pid < 1)
      throw new Error("Invalid launcher lock; preserve the data directory");
    let running = true;
    try {
      process.kill(old.pid, 0);
    } catch (error) {
      if (error.code === "ESRCH") running = false;
    }
    if (running) throw new Error("This data directory is already in use");
    await fs.unlink(file);
  }
  const token = crypto.randomUUID();
  await fs.writeFile(file, JSON.stringify({ pid: process.pid, token }), {
    flag: "wx",
    mode: 0o600,
  });
  return async () => {
    if ((await readJson(file)).token !== token)
      throw new Error("Launcher ownership changed");
    await fs.unlink(file);
  };
}

async function postgresAliases(bundle, root, environment) {
  const user = await command("whoami.exe", ["/user", "/fo", "csv", "/nh"], {
    env: environment,
  });
  const sid = user.match(/S-1-[0-9-]+/)?.[0];
  if (!sid) throw new Error("Cannot identify PostgreSQL path owner");
  const base = path.resolve(
    process.env.ProgramData || "C:\\ProgramData",
    "ScarletNexus",
    "paths-" + sid,
  );
  if (!/^[\x20-\x7e]+$/.test(base))
    throw new Error(
      "An ASCII ProgramData location is required by the PostgreSQL Windows distribution",
    );
  await prepareData(base, environment);
  async function alias(target) {
    const canonical = await fs.realpath(target);
    const link = path.join(
      base,
      "p-" + hash(canonical.toLowerCase()).slice(0, 24),
    );
    if (!(await exists(link))) await fs.symlink(canonical, link, "junction");
    if (
      !(await fs.lstat(link)).isSymbolicLink() ||
      (await fs.realpath(link)).toLowerCase() !== canonical.toLowerCase()
    ) {
      throw new Error("PostgreSQL execution path ownership differs");
    }
    return link;
  }
  return {
    postgres: await alias(path.join(bundle, "postgres")),
    data: await alias(root),
  };
}

function encryptBackup(data, key) {
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv(
    "aes-256-gcm",
    Buffer.from(key, "base64url"),
    nonce,
  );
  const content = Buffer.concat([cipher.update(data), cipher.final()]);
  return Buffer.concat([
    Buffer.from("SNDUENC1"),
    nonce,
    cipher.getAuthTag(),
    content,
  ]);
}

function decryptBackup(data, key) {
  if (data.length < 36 || data.subarray(0, 8).toString() !== "SNDUENC1")
    throw new Error("Invalid desktop backup envelope");
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    Buffer.from(key, "base64url"),
    data.subarray(8, 20),
  );
  decipher.setAuthTag(data.subarray(20, 36));
  return Buffer.concat([decipher.update(data.subarray(36)), decipher.final()]);
}

async function copyAttachments(source, destination, key) {
  await fs.mkdir(destination, { recursive: true });
  const checksums = {};
  async function copy(folder, relative = "") {
    for (const entry of await fs.readdir(folder, { withFileTypes: true })) {
      const name = path.join(relative, entry.name);
      if (entry.isSymbolicLink())
        throw new Error("Attachment links cannot be backed up");
      if (entry.isDirectory()) {
        await fs.mkdir(path.join(destination, name));
        await copy(path.join(folder, entry.name), name);
      } else if (entry.isFile()) {
        checksums[name.replaceAll(path.sep, "/")] = await encryptStream(
          syncFs.createReadStream(path.join(source, name)),
          path.join(destination, name + ".enc"),
          key,
        );
      } else throw new Error("Unsupported attachment entry");
    }
  }
  await copy(source);
  return checksums;
}

async function startRuntime({
  bundle,
  directory,
  admin,
  onStatus = () => {},
  onFailure = () => {},
}) {
  if (process.platform !== "win32" || process.arch !== "x64")
    throw new Error("This bundle requires Windows x64");
  const verified = await verifyBundle(bundle);
  const environment = cleanEnvironment(verified.root);
  const root = await prepareData(directory, environment);
  if (!(await exists(path.join(root, "administrator.json"))))
    validateAdmin(admin);
  const unlock = await acquireLock(root);
  let postgresRoot = path.join(verified.root, "postgres");
  const pg = (name) => path.join(postgresRoot, "bin", name + ".exe");
  const java = path.join(verified.root, "java/bin/java.exe");
  const cluster = path.join(root, "postgres-17");
  let pgData = cluster,
    dataAlias = root;
  const configuration = path.join(root, "state.json");
  const credentialFile = path.join(root, "secrets/database.json");
  let databasePort,
    ownedDatabase = false,
    backend,
    stopping = false,
    failure;
  let state, credentials;
  try {
    state = (await exists(configuration))
      ? await readJson(configuration)
      : { schema: 1, initialized: false };
    credentials = (await exists(credentialFile))
      ? await readJson(credentialFile)
      : {
          control: secret(),
          migrator: secret(),
          runtime: secret(),
          backup: secret(),
          encryption: crypto.randomBytes(32).toString("base64url"),
        };
    if (!(await exists(credentialFile))) {
      if (await exists(cluster)) {
        throw new Error(
          "Existing database credentials are missing; data was preserved",
        );
      }
      await writeJson(credentialFile, credentials);
    }
    if (
      ["control", "migrator", "runtime", "backup", "encryption"].some(
        (key) =>
          typeof credentials[key] !== "string" ||
          !/^[A-Za-z0-9_-]{40,}$/.test(credentials[key]),
      ) ||
      Buffer.from(credentials.encryption || "", "base64url").length !== 32
    ) {
      throw new Error("Invalid protected credentials");
    }
  } catch (error) {
    await unlock();
    throw error;
  }
  const dbEnvironment = (role) => ({
    ...environment,
    PGPASSWORD: credentials[role],
  });
  const sql = (database, query, role = "control") =>
    command(
      pg("psql"),
      [
        "-X",
        "-w",
        "-h",
        "127.0.0.1",
        "-p",
        String(databasePort),
        "-U",
        role === "control" ? "scarlet_control" : "scarlet_" + role,
        "-d",
        database,
        "-v",
        "ON_ERROR_STOP=1",
        "-At",
      ],
      { env: dbEnvironment(role), input: query },
    );
  async function verifyDatabaseOwner() {
    const actual = await sql("postgres", "SHOW data_directory;");
    if ((await fs.realpath(actual)).toLowerCase() !== cluster.toLowerCase())
      throw new Error(
        "Database ownership does not match the managed directory",
      );
  }
  async function stop() {
    if (stopping) return;
    stopping = true;
    try {
      if (backend && backend.exitCode === null) {
        backend.stdin.end("STOP\n");
        await new Promise((resolve) => {
          const timer = setTimeout(resolve, 45000);
          backend.once("exit", () => {
            clearTimeout(timer);
            resolve();
          });
        });
        if (backend.exitCode === null) {
          backend.kill();
          await new Promise((resolve) => backend.once("exit", resolve));
        }
      }
      if (!ownedDatabase && databasePort) {
        try {
          await verifyDatabaseOwner();
          ownedDatabase = true;
        } catch {}
      }
      if (ownedDatabase) {
        await verifyDatabaseOwner();
        await command(
          pg("pg_ctl"),
          ["stop", "-D", pgData, "-m", "fast", "-w", "-t", "30"],
          { env: environment },
        );
      }
    } finally {
      await unlock();
    }
  }
  try {
    const aliases = await postgresAliases(verified.root, root, environment);
    postgresRoot = aliases.postgres;
    dataAlias = aliases.data;
    pgData = path.join(dataAlias, "postgres-17");
    if (state.schema !== 1 || typeof state.initialized !== "boolean")
      throw new Error("Unsupported desktop state; data was preserved");
    if (!(await exists(path.join(cluster, "PG_VERSION")))) {
      if (await exists(cluster))
        throw new Error(
          "Partial database initialization detected; data was preserved",
        );
      onStatus("正在初始化专用数据库");
      const passwordFile = path.join(root, "secrets/cluster.password");
      await fs.writeFile(passwordFile, credentials.control, {
        flag: "wx",
        mode: 0o600,
      });
      try {
        await command(
          pg("initdb"),
          [
            "-D",
            pgData,
            "-U",
            "scarlet_control",
            "--auth=scram-sha-256",
            "--encoding=UTF8",
            "--locale=C",
            "--pwfile=" + path.join(dataAlias, "secrets/cluster.password"),
          ],
          { env: environment },
        );
      } finally {
        await fs.unlink(passwordFile);
      }
      await fs.appendFile(
        path.join(cluster, "postgresql.conf"),
        "\nlisten_addresses='127.0.0.1'\nunix_socket_directories=''\nlog_statement='none'\nlog_min_error_statement='panic'\n",
      );
    }
    if (
      (await fs.readFile(path.join(cluster, "PG_VERSION"), "utf8")).trim() !==
      verified.manifest.postgresMajor
    ) {
      throw new Error(
        "PostgreSQL major version differs; automatic major upgrades are forbidden",
      );
    }
    let alreadyRunning = false;
    try {
      await command(pg("pg_ctl"), ["status", "-D", pgData], {
        env: environment,
      });
      alreadyRunning = true;
    } catch {}
    if (alreadyRunning) {
      databasePort = Number(
        (await fs.readFile(path.join(cluster, "postmaster.pid"), "utf8")).split(
          /\r?\n/,
        )[3],
      );
      if (
        !Number.isInteger(databasePort) ||
        databasePort < 1 ||
        databasePort > 65535
      )
        throw new Error("Invalid managed database port");
    } else {
      databasePort = await reservePort();
      await command(
        pg("pg_ctl"),
        [
          "start",
          "-D",
          pgData,
          "-l",
          path.join(dataAlias, "logs/postgres.log"),
          "-w",
          "-t",
          "30",
          "-o",
          `-p ${databasePort} -h 127.0.0.1`,
        ],
        { env: environment, stdio: "ignore" },
      );
    }
    await verifyDatabaseOwner();
    ownedDatabase = true;
    if (!state.initialized) {
      const database = await sql(
        "postgres",
        "SELECT datname FROM pg_database WHERE datname='scarlet_nexus';",
      );
      if (!database) await sql("postgres", "CREATE DATABASE scarlet_nexus;");
      const roles = await sql(
        "postgres",
        "SELECT rolname FROM pg_roles WHERE rolname='scarlet_runtime';",
      );
      if (!roles) {
        const provisioning = await fs.readFile(
          path.join(verified.root, "roles.sql"),
          "utf8",
        );
        const passwords = ["migrator", "runtime", "backup"]
          .map(
            (role) =>
              `ALTER ROLE scarlet_${role} PASSWORD '${credentials[role]}';`,
          )
          .join("\n");
        await sql("scarlet_nexus", provisioning + "\n" + passwords);
      } else {
        const tables = await sql(
          "scarlet_nexus",
          "SELECT count(*) FROM pg_tables WHERE schemaname='public';",
        );
        if (tables !== "0")
          throw new Error(
            "Refusing to reprovision an existing application schema",
          );
        await sql(
          "scarlet_nexus",
          ["migrator", "runtime", "backup"]
            .map(
              (role) =>
                `ALTER ROLE scarlet_${role} PASSWORD '${credentials[role]}';`,
            )
            .join("\n"),
        );
        await sql("scarlet_nexus", "SELECT 1;", "runtime");
        await sql("scarlet_nexus", "SELECT 1;", "migrator");
        await sql("scarlet_nexus", "SELECT 1;", "backup");
      }
      state.initialized = true;
      await writeJson(configuration, state);
    }
    const applicationHash = verified.manifest.files["backend.jar"];
    if (
      state.applicationHash &&
      (state.applicationHash !== applicationHash ||
        state.release !== verified.manifest.release)
    ) {
      onStatus("正在创建升级前备份");
      const backup = path.join(root, "backups", crypto.randomUUID());
      await fs.mkdir(backup);
      const database = await encryptCommand(
        pg("pg_dump"),
        [
          "-h",
          "127.0.0.1",
          "-p",
          String(databasePort),
          "-U",
          "scarlet_backup",
          "-d",
          "scarlet_nexus",
          "-w",
          "-Fc",
        ],
        {
          env: dbEnvironment("backup"),
          destination: path.join(backup, "database.dump.enc"),
          key: credentials.encryption,
        },
      );
      const attachments = await copyAttachments(
        path.join(root, "attachments"),
        path.join(backup, "attachments"),
        credentials.encryption,
      );
      await writeJson(path.join(backup, "manifest.json"), {
        format: "scarlet-desktop-upgrade-backup/v1",
        fromApplicationHash: state.applicationHash,
        toApplicationHash: applicationHash,
        databasePlainSha256: database.plainSha256,
        databaseCipherSha256: database.cipherSha256,
        attachments,
        encrypted: true,
        cipher: "AES-256-GCM",
        keyStorage: "local-protected-credentials",
      });
    }
    onStatus("正在校验数据库结构");
    const jdbc = `jdbc:postgresql://127.0.0.1:${databasePort}/scarlet_nexus`;
    await command(
      java,
      [
        "-Dloader.main=dev.scarletnexus.database.DatabaseMigration",
        "-cp",
        path.join(verified.root, "backend.jar"),
        "org.springframework.boot.loader.launch.PropertiesLauncher",
      ],
      {
        env: {
          ...environment,
          DATABASE_URL: jdbc,
          MIGRATION_USERNAME: "scarlet_migrator",
          MIGRATION_PASSWORD: credentials.migrator,
        },
      },
    );
    const adminFile = path.join(root, "secrets/admin.password");
    if (!(await exists(path.join(root, "administrator.json")))) {
      validateAdmin(admin);
      await fs.writeFile(adminFile, admin.password, {
        flag: "wx",
        mode: 0o600,
      });
      await writeJson(path.join(root, "administrator.json"), {
        email: admin.email,
      });
    }
    const pendingAdmin = await exists(adminFile);
    const account = await readJson(path.join(root, "administrator.json"));
    const backendPort = await reservePort();
    const log = syncFs.openSync(path.join(root, "logs/backend.log"), "a");
    onStatus("正在启动学习服务");
    backend = spawn(
      java,
      [
        "-Dloader.main=dev.scarletnexus.desktop.DesktopApplication",
        "-cp",
        path.join(verified.root, "backend.jar"),
        "org.springframework.boot.loader.launch.PropertiesLauncher",
      ],
      {
        cwd: root,
        windowsHide: true,
        stdio: ["pipe", log, log],
        env: {
          ...environment,
          DESKTOP_MANAGED: "true",
          DATABASE_URL: jdbc,
          DATABASE_USERNAME: "scarlet_runtime",
          DATABASE_PASSWORD: credentials.runtime,
          SPRING_FLYWAY_ENABLED: "false",
          SERVER_ADDRESS: "127.0.0.1",
          SERVER_PORT: String(backendPort),
          API_DOCS_ENABLED: "false",
          ADMIN_EMAIL: pendingAdmin ? account.email : "",
          ADMIN_PASSWORD_FILE: pendingAdmin ? adminFile : "",
          APP_ATTACHMENTS_DIRECTORY: path.join(root, "attachments"),
          LOGGING_FILE_NAME: path.join(root, "logs/application.log"),
          LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE: "10MB",
          LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY: "3",
        },
      },
    );
    syncFs.closeSync(log);
    backend.stdin.on("error", () => {});
    backend.once("error", () => {
      failure = new Error("Desktop Java process could not start");
    });
    backend.once("exit", () => {
      if (!stopping) {
        failure = new Error("Desktop learning service stopped unexpectedly");
        onFailure(failure);
      }
    });
    const origin = `http://127.0.0.1:${backendPort}`;
    let ready = false;
    for (let attempt = 0; attempt < 120; attempt++) {
      if (failure || backend.exitCode !== null)
        throw failure || new Error("Desktop service startup failed");
      try {
        const response = await fetch(origin + "/api/v1/system/status", {
          signal: AbortSignal.timeout(1000),
        });
        const status = await response.json();
        if (
          response.ok &&
          status.service === "scarlet-nexus" &&
          status.database === "UP" &&
          status.schemaVersion === verified.manifest.schemaVersion
        ) {
          const readiness = await fetch(origin + "/actuator/health/readiness", {
            signal: AbortSignal.timeout(1000),
          });
          if (!readiness.ok || (await readiness.json()).status !== "UP") {
            await sleep(100);
            continue;
          }
          const ports = await command("netstat.exe", ["-ano", "-p", "tcp"], {
            env: environment,
          });
          ready = ports.split(/\r?\n/).some((line) => {
            const fields = line.trim().split(/\s+/);
            return (
              fields[1] === `127.0.0.1:${backendPort}` &&
              fields.at(-1) === String(backend.pid)
            );
          });
          if (ready) break;
        }
      } catch {}
      await sleep(500);
    }
    if (!ready)
      throw new Error("Owned desktop learning service did not become ready");
    if (pendingAdmin) await fs.unlink(adminFile);
    state = { ...state, applicationHash, release: verified.manifest.release };
    await writeJson(configuration, state);
    return {
      origin,
      assets: path.join(verified.root, "assets"),
      directory: root,
      databasePort,
      recoveredDatabase: alreadyRunning,
      stop,
      backendPid: backend.pid,
      manifest: verified.manifest,
    };
  } catch (error) {
    await stop();
    throw error;
  }
}

function validateAdmin(admin) {
  if (
    !admin ||
    typeof admin.email !== "string" ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(admin.email) ||
    admin.email.length > 254 ||
    /[\x00-\x1f\x7f]/.test(admin.email) ||
    typeof admin.password !== "string" ||
    admin.password.length < 12 ||
    Buffer.byteLength(admin.password, "utf8") > 72 ||
    admin.password !== admin.password.trim() ||
    /[\x00-\x1f\x7f]/.test(admin.password)
  ) {
    throw new Error(
      "Provide a valid email and a single-line password of 12 characters to 72 UTF-8 bytes",
    );
  }
}

module.exports = {
  verifyBundle,
  startRuntime,
  prepareData,
  acquireLock,
  validateAdmin,
  cleanEnvironment,
  encryptBackup,
  decryptBackup,
};
