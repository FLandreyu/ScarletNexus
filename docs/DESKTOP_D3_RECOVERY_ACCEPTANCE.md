# D3 第二批：流式升级保护与失败回归

2026-11-17，从 d325f66 继续。GraphFlow 无工具，定向检查。保留 .env.example/.gitignore，无推送/发布/系统账户或虚拟化功能修改。

实施前标准：

1. pg_dump 直接通过管道加密，数据库与附件流式处理；保留既有 SNDUENC1 AES-256-GCM 格式和摘要，不再创建明文 pending dump，完整清单最后写。失败关闭本次子进程/服务，不把不完整备份标为成功。
2. 现有小备份解密兼容；512MiB 文件真实加密与逐字节摘要验证、独立进程峰值内存观察；来源/写入错误和既有目标保护。不可用模拟错误宣称真实磁盘耗尽通过。
3. 原生 Windows 真实 PG：升级前备份导出失败阻止迁移；另一个仅测试候选 JAR 新增失败 V13（不修改已执行迁移），确认先完成备份、Flyway 事务回滚、V12/原账号/业务行/附件仍保留，旧版可重新打开。生产 JAR 不变。
4. 打包新预览并用已打包 Electron 跑现有手动业务旅程/附件/导入导出，确认源码与制品一致，保留失败日志。
5. 干净 Windows 普通账户/中文用户名/真实断电/磁盘满仍须独立实证；本批不可因此关闭 D3 或进入 D4。

## 本批实现与本机结果

- 新增 backup.cjs，仅 Node 标准库。数据库 stdout 直接加密，附件与资产清单摘要流式处理，SNDUENC1/v1 头保持36字节；管道成功写入认证标签并 fsync。导出退出非0/来源错误/附件错误均不写完成清单，失败只结束本次导出子进程，保留加密片段；片段信封可认证并不证明数据库导出完整。已不再生成 database.pending.dump。
- Node9项测试通过，新增分块加密兼容/摘要/目标不覆盖，以及来源错误、导出失败与导出程序缺失的失败传播；语法/Prettier通过。前端格式/类型/14测试/构建通过，保留既有图表chunk警告。npm audit 0发现（`.runtime-desktop-d3-stream-audit.json`），非二进制安全扫描。后端/Flyway生产源码与JAR未改，沿用D2已过84项的同字节JAR，不宣称本批新跑84。
- `npm run test:backup-large --prefix desktop`：真实512MiB文件加密、全量流式GCM认证解密、原文SHA一致；每5ms采样的增量峰值RSS为77783040字节，约74.2MiB，非512MiB整文件分配（`.runtime-desktop-d3-stream-large.log`）。仅此文件规模/当前进程的测量，不声称任意数据量恒定内存或数据库导出本身同样峰值。
- `DESKTOP_TEST_RUNTIME=.runtime/desktop-bundle-d2-verified`（实际设绝对路径），`npm run test:upgrade-failure --prefix desktop` 通过，见 `.runtime-desktop-d3-upgrade-failure.log`。隔离候选JAR新增 V13，仅测试、不替换生产JAR；其他不可变运行资产硬链接后只读使用。真实创建技能和私有附件，撤销备份角色对skills的SELECT后，pg_dump失败且迁移阻断、无完成清单/明文dump、state保持；恢复授权后升级先完成加密备份，再由V13的RAISE主动失败。PG日志确认 desktop_v13_expected_failure 真正执行，候选表不存在、Flyway成功版本仍12、state保持。旧版重新登录原账号，技能和附件字节保留，服务正常停止。
- `npm run test:runtime --prefix desktop` 使用流式实现重跑真实原生 PG 初始化/认证/SCRAM/角色限制/重启/备份恢复全部通过（`.runtime-desktop-d3-stream-native.log`），备份实际解密并 pg_restore 到隔离数据库，表数与业务行、附件一致。
- 业务适配器使用 TypeScript5.9.3 转换现有六个手动spec，仅注册/超时/相对URL/输出目录适配，断言和真实 Electron page/context 保持，记录源码SHA；不是新浏览器代替桌面窗口。首轮前5个通过，analytics-data 等待download超时（`.runtime-desktop-d3-business-baseline.log`）；截图显示JSON生成。显式 acceptDownloads:true +隔离artifacts后6个通过（`.runtime-desktop-d3-business-downloads.log`），真正读取下载目录JSON和本人学习快照，随后完成导入预览/确认，未改业务代码或删除断言。

