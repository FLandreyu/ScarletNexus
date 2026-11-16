# D2 内置运行时与持久数据验收

2026-11-17，起点048e5ea；GraphFlow未提供，定向检查。用户已授权D2，不改现有学习卷或已执行迁移，不发布/推送。

先定标准：
1. 官方Temurin Java25和EDB PostgreSQL17.12 Windows x64归档固定SHA，装配到忽略目录，包含原许可；生成逐文件完整性清单，缺失/篡改失败，无系统Java/PG/Docker回退。
2. 独立持久目录、用户ACL、启动锁、随机SCRAM口令与控制/迁移/运行/备份身份；拒绝已有不明目录、部分初始化/主版本不匹配，不使用trust或默认账号密码。
3. 首次窗口设置管理员，未配置不初始化业务；独立迁移成功才启动受限Web，私有附件/日志/配置在数据目录，AI默认关闭，口令不进入命令行/日志；旧账号不得重置。
4. 升级应用前成对备份数据库/附件，失败阻止迁移；保留旧数据与备份，不自动跨PG主版本升级。
5. 仅loopback/随机端口，识别自己拥有的服务；关闭优雅停止自己的Java与PG，异常退出后可验证恢复，不误杀其他进程/删除PID文件或用户目录。
6. 在原生Windows独立测试目录真实运行：初始化、认证、数据写入、重启保留、二次启动锁、错误/缺失/篡改资产拒绝、升级备份、正常及异常退出。再验证真实Electron首次设置/业务窗口。单测不能代替上述实证。

D3安装包、图标、离线干净Windows/卸载升级和D4全桌面供应链扫描/签名/Release另验，本批不冒称成品。

## 实现与本地证据（2026-11-17）

- 装配：`powershell -NoProfile -File desktop/assemble-runtime.ps1 -OutputRoot .runtime/desktop-bundle-d2-verified` 成功，日志 `.runtime-desktop-d2-assembly-verified.log`。官方源、版本与 SHA 固定在 desktop/runtime.lock.json；原始归档重新核对官方摘要，保留运行许可。新输出目录、防越界路径、Java 桌面入口存在性、Flyway schema 版本与每文件清单均已执行校验。完整清单没有签名，不提供发布者真实性保证。
- 后端：`wsl -d Ubuntu -- sh .runtime/testcontainers.sh` 的最终完整 verify 成功（`.runtime-desktop-d2-backend-confirmed.log`），31 单测 + 53 真实 PostgreSQL 集成测试，零失败、零跳过。新增 Java 私有 stdin 入口，业务逻辑和已执行迁移不变。启动前便监听父通道 EOF，运行只用 scarlet_runtime，Flyway 独立 migrator 进程先执行。
- 前端：format:check、typecheck、14 单测、build 均通过；保留既有图表 chunk 大小警告。桌面 Node 单测 7 项通过，覆盖路径/缺失/篡改、未知数据保护/锁、管理员输入、备份认证加密、子进程环境隔离和 D1 网关。桌面依赖 audit 零发现，见 `.runtime-desktop-d2-audit.json`；不等于二进制扫描完成。
- 原生 Windows：`DESKTOP_TEST_RUNTIME=.runtime/desktop-bundle-d2-final`（命令应设为绝对路径），`npm run test:runtime --prefix desktop` 通过，见 `.runtime-desktop-d2-native-final.log`。直接运行内置 Java25/PG17.12，无系统 Java/PG/Docker 回退。真实初始化到 V12、管理员 CSRF 登录、全部认证规则为 SCRAM、错误密码拒绝，三个业务角色没有超级用户/创建角色/建库/复制/bypassrls 权限；runtime DDL、backup DML 拒绝。真实数据关闭重开保留；升级前数据库/附件加密备份，解密 dump 实际恢复到另一隔离数据库，核对业务表数与持久化测试行，附件字节一致，结束停止专属服务。
- Electron：对最终重新装配的 `.runtime/desktop-bundle-d2-verified` 执行 `npm run test:managed --prefix desktop` 通过，日志 `.runtime-desktop-d2-managed-confirmed.log`。真实首次设置窗口、随机测试账号、登录、中文技能写入、关闭、重开仍可见同一技能，无 pageErrors；管理员暂存口令在 ready 后已删除；正常退出后 launcher.lock/postmaster.pid 移除。截图 `.runtime/desktop-d2-setup.png` 与 `.runtime/desktop-d2-skills.png`，测试数据不提交。
- 异常生命周期：`npm run test:lifecycle --prefix desktop` 独立验证启动期间 EOF、故障注入后 Java 退出、遗留专属 PG 的核验接回与停止。最终结果和失败修正见下方生命周期复核。

