# Scarlet Nexus Desktop

D1 安全窗口与 D2 内置运行时已实现。D3 已新增离线预览安装包和快捷方式，干净 Windows 等剩余验收尚未完成；此目录目前是开发与验收入口，尚未发布 GitHub 发行版。阶段边界见 [DESKTOP_PLAN](../docs/DESKTOP_PLAN.md)，实证见 [D2 验收](../docs/DESKTOP_D2_ACCEPTANCE.md)。

## 构建和本地启动

开发环境需要 Node 24、PowerShell、已构建的真实 backend/target/scarlet-nexus-0.1.0-SNAPSHOT.jar 与 frontend/dist。后端按根 README 使用 Java 25 构建；桌面运行时不会调用系统 Java、PostgreSQL 或 Docker。

从项目根目录执行：

```powershell
npm ci --prefix desktop
npm test --prefix desktop
npm run check --prefix desktop
# 指定一个尚不存在的 .runtime 子目录；重复构建请换新目录。
powershell -NoProfile -File desktop/assemble-runtime.ps1 -OutputRoot .runtime/desktop-bundle-local
npm start --prefix desktop -- --runtime-dir="$PWD/.runtime/desktop-bundle-local"
```

装配脚本按 runtime.lock.json 下载并核对官方 Temurin 25.0.5+8 / EDB PostgreSQL 17.12 Windows x64 原始归档 SHA-256，保留运行时许可和文档，生成每文件完整性清单。清单用于装配完整性检查，本身没有发行签名。构建首次需要网络；已装配运行时启动无需下载。打包后只读取 resources/runtime，拒绝开发目录和外部后端参数。

首次窗口要求填写邮箱与 12 字符至 72 UTF-8 字节的密码，没有默认账号。默认持久数据在 %APPDATA%/ScarletNexus/learning；开发时可用 --data-dir 指定独立测试目录。已有账号不会重置。数据库仅监听随机 127.0.0.1 端口，使用随机 SCRAM 密码，控制、迁移、业务和只读备份分离；Java 只获得业务身份。AI 默认关闭，启动子进程不会继承 AI Key 或 Java/Node 注入选项。

为绕过 EDB initdb 的 Windows 非 ASCII 路径限制，程序在 %ProgramData%/ScarletNexus/paths-当前用户SID 下建立受当前用户 ACL 保护的固定 junction；目标始终核验为真实安装和数据目录。没有安装系统服务。D3 必须在普通用户、中文用户名和干净 Windows 中验证此策略，并保证卸载不跟随 junction 删除数据。

关闭窗口会先关闭本地网关，再通过私有 stdin 通道关闭 Java，最后核验 data_directory 后停止本次拥有的 PostgreSQL。父进程通道断开会关闭 Java；残留的私有 PG 在下次启动核验后接回。不会删除 postmaster.pid、重置旧账号或停止其他数据库进程。并发启动锁拒绝共享同一数据目录。

## 升级保护

应用 JAR 哈希或发行标识变化时，迁移之前使用只读备份身份导出数据库，并以 AES-256-GCM 加密数据库与附件；完整清单最后写入，任何失败都会阻止迁移。该内部格式是 scarlet-desktop-upgrade-backup/v1，与 Web SNBACK 备份不同。密钥在用户 ACL 保护的 secrets/database.json，恢复需要保留密钥；当前没有恢复 UI、异地备份或自动回滚。禁止自动跨 PostgreSQL 主版本升级。

从 d3.3 开始，pg_dump 的 stdout 直接进入 AES-256-GCM 管道，附件和文件摘要也逐块处理，不再创建 database.pending.dump。每个加密文件成功后写认证标签并刷盘，导出进程必须退出0，最后才写完整清单；失败目录可能留加密片段，没有完成清单就不属于完整备份。512MiB 文件的加密/认证解密与真实 PG 恢复已验证，更多容量、真实断电/磁盘不足和用户恢复流程仍需后续验收。不要手工覆盖或删除真实用户数据目录。

