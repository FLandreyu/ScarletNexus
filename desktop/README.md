# Scarlet Nexus Desktop

D1 安全窗口与 D2 内置运行时已实现。D3 安装包、桌面快捷方式和干净 Windows 验收尚未交付；此目录目前是开发与验收入口，尚未发布 GitHub 发行版。阶段边界见 [DESKTOP_PLAN](../docs/DESKTOP_PLAN.md)，实证见 [D2 验收](../docs/DESKTOP_D2_ACCEPTANCE.md)。

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

备份过程暂存的原始 dump 在正常路径和异常捕获后删除；父进程被强行终止时，未完成备份目录可能留下受私有 ACL 保护的 pending 文件，没有完成清单。大数据量内存占用、磁盘不足/断电、升级失败后的用户恢复流程仍须在 D3 验收。不要手工覆盖或删除真实用户数据目录。

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