## 新预览与已安装验证

构建命令和原料与D3第一批相同，`DESKTOP_DIST_DIR=.runtime/desktop-d3-stream-preview` 后 `npm run dist:win --prefix desktop` 成功（`.runtime-desktop-d3-stream-build.log`）。新包：

- `.runtime/desktop-d3-stream-preview/ScarletNexus-0.1.0-d3.3-windows-x64-setup.exe`，312018570字节；SHA-256 `fd5d82dde4aed2854afca42925913428f0130369eee63db8a217ef802985dac8`，旁置 SHA256SUMS。
- app.asar白名单9文件，包含新 backup.cjs；八个源码/设置资源与工作树字节一致，另一个是生产package.json；release=0.1.0-d3.3。生产 backend.jar SHA为 `3e160d55a5715c047457dcf411dddcda8130ae8af338bee7fd2a58bbf57fdb6b`，没有候选V13进入正式制品。Authenticode NotSigned，D4未开始。

实际执行：

```powershell
powershell -NoProfile -File desktop/test/installer-smoke.ps1 -Installer .runtime/desktop-d3-preview/ScarletNexus-0.1.0-d3.2-windows-x64-setup.exe -UpgradeInstaller .runtime/desktop-d3-stream-preview/ScarletNexus-0.1.0-d3.3-windows-x64-setup.exe -BusinessRegression
```

`.runtime-desktop-d3-stream-installed.log` 已确认 d3.2初始/重启、d3.3覆盖、运行中安装卸载拒绝、cross_version_upgrade_backup=true；随后在真实已安装d3.3 EXE中六个原旅程全部通过，installed_business_regression=passed，page/context非开发浏览器：系统真实PG/V12、知识树/环拒绝/草案版本/登出、学习暂停继续/路线历史/完成、独立作答/提示/自评证据/私有图片、三技能预算计划/到期复习、分析/两个JSON下载/导入确认。结果和spec SHA在 `.runtime/desktop-business-dcb20f4d-1827-4d94-b06f-e83c0c7c0e23/results.json`。业务阶段全局工具PATH移除，实际仍用内置运行时。

最终卸载/重装与进程检查结果在下方收尾记录；没有以中间成功替代完整安装往返。

## 保留门禁

宿主 WindowsSandbox.exe、常用 VirtualBox/VMware路径和Hyper-V模块均未发现；没有启用系统功能、创建系统账户或下载系统镜像。干净Win11、真正普通账户/中文用户名、断网无全局工具、真实断电/磁盘满仍未实测；中文安装/配置路径和移除PATH不等于这些环境。512MiB文件不替代全部容量与故障矩阵。D3保持进行中，D4/签名/公开Release未开展。内部升级备份不是SNBACK、没有自动降级/跨PG主版本升级；不把新增恢复UI扩大为D3硬门禁，仍需完善实际用户恢复说明与隔离恢复验证。

## 收尾记录

最终 `.runtime-desktop-d3-stream-installed.log` 退出0，installed_business_regression=passed、installer_roundtrip=passed；重装继续使用原账号/技能，卸载前后数据库密钥SHA不变，最终安装文件/快捷方式/注册项移除而学习目录保留：`.runtime/desktop-d3-install-1798602b36ed4ddcb178404e48660b31/独立用户`。已查看已安装程序 catalog-confirmed.png，目录确认导入与学习记录保留显示正常。最终项目所属 DesktopApplication/PG/产品进程与产品卸载项检查无残留；其他工具进程未触碰。用户.env.example/.gitignore保持原改动。

本批可以交付d3.3本机预览和可复现验证，D3剩余环境/故障门禁不变，不推送或发布。