## 可重复验证

所有桌面测试创建 workspace/.runtime 内的新目录，使用随机测试密码，不接触默认个人目录。先装配测试运行时，再依次执行，避免共享清单的升级模拟并发运行：

```powershell
$env:DESKTOP_TEST_RUNTIME="$PWD/.runtime/desktop-bundle-local"
npm run test:runtime --prefix desktop
npm run test:lifecycle --prefix desktop
npm run test:managed --prefix desktop
```

runtime 验证真实原生 PG/Flyway/认证、SCRAM、角色拒绝越权、重启持久化、加密备份及隔离数据库实际恢复；lifecycle 验证启动期间 stdin EOF、父进程故障后的 Java 退出和专属 PG 恢复；managed 使用真实 Electron 窗口完成首次设置、登录、中文技能写入、正常退出与重新打开。截图在 .runtime；测试目录保留供检查。CI 当前运行 Windows Node 单测与语法检查，本地原生验收不等于远端 CI 或干净系统安装验收。

D1 可选开发模式仍支持 --dev-backend=http://127.0.0.1:8089，仅连接显式隔离服务；关闭桌面不会停止此外部服务。其旧窗口冒烟入口为 test:smoke，环境参数见 test/smoke.cjs。启动器移除 ELECTRON_RUN_AS_NODE，防止 VS Code 环境把 Electron 当作 Node 启动。

npm audit 仅检查 npm 依赖；Electron/Chromium、Java、PG 二进制及安装制品的完整供应链扫描、签名与 Release 留到 D4。

## D3 离线安装包（预览）

当前版本 0.1.0-d3.3，使用固定 electron-builder 26.18.0 构建 Windows x64 NSIS 完整安装包。构建工具中的 minimatch 7–10 系列有 GHSA-g3hv-75wg-m249 放大消耗风险；用范围覆盖精确固定 11.0.9，保留其他不受该条目影响的旧 API 分支，完整 npm 锁文件随源码保存。初次增量安装仍保留无效旧嵌套版本，重新生成锁并 npm ci 后才确认审计归零；不使用 audit fix --force。

```powershell
npm ci --prefix desktop
# 如果宿主 npm 的脚本策略没有安装 Electron 二进制，显式运行固定包自带的安装器。
# 需要本机代理时可先设置当前进程 ELECTRON_GET_USE_PROXY=1，不关闭 TLS 校验。
node desktop/node_modules/electron/install.js
$env:DESKTOP_RUNTIME_DIR="$PWD/.runtime/desktop-bundle-local"
# 输出目录必须是一个尚不存在的 workspace/.runtime 子目录。
$env:DESKTOP_DIST_DIR="$PWD/.runtime/desktop-preview-build"
npm run dist:win --prefix desktop
```

输出包含完整 setup.exe、SHA256SUMS.txt 和开发用 win-unpacked；没有资源网络下载型安装器，没有自动更新或发布。图标为本项目绘制的 SN 几何图形，源在 build/icon.svg，ICO 含 16–256 像素图层。构建只收录校验清单内的运行时文件和 9 个应用文件，不含测试、账户、学习记录、开发依赖或 .env。安装程序和主程序暂未签名，完整二进制安全扫描仍属 D4；这是预览产物。

安装包以当前用户权限安装，可选目录并创建桌面/开始菜单 Scarlet Nexus 快捷方式。双击快捷方式进入首次账号设置。默认数据仍为 %APPDATA%/ScarletNexus/learning；新壳显式固定 app 名为 ScarletNexus，浏览器配置与学习目录归同一用户配置根，保持 D2 默认学习目录。Electron 原生 --user-data-dir 可选择隔离配置根，验收使用它，不提供业务页面的任意路径 IPC。正式包仍拒绝 --dev-backend/--runtime-dir/--data-dir，且禁止把安装目录用作学习目录。