## 实际遇到的问题与保留边界

- EDB initdb 在当前中文项目路径发生编码错误；使用受用户 SID ACL 保护的 ProgramData 固定 junction 提供 ASCII 路径，真实目标不移动，已有链接必须精确匹配。保留最初失败日志，未用 trust 或系统 PG 绕过。Windows pg_ctl 的后台子进程持有 stdout 管道导致等待不退出，改为 spawn + ignore stdio 后真实初始化通过。
- 故障测试最初在 Windows 上只等待 exitCode；被 signal 终止时 signalCode 已设置而 exitCode 仍为空，造成测试等待超时。修正测试同时检查 signalCode；保留失败日志 `.runtime-desktop-d2-lifecycle.log`。失败目录保留，使用同一启动器验证所有权后接回、停止，未删数据库或 PID 文件。早期 Electron 调试驱动故障注入曾挂起，不据此宣称 GUI 崩溃测试成功；GUI 的实证为正常关闭重开，异常恢复由原生父进程故障测试单独证明。
- 默认持久目录 `%APPDATA%/ScarletNexus/learning`，测试全部位于 workspace/.runtime。控制/迁移/业务/备份随机口令与备份密钥保存在用户 ACL 隔离目录，不在命令行/错误消息中。升级内部格式 `scarlet-desktop-upgrade-backup/v1` 与 Web SNBACK 分开，没有恢复 UI/自动回滚，也不自动跨 PG 主版本升级。
- 数据库导出加密前暂存明文；正常和捕获错误路径均删除，强行结束父进程可能留下受私有 ACL 保护的 pending 文件，没有完整清单。大体量备份内存、磁盘不足/断电、普通用户 ProgramData 可写性、中文用户名/安装目录、干净 Windows 无全局工具、安装/卸载/junction 与升级恢复流程留 D3。
- D2 是本机运行时切片验收，不是安装包/离线干净系统/远端 CI/完整桌面业务矩阵验收；Electron/Java/PG 制品扫描、发行签名、GitHub Release 留 D4。P0–P6 Web 结论保持，不自动进入 P7。本批 GraphFlow 工具未提供，未执行 context/plan/index；采用定向文件检查。没有推送、公开部署或发布。

## 生命周期复核与本批结论

最终 `.runtime-desktop-d2-lifecycle-confirmed.log` 成功：earlyParentEofStopsJava、parentLossStopsOwnedJava、recoveredOwnedPostgres、recoveryStoppedServices 均为 true；针对故障父进程进行定点终止，核对原 Java PID 已退出，再以旧数据与账号信息接回同一 PG，未重新设置管理员。超时测试留下的专属集群也已用正式所有权核验路径接回并停止，见 `.runtime-desktop-d2-lifecycle-recovery-confirmed.log`；过程中一次 PowerShell node -e 引号错误没有执行恢复，随后改为私有脚本成功，原失败日志保留。

已查看首次设置与中文技能截图，布局和文字可读。最终项目路径限定的进程检查没有残留本次 Java/PostgreSQL/Electron；未触碰其他 Java 工具进程。D2 本地切片验收通过，下一步 D3；尚未构建安装包或推送/发布。Windows CI 已扩展到运行时/设置桥语法检查及7项单测配置，但本批没有远端 CI 运行记录。