关闭软件后，可在 Windows 应用列表卸载；卸载仅清理安装文件、产品快捷方式和卸载注册，不删除学习库/密钥/附件。安装、卸载发现 Scarlet Nexus 正在运行时应拒绝继续，由用户正常退出；自定义 NSIS 检查没有强杀进程分支。ProgramData 的固定数据 junction 元数据保留，不跟随它递归删除数据。重装应接回原账号，不能依赖卸载重置密码。

本机隔离安装回归（会建立当前用户注册与产品快捷方式，拒绝覆盖已有安装/快捷方式）：

```powershell
powershell -NoProfile -File desktop/test/installer-smoke.ps1 -Installer "$PWD/.runtime/desktop-preview-build/ScarletNexus-0.1.0-d3.3-windows-x64-setup.exe"
```

该测试使用 workspace/.runtime 中文安装和配置路径，使用真实已安装 exe，移除全局工具 PATH，验证设置/登录/技能写入、运行中安装卸载拒绝、关闭重开、同版覆盖、卸载保留密钥、重装保留账号和数据，并最终卸载测试产品。测试记录保留；它不能证明干净 Windows 或普通非管理员账户通过。D3 仍待干净系统/中文用户名/离线网络验证、跨版本升级故障/备份容量/完整业务矩阵，见 [D3 验收](../docs/DESKTOP_D3_ACCEPTANCE.md)。

如需复现跨版本升级，installer-smoke.ps1 可追加 `-UpgradeInstaller "新版本完整安装包路径"`，初始包和升级包都须在 workspace/.runtime 内；它将验证发行标识变化确实生成备份，再验证卸载/重装后账号与技能。已安装旧版本或产品快捷方式存在时，测试会拒绝覆盖；失败时保留目录和安装状态供检查，不能手动删除数据库或强杀同名进程。

## D3 流式保护与业务回归

无需新增运行时依赖。backup.cjs 使用 Node 标准流 API，保持既有 SNDUENC1/v1 包络兼容；旧版加密备份不会被改写。资产清单校验同样改为流式摘要。启动器的备份导出进程仅获得 scarlet_backup 身份，失败只结束自己的子进程并阻止迁移。

```powershell
npm test --prefix desktop
npm run test:backup-large --prefix desktop
$env:DESKTOP_TEST_RUNTIME="$PWD/.runtime/desktop-bundle-local"
npm run test:upgrade-failure --prefix desktop
npm run test:runtime --prefix desktop
```

large 测试保留512MiB随机块重复文件和加密文件，观察独立进程 RSS，并以流式认证解密验证全部字节。upgrade-failure 在新的 .runtime 目录中仅为测试候选 JAR 增加失败 V13，生产 JAR/迁移不变；验证真实导出权限失败阻断迁移，及真正执行的 V13 回滚后 V12 原账号/技能/附件仍可打开。它不是磁盘满或断电实测。

已打包/安装程序的业务回归需要开发机已安装 frontend 的固定 TypeScript 和 Playwright 依赖；这些不会进入安装包：

```powershell
$env:DESKTOP_TEST_EXE="$PWD/.runtime/desktop-preview-build/win-unpacked/Scarlet Nexus.exe"
npm run test:business --prefix desktop
```

测试适配器直接执行 frontend/tests/e2e 中六个原有手动 spec 的原始断言，不改响应、不跳过断言；page/context 均属于真实 Electron 窗口。只适配相对 URL、测试注册/超时与截图目录，保留源码 SHA；明确设置 acceptDownloads:true，下载存入隔离 artifacts。首次省略此项时本环境未收到下载事件，保留超时日志；显式设置后真实下载目录/学习快照 JSON、导入预览与确认均通过。真实模型和移动端不在这套桌面测试里。

installer-smoke.ps1 加 `-BusinessRegression` 可在覆盖安装之后运行同一套六个旅程，随后继续验证卸载/重装。详细结果和剩余干净系统门禁见 [D3 第二批验收](../docs/DESKTOP_D3_RECOVERY_ACCEPTANCE.md)。
