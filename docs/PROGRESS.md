# 开发进度

更新：2026-11-17（Asia/Shanghai）。正式需求：根目录总纲 v1.0。

## 当前阶段

测试账户准备更新（2026-11-17）。用户报告已使用芙兰账户登录，宿主vmrun list确认ScarletNexus-D3-Clean正在运行。账户实际USERPROFILE及Administrators组成员状态仍需客体只读输出核验，随后采集环境和离线状态、保存S0快照。已确认离线测试介质与8文件清单仍在。尚未运行安装/业务验收，GraphFlow工具未提供，未变更业务代码。

用户报告Windows11已安装完成（2026-11-17）。本轮只读查询的运行VM数量为0，尚未在客体核验版本、真实用户路径和权限。软件测试ISO及此前8文件SHA验证报告仍在。下一步在客体建立实际路径含中文和空格的标准账户，记录系统/工具/网络基线，再保存S0快照并执行d3.3安装验收。Windows安装完成当前依据用户报告，标准账户/离线/安装与业务门禁继续待验证。GraphFlow工具未提供，定向检查，无业务代码变更。

Windows安装器磁盘识别排查（2026-11-17）：用户截图证实已通过ISO进入Windows安装器，但安装位置列表为空。只读核对系统盘仍存在，VMX为scsi0.virtualDev=lsilogic/scsi0:0.present=true，vmware.log识别容量209715200个512字节扇区（100GiB）；尚无磁盘丢失证据。优先怀疑旧式LSI Logic驱动兼容，交接用户关机后仅从VM移除硬盘设备、保留VMDK，再以SATA/使用现有虚拟磁盘重新接入同一VMDK；不新建盘、不删除文件、不改宿主盘。尚未实际修改或验证恢复识别，不将推测记作已确认根因。加密VM不可直接编辑，当前桌面控制不可用；D3仍未完成，GraphFlow未提供，无业务代码改动/业务测试重跑。

TPM/加密配置已核对（2026-11-17）：用户完成VMware设置，VMX检测到vtpm.present=true、encryption.keySafe及encryption.data，EFI/Secure Boot和已校验系统ISO路径仍在；仅记录存在性，不输出加密材料。加密后的原生Power/Chipset查询要求stdin密码，未请求或传入密码，不能声称原生运行配置查询通过。已通过现有VMware程序请求打开此VM，需用户在本机解锁并启动，再确认进入Windows安装界面。尚未验证安装/客体业务，不关闭D3；证据 `.runtime/desktop-empty-vm/tpm-handoff.json`。未改加密VMX，无密钥入Git；GraphFlow不可用，业务未变不重复测试。

Windows ISO已校验并接入空VM（2026-11-17）：用户已下载到 `D:/Downloads/Windows11_Client_x64_zh-cn_26300_9457.iso`，9112440832字节，SHA256 `b795b52d598bf8fdea5975844731f4a50c5713876cefc513884354272b023af0`，与当前微软下载页“简体中文64-bit”完全一致。VM关机/配置SHA检查通过后，仅改虚拟光驱来源与开机连接设置，未挂载宿主或运行setup，未复制9GB镜像；网卡仍默认断开。

- 原VMX备份和新证据在 `.runtime/desktop-empty-vm/before-os-iso.vmx`、`iso-verification.json`；原verification.json的配置SHA是此前历史快照，新配置SHA见新报告。原生Disk查询确认ISO路径与startConnected=true，运行VM为0。
- 桌面控制native pipe仍不可用，需用户在VMware选项/访问控制中按受支持流程启用加密，再添加Trusted Platform Module；尚未加密/添加vTPM/安装Windows或创建账户。ISO下载不代表额外Windows许可已具备，未购买或激活。D3客体/真实故障仍NOT_RUN，D4未开始；GraphFlow未提供，业务未变，不重复业务测试。

Windows镜像准备更新（2026-11-17）：已创建宿主下载目录 `D:/VMs/ISO`。官方语言查询成功，但下载链接接口返回 `ErrorSettings.SentinelReject`，未下载ISO；随后命令启动浏览器被自动审批策略拒绝（blocked by policy），改由用户手动打开微软官方页，选择x64多版本ISO/简体中文，保存到该目录。不绕过请求拒绝，未取得/校验ISO，未配置vTPM或安装系统，空VM保持关机；许可仍未确定，D3门禁不变。仅改记录，无业务测试重跑；GraphFlow工具仍未提供。

D3空VM已搭建（2026-11-17）：用户明确先搭空测试机，暂不安装Windows。发现并复用非默认位置 `D:/VMware` Workstation17.6.4（程序签名Valid/原有服务运行），替代原VirtualBox建议；未安装新驱动或改变宿主WSL/Hyper-V/VBS。VM在 `D:/VMs/ScarletNexus-D3-Clean`，4核/8GiB/100GiB动态盘、EFI/Secure Boot配置、默认断网与禁用宿主共享，桌面已有 `ScarletNexus D3 VM` 快捷方式。详见 [空VM搭建记录](DESKTOP_EMPTY_VM_SETUP.md)。

- 配置查询、VMDK头容量与磁盘链检查通过；真实start返回0/PowerState on，日志记录ULM/WHP、EFI和No Media；随后关闭，运行VM为0。仅证明空VM启动，不证明Windows或应用可用。
- 无Windows系统ISO/客体账户/安装验收；正式vTPM2.0及其所需加密尚未配置，待镜像/许可确定后完成，不绕过TPM。桌面控制native pipe不可用，截图未成功，不声称视觉验收；私有报告 `.runtime/desktop-empty-vm/verification.json`。
- D3干净客体及真实故障门禁仍NOT_RUN，D4未开始。仅改文档和本机空VM，无业务代码变更，未重复业务测试；GraphFlow未提供，定向检查，保留用户.env.example/.gitignore，不推送。

以下为此前只准备方案/测试介质时的记录。

D3 环境准备（2026-11-17）：用户确认没有现成VM/第二台电脑，授权先准备方案。本机CIM确认为Windows11家庭中文版（CoreCountrySpecific），微软不支持Home Sandbox；已写 [测试环境方案](DESKTOP_TEST_ENVIRONMENT.md) 与 [客体验收表](DESKTOP_GUEST_CHECKLIST.md)，建议VirtualBox7.2.22基础包+合法Windows11 x64客体，4核/8GiB/100GiB动态盘放D，普通中文空格账户、离线、只读ISO、快照与独立故障小盘。不安装Guest Additions来掩盖依赖，不自动关闭WSL/Hyper-V/VBS。

- 只读Host审计实际执行：`.runtime/desktop-clean-windows-host.json`，记录约32GB内存、HypervisorPresent=true及C/D容量、工具/运行库/连接情况。部分CPU能力false不作为BIOS关闭证据；令牌组/提权记录不替代完整客体身份基线。报告一直是ENVIRONMENT_OBSERVATION_ONLY，业务/安装NOT_RUN。
- 已生成离线介质 `.runtime/desktop-clean-windows-kit-ready/ScarletNexus-D3-offline-test.iso`，624117760字节，SHA256 `4e4fda3f18fa16a35ec60e156333b1c0fe9e3a31723e0446b30a312b8dfdb835`。只含两个既有SHA锁定d3.3/d3.4安装包、只读审计脚本、方案/恢复/验收文本、manifest与SHA表，共8文件，无学习库/密钥/测试账号/系统镜像。状态NOT_RUN。
- PowerShell语法、Host真实只读执行、拒绝覆盖介质/报告和拒绝输出越界通过。IMAPI生成ISO后，专用只读ISO9660/Joliet解析器逐文件SHA比较通过，未挂载/执行安装器；验证JSON在同目录verification.json。缓存7za不支持该ISO解析而误识别内嵌7z数据，未据此判通过，改为检查CD001/Joliet目录和全部文件字节。
- 业务/前后端代码未变，未重复应用测试；本批只有方案、诊断与介质验证，不关闭D3。未下载/安装VirtualBox或Windows镜像、创建VM/账户、改系统功能或重启。GraphFlow无工具，定向检查；保留用户.env.example/.gitignore，不推送/发布。下一步实际安装虚拟化驱动与准备授权OS镜像需进入明确实施范围。

以下d3.4及之前为已完成本机批次记录，不能替代干净客体门禁。


桌面发行版 D3 第三批（2026-11-17）：d3.4新增已安装EXE恢复入口，只向空的新库恢复，源库/备份不覆盖。包含沙箱进度窗口、密文快照预验证、单事务恢复、原授权复核、旧会话清理及READY图片引用校验。步骤见 [桌面恢复说明](DESKTOP_RECOVERY.md)，证据见 [D3第三批](DESKTOP_D3_RESTORE_ACCEPTANCE.md)。D3保持进行中，D4未开始。

- 新预览 `.runtime/desktop-d3-restore-preview/ScarletNexus-0.1.0-d3.4-windows-x64-setup.exe`，312021752字节，SHA256 `01932a1d2151ab34c4edaa7f8150b689dce319f124b10bf1465d1c6c0703d7d8`，未签名/未发布；白名单13文件，无测试密钥或用户数据。
- 原生PG恢复成功、原密码/技能/正规PNG恢复并可重开，密钥口令全部轮换、旧会话401；DDL/历史修改仍拒绝，源密钥/密文摘要保持（`.runtime-desktop-d3-restore-native-r3.log`）。错误密钥/篡改在PG初始化前拒绝，非空/未完成目标不开放；认证有效但缺失READY图片的副本也拒绝（restore-missing日志）。
- 真实权限复核曾拦截新库默认CRUD叠加；清除初始化默认权限再回放ACL后通过，失败现场/日志保留，不绕门禁。512MiB生产加解密全量SHA通过，观测增量RSS约69.4MiB。11项Node/语法/格式、前端格式/类型/14测试/构建通过，npm审计0；Java/Flyway/JAR未改，84后端沿用此前同字节实证，不声称新跑。
- 已打包恢复入口通过沙箱窗口、原账号/图片与普通重开；实际安装d3.3→d3.4与六条业务及恢复入口回归见 `.runtime-desktop-d3-restore-installed.log`，收尾结果在第三批验收文档。
- 最终已安装入口/六旅程/安装往返全部passed；重装保留数据、密钥SHA和原账号。恢复窗口显式sandbox/contextIsolation/noNode复核通过，已查看进度/恢复技能截图；最终无本程序服务或产品卸载项残留。13文件与源码一致，预览仍未签名。
- **下一步仍为D3环境/故障门禁**：干净Windows普通账户/中文用户名/断网、真实断电/磁盘满与更多容量。没有擅自改变系统。GraphFlow不可用，定向检查；保留用户.env.example/.gitignore，无推送/发布/公开服务，不进入P7。

以下第二批及之前为历史记录。


桌面发行版 D3 第二批（2026-11-17）：d3.3 将数据库导出、附件加密和资产摘要改为流式，移除明文pending导出，保持旧格式兼容；补齐真实PG失败迁移回滚与已安装桌面六个手动旅程。详细证据与范围见 [DESKTOP_D3_RECOVERY_ACCEPTANCE](DESKTOP_D3_RECOVERY_ACCEPTANCE.md)。D3仍进行中，未进入D4。

- 新本地预览 `.runtime/desktop-d3-stream-preview/ScarletNexus-0.1.0-d3.3-windows-x64-setup.exe`，312018570字节，SHA256 `fd5d82dde4aed2854afca42925913428f0130369eee63db8a217ef802985dac8`，未签名/未发布；应用白名单9文件与源码吻合，生产JAR不含测试V13。
- 9项Node测试、语法/格式、前端格式/类型/14测试/构建通过，npm审计0；512MiB认证加密解密全字节SHA一致，观测增量峰值RSS约74.2MiB。真实PG新实现备份实际恢复到隔离库通过（`.runtime-desktop-d3-stream-native.log`）。生产Java/Flyway未改，84后端沿用D2同字节JAR实证，不声称重跑。
- 原生PG备份权限失败阻断迁移；测试候选V13确实执行后RAISE失败、事务回滚，state/原技能/附件保持，旧V12原账号重新登录通过（`.runtime-desktop-d3-upgrade-failure.log`）。测试Jar只存在私有.runtime，未改正式迁移。
- 实际安装d3.2→d3.3产生流式升级备份，并在已安装新EXE跑原始六条手动业务旅程全部通过，含私有图片、计划复习、真实JSON下载和目录导入（`.runtime-desktop-d3-stream-installed.log`）。下载首轮未接管导致超时已保留，明确设置acceptDownloads后原断言通过。
- 最终安装回归退出0，installed_business_regression/installer_roundtrip均passed；卸载后重装保留原账号/技能和密钥SHA，最终只清理产品安装/快捷方式/注册，隔离数据保留。已查看目录确认导入截图，无本次Java/PG/产品服务残留。
- **下一步仍为D3剩余门禁**：干净Windows普通账户/中文用户名/断网、断电/磁盘满、更多容量和用户恢复说明。宿主无可用Sandbox/常用虚拟化工具，未擅自修改系统。GraphFlow未暴露，定向检查；用户.env.example/.gitignore保留，无推送/Release/公开部署，P7不自动进入。

以下第一批、D2与D1为历史记录。


桌面发行版 D3 第一批（2026-11-17）：完成本机离线预览安装包切片，版本 0.1.0-d3.2。固定 electron-builder26.18.0、完整运行时、当前用户 NSIS、SN 产品图标、桌面/开始菜单快捷方式、白名单装包、卸载保留学习数据；安装前拒绝数据树和运行中的产品。**D3 仍在进行，不能标记整阶段完成**；详细门禁见 [DESKTOP_D3_ACCEPTANCE](DESKTOP_D3_ACCEPTANCE.md)，构建/测试命令见 [desktop/README](../desktop/README.md)。

- 本地预览：`.runtime/desktop-d3-preview/ScarletNexus-0.1.0-d3.2-windows-x64-setup.exe`，312017988字节，SHA-256 `72285cfcca6d9603e3e7aa76b014d3d14425cfa0c4b4251b77d12631fa963758`；旁置 SHA256SUMS.txt。未签名、未发布，不把 npm 审计当全二进制扫描。
- 实际已安装 EXE 验证：中文安装/独立配置路径、首次设置/CSRF登录/中文技能、关闭重开；移除全局工具 PATH 仍使用内置 Java/PG。已验证同版覆盖/卸载重装保留账号与技能（第一轮 `.runtime-desktop-d3-installer-roundtrip.log`）；跨版 d3.1→d3.2 保留业务并生成加密升级备份，最终完整日志见 `.runtime-desktop-d3-installer-upgrade.log`。
- 新版安装器在清旧版之前检查数据标记祖先链，拒绝安装进学习/数据库树；运行中安装、直接等待的卸载脚本均拒绝而不关闭业务窗口。标准 NSIS 外层卸载进程会先返回，测试用 `_?=` 等待真实脚本；原误判日志保留，未发生数据删除。默认 userData 兼容 D2 路径的独立探针已通过。
- 桌面7单测/语法/格式、前端格式/类型/14单测/构建通过；后端与迁移无修改，沿用 D2 已通过84项真实PG验证的同字节JAR，不宣称新跑后端。构建依赖6中风险定向固定修复范围到 minimatch11.0.9；重新生成锁与 npm ci 后 npm audit 为0，实际构建成功。初次架构参数误传和 Electron dist 缺失等开发失败日志保留。
- 未提供 GraphFlow 工具，未 context/plan/index；用户 .env.example/.gitignore 保留，无生产、公开服务、推送或 Release。
- **下一步继续 D3**：干净 Win11 x64 普通账户/中文用户名/断网无全局工具的真实验收，完整业务/附件导出恢复矩阵、跨 schema 升级失败与断电/磁盘不足/大备份恢复。宿主没有 Windows Sandbox，未擅自启用系统功能。D4 的制品扫描、签名及发行仍未开始，P7不自动进入。

以下 D2 与 D1 为已完成批次记录。


桌面发行版 D2（2026-11-17）：内置 Temurin25/PG17.12 装配与原始归档 SHA、首次管理员窗口、独立 ACL 持久数据、随机 SCRAM 和控制/迁移/业务/备份身份、V12 独立迁移、启动锁和服务所有权核验、私有 stdin 生命周期、升级前认证加密备份已实现。完整验收与保留边界见 [DESKTOP_D2_ACCEPTANCE](DESKTOP_D2_ACCEPTANCE.md)，复现命令见 [desktop/README](../desktop/README.md)。

- 最终后端 verify：31 单测 + 53 真实 PostgreSQL 集成测试通过，零跳过（`.runtime-desktop-d2-backend-confirmed.log`）；前端格式/类型/14 单测/构建通过，保留原有图表 chunk 提醒；桌面7单测与语法通过，npm audit零发现，不等于二进制扫描。
- 原生 Windows 内置运行时实际初始化、CSRF 登录、SCRAM与越权拒绝、重启持久化、升级前库/附件加密及隔离数据库实际恢复通过（`.runtime-desktop-d2-native-final.log`）。最终装配脚本独立执行成功（`.runtime-desktop-d2-assembly-verified.log`）。
- 真实 Electron 窗口首次设置、登录、中文技能写入、正常关闭重开数据保留、退出后本程序服务停止通过，无页面异常（`.runtime-desktop-d2-managed-confirmed.log`）。原生故障测试确认启动期 EOF 与父进程丢失均退出 Java，遗留专属 PG 核验接回后可停止（`.runtime-desktop-d2-lifecycle-confirmed.log`）；Windows signalCode 测试等待修正与失败保留见 D2 验收。
- 当前中文项目路径上的 initdb 编码与 pg_ctl 后台管道问题已定位修正；失败日志与隔离目录保留，未删除真实数据/PG PID。备份 pending 明文的强制中断窗口、备份容量、ProgramData junction 普通用户/中文用户名与卸载验证明确留 D3。
- GraphFlow 未提供，未执行 context/plan/index。`.env.example` / `.gitignore` 用户改动保留；未修改已执行迁移，无公开服务、Release或推送。
- **下一步 D3**：Windows 离线安装包、桌面/开始菜单图标、卸载保留数据、普通用户和干净 Windows 验收、完整桌面业务与安装升级回归。D4 再完成制品供应链扫描与发行准备。D2 完成不表示已有可下载安装的成品；P0–P6 Web 保持关闭，P7不自动启动。

以下 D1 与 P6 为历史记录。


桌面发行版D1（2026-11-17）：用户授权开始桌面开发，按DESKTOP_PLAN/ADR-041新增独立desktop工程，固定Electron45.3.3和Playwright1.65.1及依赖锁；保留Vue/Java25/Boot4/PG业务栈，无业务/迁移/个人配置改动。完成独立窗口、单实例、Node禁用/沙箱/上下文隔离、外部导航/重定向/新窗口/权限阻断，以及随机loopback同源网关（Host/Origin/文件与运维路径隔离、API代理头清洗、SSE不缓冲、窗口退出关闭网关）。显式开发后端仅127.0.0.1，packaged模式在内置运行时交付前拒绝启动。

- Node24两项集成式单测通过，覆盖远程URL/凭据拒绝、真实HTTP SPA/文件/Host/Origin隔离、Cookie/SSE传输；非业务假响应，真实业务另由原生Electron验证。CJS语法与Prettier检查、npm audit零发现通过（.runtime-desktop-d1-audit-final.json）；不等于Electron/Chromium二进制扫描通过，该项留D4。
- 原生Windows Electron实际窗口冒烟通过：连接原scarlet-nexus-p6-backup-role-acceptance隔离PG/V12服务，真实登录并读取成长分析，页面无JS异常，Node不可访问、sandbox/contextIsolation为true；外部导航拦截、二次启动退出且仅一个窗口、退出后网关无法连接均通过（.runtime-desktop-d1-smoke-confirmed.log）。截图.runtime/desktop-d1-analytics.png已查看，含隔离验收数据，不入Git、不冒充个人数据。开发测试脚本可复现，文档desktop/README。
- 初轮npm安装后Electron按需下载未使用代理而失败；设置本进程ELECTRON_GET_USE_PROXY后官方校验下载成功。VS Code继承ELECTRON_RUN_AS_NODE造成首次窗口启动失败，测试子进程明确移除后通过；导航阻断测试改为不等待被阻止的导航完成，原失败日志保留。没有关闭TLS校验或删除测试。
- 新增Windows桌面unit/语法CI作业，远端尚未运行，不宣称桌面CI/安装包/干净Windows验收通过。应用前后端未变，不重复P6整套；仅复用隔离服务做真实桌面验证，结束后停止本验收项目，PG/附件卷保留。用户.env.example/.gitignore保持，无生产/付费/Release/推送或系统服务修改；GraphFlow仍不可用，未context/plan/index。
- **下一步D2**：内置Java25/真实PG17运行时、身份与持久数据初始化、服务进程生命周期和升级保护。D3才交付桌面图标/安装包/干净Windows验收；当前D1仍依赖显式开发后端，不是可独立下载安装的成品。P0–P6 Web验收结论保留，P7不自动启动。

以下P6关闭与历史记录不代表桌面发行版已经完成。

P6第十四批收尾（2026-11-17）：按总纲P6逐项关闭Web功能、部署准备和验收，**P0–P6核心Web版本开发阶段通过**；详细证据、保留局限及关闭标准见P6_CLOSEOUT。核对远端d2e67c9运行43903457274 completed/success，六作业全部通过（84后端/14前端/四组24浏览器旅程，含真实PG、恢复和TLS/SSE）；当前与该源码差异仅文档。本批新跑只读安全复核退出0、16目标、当前锁/时效一致、有效产物无高/严重，原扫描SHA/日期不改，不冒称重新扫描或重跑应用测试。

此前将未授权的生产实际部署混入P6关闭门禁，与总纲“部署准备/说明”和ROADMAP“生产部署另行确认”不符；ADR-040明确按原范围关闭，不放宽功能、恢复或高/严重阈值。当前中低/未知台账、2026-11-23/30复查、生产域名/续期/旧库切换/持续告警/异地灾备保留，不等于生产就绪。桌面图标/安装包/内置运行环境尚未实现，列为用户新增独立交付范围；不自动开始P7。无业务/依赖/配方/Flyway改动，无生产/付费调用/Release，用户.env.example/.gitignore保持。GraphFlow未提供，定向核对，未context/plan/index；本地可回滚文档提交，不自动推送。

以下为历史分批记录，旧“P6未完成”结论以当时证据为准，当前结论以上述及P6_CLOSEOUT为准。

P6第十三批远端CI修复（2026-11-17）：两次首次推送均因干净runner的Engine缺少Go测试夹具镜像而失败；BuildKit缓存不等于Engine已加载。verify_ryuk显式拉取锁定linux/amd64摘要后才执行全套测试，新增拉取失败关闭和执行顺序回归。本地36脚本/全部Go通过（.runtime-ci-ryuk-fixture.log）；修复d2e67c9推送后，远端运行43903457274最终completed/success，前端、后端及HTTP/TLS桌面/手机六作业全通过，含84后端真实PG、14前端、四组六故事共24浏览器旅程、备份隔离恢复及TLS/SSE/资源验证。标准、原始失败与通过证据见P6_CI_ACCEPTANCE，不跳过测试或减少矩阵。仅runner/测试/文档变更，不改应用/依赖/配方/Flyway；GraphFlow仍未提供，无context/plan/index。用户.env.example/.gitignore保留；P6整体尚未完成、桌面发行版未实现。下一步收尾P6功能与风险边界，再开展桌面交付设计，不发布Release或生产部署。

GitHub连接与首次推送：origin为https://github.com/FLandreyu/ScarletNexus.git，连接时远端为空。用户明确要求推送并完成本机设备授权后，以FLandreyu账号将当前已提交历史推送到远端main，首次远端HEAD为b0a2dbb；本机ls-remote核对一致。凭据通过Git Credential Manager保存，仓库本地指定GitHub用户名以避免多账号选择歧义，不写入令牌。保留本地appmod/java-upgrade-20261116030222分支及用户.env.example/.gitignore未提交修改，不上传运行数据/秘密、不发布Release。推送可触发Quality gates，其远端结果尚未核验，不把本地通过视为远端CI通过；手动安全workflow未主动触发。仅远端配置/记录变更，无应用测试重跑。

P6第十二批：固定Ryuk源码适配官方拆分SDK，连同测试构建目标纳入正式门禁；当前应用回归通过，最终16目标扫描结果见P6_RYUK_SDK_MIGRATION。P6整体仍待收尾复核，未启动P7或生产部署。

- 起点e75a6a5，先定义P6_RYUK_SDK_MIGRATION标准。固定0.14.0归档和client0.6.3/api1.57.0，提交可审查补丁、Go模块锁、源码输出SHA和MIT版权；保持ACK、标签过滤、超时/重试、清理顺序及Force语义，不禁Ryuk、不用ignore/VEX。新增patch2.8.1-r0仅用于构建；配方覆盖全部资产，最终镜像保留版权与可识别符号/元数据。ADR-039记录维护边界。
- 初期15目标扫描为零，但补查新测试镜像发现compress/archive两高，原报告完整保留；升级compress1.18.7/go-archive0.3.0及必要间接依赖，适配compression.None测试API。将ryuk-test加入第五工具、第16正式扫描目标；扫描及离线复核强制双二进制SDK/Go、测试依赖与patch包覆盖，缺失或旧模块回流失败，不以运行二进制零高掩盖测试工具风险。
- 最终从原始归档/补丁重建后，全部上游Go测试加四类未选中资源保留测试通过（10个顶层测试，20.506秒，.runtime-ryuk-sdk2-build-final.log）；实际二进制SDK版本/符号与Docker29.9.3核对通过，binary SHA47c57509...45e0573。初轮构建缺patch、归档API编译失败及测试镜像两高记录保留，未跳过测试求绿。
- 最终Java25/Maven全量verify -Ptestcontainers：31单元+53真实PG17.12集成=84通过，0失败/错误/跳过，格式/编译/打包通过，2分38秒（.runtime-ryuk-sdk2-backend-final.log）。最终受测Node构建阶段格式/类型/14单元/构建通过（.runtime-ryuk-sdk2-frontend-final.log），原chunk/弃用提示保留；Windows原生PG未重跑。
- 复用原独立验收卷，base+migrate+TLS真实构建、迁移退出0、三服务Healthy；最终TLS桌面6项23.9秒/手机6项27.8秒通过，无API mock/JS异常/横向溢出（.runtime-ryuk-sdk2-browser-final.log）。无业务代码/Flyway变更，默认HTTP12及v2/旧v1恢复沿用前批同应用代码实证，不冒称本轮重跑。
- 用户.env.example/.gitignore保持且不入提交；未对个人库/旧角色授权，无推送/生产部署/付费模型/全局服务修改。GraphFlow仍未提供，定向检查，未context/plan/index。最终扫描、运行检查及清理见本批验收文档。
- **下一步**：复核剩余中低/未知风险、本地工具分支维护和生产前验收边界；域名/续期、旧库生产权限切换、持续告警/异地灾备未实测，不凭扫描通过宣称P6整体或生产就绪，不进入P7。

最终16目标扫描及离线复核均退出0，evidenceVerified=true、有效产物0高/严重、全部父层合计41次保留；summary SHA c06fa64fb7e1dade30269de0af59a1fc6138b8125e2ab3fc4b250b1a76d598d6（.runtime/security-ryuk-sdk-complete）。最终Python/OpenSSL34项、TLS/资源/monitor/8附件只读审计与延迟SSE通过；收尾仅本项目down并保留两卷。中低/未知风险及生产前边界仍未关闭，P6整体不自动通过。

P6第十一批（以下为历史记录）：最终验收矩阵与离线扫描证据复核已完成；当时Ryuk2高仍阻止安全门禁通过。

- 先定义P6_FINAL_REVIEW标准，按总纲P6核对分析/证据、JSON边界、备份恢复、身份分离、鉴权/附件、TLS/资源、监控日志、桌面手机旅程、供应链和文档一致性。矩阵区分本地通过、未通过、未验证生产项；修正P6_DESIGN旧Java21基线及运维/TLS/安全说明中独立迁移/备份角色的过期待办，不改写历史分批失败记录。
- 新增只读review_security_report.py：核对15目标/全部父层与有效产物、原始报告SHA、当前锁/配方/扫描器、时效/漏洞库、包/双Jackson/Go覆盖、镜像身份、Tomcat SBOM版本与npm实际包计数，重算高/严重。缺失/篡改/不一致/过期返回1，证据一致但仍有高/严重返回2，无高/严重才0；输出始终不据此宣布P6通过，不改原始发现或抑制风险。
- 新增9项复核测试（合成夹具只验证复核逻辑，不证明真实扫描干净），覆盖原始文件篡改、漏父层/移出门禁、伪零计数、错误镜像即使更新hash、旧/未来/未完成报告、过期漏洞库/旧配方、npm包发现与metadata矛盾，以及有效产物无高但父层仍有发现时也不宣称P6成立。最终Linux Python/OpenSSL**31项通过**，Python编译/CI YAML/Git diff通过（.runtime-final-evidence-checks.log）。
- 对最新真实.runtime/security-backup-role-final完成15目标复核，输出.runtime/p6-final-evidence-review.json：archived-report-consistency、evidenceVerified=true、仍2高/含父层43、securityGatePassed=false，明确review_exit=2。输入summary SHA efbc216308fcc412b7d72e9b78fee4bc1ce14f787419f3058df3c0000fa96da1。reviewedAt不修改scanFinishedAt；不把哈希一致性称外部签名、独立可利用性或第三方审计。
- 安全workflow扫描后always运行复核，报告缺失也失败，结果仍上传；已有扫描退出2不会被复核覆盖成成功。远端workflow未运行，Quality gates成功不代表安全门禁/P6通过。ADR-038记录复核范围与证据边界。
- 核对发现最新完整回归此前覆盖TLS12、默认HTTP整套仍为较早组合；本批复用原scarlet-nexus-p6-backup-role-acceptance隔离卷，base+migrate配置真实构建/启动V12三服务Healthy、迁移退出0，默认HTTP桌面6项23.6秒、手机6项28.4秒全通过，无API mock/JS异常/横向溢出（.runtime-final-review-http-browser.log），未调高限流。
- 本批没有修改应用代码/Flyway/依赖/镜像锁，不重复跑84后端/14前端或TLS12，明确沿用12977a1对应实证；备份v2及旧v1恢复同样沿用前批记录，不冒称本批重新执行。总矩阵保留JSON学习快照不可直接导回历史、P5 AI文字建议/图片识别等既有局限；监控为本地探测、日志为固定字段文本/requestId和轮转，不冒称持续告警或全链路JSON。
- 收尾Compose down，原隔离项目ps -a空、PG/附件两卷保留，个人库/旧角色/秘密/归档不改，用户.env.example/.gitignore不入提交。无推送/生产/付费调用/系统服务修改。GraphFlow工具仍未提供，定向检查，未context/plan/index；历史记忆仅定位当前项目，状态以仓库/实证为准。
- **下一步**：集中处置Ryuk两项高等级及其他风险，重扫后才可能关闭安全门禁；生产域名/证书续期、旧库权限切换、持续告警/备份留存与实际灾备仍待具体环境授权/验证。P6未通过前不进入P7或以无关新功能代替处置。

P6第十批（以下为历史记录）：备份专用只读身份、v2角色感知备份与受限恢复已验收；当时仍保留Ryuk2高、P6门禁未通过。

- 先定义P6_BACKUP_ROLE_ACCEPTANCE，沿用新空库provision约定增加scarlet_backup（无特权/成员关系，CONNECT/USAGE、迁移角色未来表/序列SELECT）；不改V1–V12或业务schema，不给旧卷/个人库授权。JDBC真实验证DML/DDL/改角色/读服务器文件/执行程序拒绝42501，未来表/序列SELECT可用而nextval拒绝；Web无备份或迁移密码。
- 分离角色项目backup强制`--database-password-file`，和加密口令文件分离，不回退管理员。用源PG同一镜像的短期客户端、只读根/无cap/私有0600 pgpass，通过非loopback地址TCP验证无口令不能登录，检查current_user、角色能力/成员关系/所有权和完整SELECT/无DML。预检先于停止Web，缺/错口令及过量/不足权限四种真实情况均拒绝、源进程StartedAt不变、无输出文件或密码披露（.runtime-backup-role-preflight.log / .runtime-backup-role-final-v2.log）。
- 新角色备份清单升v2，固定readonly/tcp/separate-v1语义；旧工具按格式拒绝，不能静默忽略角色元数据而管理员恢复。新工具保留旧v1 legacy-admin；信封SNBACK1/加密算法不变，角色密码不归档，清单区分备份与恢复bootstrap身份，ADR-037记录安全边界。
- 最终实际v2备份54表/2附件，scarlet_backup/tcp-password；认证/隔离恢复全表/文件匹配，角色以新随机密码重建，pg_restore以migrator拥有所有表、恢复原V12权限、独立迁移validate后Web用runtime且禁Flyway。只读身份复算摘要、runtime DDL/历史写入拒绝、实际pg_stat_activity连接身份、登录/学习快照/2私有图片均通过，backup_checks_exit=0（.runtime-backup-role-final-v2.log）。没有把管理员恢复当受限恢复。
- 新工具实际恢复旧Boot4 v1备份54表/8附件并认证读8图，明确legacy/管理员模式；取ae02964旧读取器验证拒绝新v2且仍能认证旧v1，没有创建恢复资源（.runtime-backup-role-reader-compat.log）。初期内部v1角色草案不交付，最终只认v2。
- 隔离Maven/Java25全量`verify -Ptestcontainers`：**31单元+53真实PG17.12集成=84项通过**，0失败/错误/跳过，格式/编译/打包通过，2分50秒（.runtime-backup-role-backend.log）。Windows原生PG未重跑。受测Node格式/类型/14单元/构建通过（.runtime-backup-role-frontend.log），原chunk/弃用提示保留，无新依赖。
- 新scarlet-nexus-p6-backup-role-acceptance两卷/三角色/V12启动后，TLS桌面6项26.2秒、手机6项39.8秒全旅程通过，无API mock/JS异常/横向溢出（.runtime-backup-role-browser.log）。最终串行TLS正反/主机名/明文/Secure Cookie/CSRF/退出/限流、资源/monitor/附件审计与SSE/头清洗通过；首轮与浏览器重建交叠造成末尾exec137/扫描找不到Web，失败保留，不作为完整结果。私有启动脚本CRLF改LF后成功（.runtime-backup-role-compose-r2.log）。
- Python/OpenSSL最终**22项通过**，新增缺口令不停止服务、trust拒绝/客户端清理、pgpass转义、v1防角色降级和v2必要身份字段检查；Python编译、CI YAML/静态锁/Git diff通过，远端CI未运行。最终v2代码后重跑实际备份/角色恢复、旧v1恢复与读取器兼容，不以早期测试覆盖后续修改。
- `.runtime/security-backup-role-final`完整扫描、退出2，仍Ryuk2高/0严重、含父层合计43次；后端2中、前端1中，npm生产0/全部5 moderate，作业与后端同镜像f1341870...。没有ignore/VEX或过滤未修项，中低/未知保留。最终记录见P6_BACKUP_ROLE_ACCEPTANCE/SUPPLY_CHAIN_REVIEW。
- 收尾仅本轮四服务down，ps -a空、临时客户端/恢复资源/Testcontainers清理，新PG/附件卷保留，旧卷/个人学习数据未改。用户.env.example/.gitignore不入提交，秘密/归档/样例只在忽略目录，无推送/生产部署/付费模型/全局服务或证书信任修改。GraphFlow仍未提供，定向检查，未context/plan/index；历史Java25记录仅用于定位，现状以仓库和本轮实测为准。
- **下一步**：汇总P6最终门禁与可用/未通过项，继续跟踪Ryuk2高及中低/未知风险；旧库权限切换、真实生产域名/证书续期/部署与独立安全审计仍需另行确认，当前不标P6完成。

P6第九批（以下为历史记录）：独立迁移作业已实现并验收；当时Ryuk2高与整体门禁仍未关闭。

- 先定义P6_RYUK_ACCEPTANCE与P6_MIGRATION_JOB_ACCEPTANCE。官方Ryuk稳定0.14.0/main仍分别依赖旧Docker模块28.3.3/28.5.2，旧模块公告没有列出兼容修复。试验官方拆分client0.6.3/api1.57.0，接口/返回类型及旧测试build/jsonmessage路径需要适配，依赖解析发生root/api模块包歧义；未编译/测试通过，不提交候选或替换现有清理工具，不生成ignore/VEX。证据.runtime-ryuk-sdk-compile.log与官方源码核对，2026-11-23继续跟踪，详见P6_RYUK_ACCEPTANCE。
- 转而完成已规划的独立迁移入口DatabaseMigration：同一Boot JAR直接Flyway migrate/validate，不启动Spring/Web/管理员或AI；显式scarlet_migrator与私有密码，拒绝URL查询/内嵌凭据，禁clean/baseline，失败退出1且不打印异常内容。无V1–V12改动，新角色只在新隔离库provision；不切换个人/既有库权限。
- 新docker-compose.migrate.yml仅用于新库：migrate退出0后才启动backend；后端禁Flyway、只持scarlet_runtime身份，环境移除迁移用户名/密码。迁移容器只读根、无附件卷/公开端口、有内存/PID限制，完成后不常驻。初轮分别build产生不同镜像ID，扫描正确拒绝；已改显式共用同一项目镜像、作业pull_policy=never，ADR-036记录选择。
- 新DatabaseRolesIT独立JVM故事验证实际Boot Launcher入口：错误口令返回1且空库不变，正确迁移/第二次幂等返回0、作业不创建用户；Web无Flyway bean及迁移密码，真实current_user/owner和DDL/提权/历史写入拒绝，登录/技能/学习/练习/证据/快照通过。首轮共享Windows target在IdentityCatalogIT重启时类文件瞬时缺失，31单元/53集成中3错误（.runtime-job-backend.log，9分06秒），原因未证实，未改业务断言或跳过测试。
- runner改只读源快照、容器内独立编译，导出JAR与报告后，最终`verify -Ptestcontainers`**31单元+53真实PG17.12集成=84项通过**，0失败/错误/跳过、格式/编译/打包通过，3分01秒（.runtime-job-backend-isolated.log）；Ryuk清理正常。没有把定向角色故事代替最终全量，也不声称Windows原生PG已重跑。
- 新scarlet-nexus-p6-job-acceptance两卷/专用角色Compose：实际作业退出0、表owner为migrator、Web为runtime且无迁移环境变量。错误迁移配置执行完整up返回1、backend不运行，finally恢复正确配置后健康，共用镜像改动后再次验证（.runtime-job-negative.log / .runtime-job-final-checks.log）。默认/旧角色overlay保持兼容，不自动套用新overlay到旧卷。
- 受测Node前端格式/类型/14单元/构建通过（.runtime-job-frontend.log）；新角色TLS桌面6项37.1秒、手机6项36.3秒全通过（.runtime-job-browser.log），无API mock/JS异常/横向溢出。TLS正反/主机名/明文拒绝/Secure Cookie/CSRF/退出/限流，资源/monitor与附件只读审计通过；延迟SSE首段0.077秒/终段2.578秒及头清洗通过。Linux Python/OpenSSL17项、Python编译、CI YAML/Git diff通过；CI新增overlay配置检查，远端未运行。
- `.runtime/security-job-final`完整，捕获退出2：门禁仍**Ryuk2高/0严重**，含所有父层合计43次；后端2中、前端1中，npm生产0/全部5 moderate。新增已完成作业与backend镜像ID一致/运行环境无迁移凭据检查，migrationImage=360459d907dc...；首轮不一致失败日志保留，不删除覆盖检查求绿。全部中低/未知保留，详见SUPPLY_CHAIN_REVIEW。
- 收尾仅对本轮新隔离项目Compose down，四服务移除、ps -a空、Testcontainers无残留，两新验收卷保留；旧卷/用户数据未改。原.env.example/.gitignore修改不入提交，SDK候选/口令/报告/样例只在忽略目录。无推送/生产部署/付费模型/全局服务变更；GraphFlow仍未提供，定向检查，未context/plan/index。
- **下一步**：设计并实证备份专用身份/权限和新角色库的配对恢复，再完成P6最终门禁；Ryuk2高继续跟踪上游兼容修复或单独评审工具分支。当前不把上一批默认身份备份当作新备份权限验收，不标P6完成。

P6第八批（以下为历史记录）：经用户确认完成Boot4框架迁移与本地回归；当时仍有Ryuk2高，安全门禁未通过。

- 用户在了解迁移理由后以“那继续吧”确认BOOT4_MIGRATION_PLAN，回滚起点9dcc816。Boot4.1.2/MyBatis4.0.1/springdoc3.2.0，专用Web MVC/Session JDBC/Flyway及测试模块；适配上下文/安全配置包名、显式TestRestTemplate自动配置。实际Framework7.0.10、Security7.1.2、Tomcat11.0.27；移除旧Tomcat覆盖，保留JDBC42.7.14，Java25不变。无V1–V12修改或MCP依赖。
- 采用官方弃用Jackson2过渡模块，原严格AI/Schema/目录解析保持，HTTP明确jackson2与UTC；安全覆盖属性改为jackson-2-bom.version=2.21.8，Jackson3 BOM保持3.1.7。新增真实转换器、UUID/日期/小数/401错误与requestId回归；两套Jackson都强制识别扫描。ADR-035记录选择与后续Jackson3迁移边界。
- patched Maven/Java25 `verify -Ptestcontainers`最终**31单元+52真实PG17.12集成=83项通过**，0失败/错误/跳过，7分10秒（.runtime-boot4-integration-r2.log），格式/编译/打包通过；含角色、OpenAPI、会话、严格解析、故障回滚与新增JSON合同，Ryuk启用。初轮包名/注解位置、UTF-8 BOM/导入排序失败已修正，日志保留，未跳过门禁。Windows原生PG未重跑。
- Node受测容器格式/类型/14单元/构建通过（.runtime-boot4-frontend.log），既有chunk与弃用提示保留。隔离Compose复用旧V12卷启动Boot4，三服务Healthy；真实TLS桌面6项30.6秒+手机6项32.4秒全通过，无API mock/JS异常/横向溢出（.runtime-boot4-browser.log）。未重复默认HTTP完整12项。
- TLS信任正反/主机名/明文拒绝/Secure Cookie/CSRF/退出/伪造头限流，资源/只读/monitor UP及6引用/6文件审计通过；延迟SSE首段0.041秒/终段2.541秒、代理头清洗通过（.runtime-boot4-runtime.log）。仅协议夹具，未调用真实模型，不声称旧浏览器会话跨Security代际保持。
- 新增`drill --backend-image sha256:...`，仅已有本地不可变ID，认证备份不改，只在新隔离卷/网络验证。旧Boot3备份在实际Boot4镜像上**54表/6附件匹配、认证读取/6图成功**，报告保留不同原/新ID（.runtime-boot4-upgrade-drill.log）；新Boot4配对备份/恢复**54表/8附件匹配、认证读取/8图成功**（.runtime-boot4-paired-restore.log）。不覆盖源卷，私有备份/口令不入Git。
- 最终Linux Python/OpenSSL17项通过，增加双Jackson覆盖、Tomcat11三包清单/缺失/版本不一致拒绝及升级镜像标签拒绝；Python编译、镜像消费者静态锁、CI YAML/Git diff检查通过（.runtime-boot4-static.log）。远端CI未运行。
- `.runtime/security-boot4-final`完整扫描、明确scanner_exit=2：**实际后端0高/严重、2中；前端1中；门禁剩Ryuk2高/0严重**。所有父层基线保留合计43次，不能当独立CVE；npm生产0、全依赖5 moderate。首次因Tomcat只接受10.1清单而扫描不完整，修正实际11.0版本/hash提取后重跑；没有忽略漏洞/未修项，原Spring2严重告警消失。zlib/OGNL中等级、工具中低/未知继续保留，台账见SUPPLY_CHAIN_REVIEW。
- 收尾Compose down，该项目ps -a空、Testcontainers无残留、两卷保留，恢复/SSE临时资源清理；用户.env.example/.gitignore原改动保持不入提交。无推送/生产部署/公网服务/付费调用/全局服务修改；GraphFlow仍未提供，定向检查，未context/plan/index。细节见P6_BOOT4_ACCEPTANCE。
- **下一步**：继续复核Ryuk2高的上游SDK修复/公告适用性，分类处理中低/未知，再完成独立迁移作业、备份权限与P6最终门禁；不重复请求已获授权的Boot4迁移确认，不标P6完成。

P6第七批（以下为历史记录）：构建、测试、数据库启动工具重建修补已验证；当时实际产物4次高/严重，Boot4仅为待确认提案。

- 先定义P6_TOOLCHAIN_REMEDIATION验收标准。六个官方父镜像digest与gosu1.20/Ryuk0.14.0源码、Guava33.7.2-jre/npm11.21.2资产摘要固定；Go1.27.4重建启动/清理二进制、保留符号与构建信息。Maven精确升级apt/OpenSSL/brotli及Guava，Node仅镜像内修补npm；实际PG17.12数据库引擎不变，无Flyway或业务代码变更。
- 增加四个本地工具目标、recipe指纹/平台/用途核对、缺失失败且无旧版回退；构建、Testcontainers、Compose、SSE、安全workflow统一消费。Compose数据库pull_policy=never，根.dockerignore排除环境文件、缓存/附件；README补充Python/预构建要求。默认本地原生PG与宿主工具不改；CI前端/浏览器宿主环境不冒称已修补。ADR-034记录有效产物门禁语义，远端CI未运行。
- `security_images.py --build`四目标成功（.runtime-p6-tools-build-r2.log）；首轮Go下载因网络失败，显式本轮host构建网络/代理后成功，默认仍default。`verify_image_lock.py --remote`六索引/消费者/精确包与资产检查通过（.runtime-p6-tools-lock-remote.log）；首次漏传代理的registry超时已保留，远程请求新增60秒上限，不安装服务或改全局网络。
- 修补Maven/Java25实际`verify -Ptestcontainers`全量**31单元+51真实PG17.12集成=82项通过**，0失败/错误/跳过，4分48秒（.runtime-p6-tools-backend.log）。格式/编译/打包通过，Ryuk正常清理；本轮未重跑Windows原生PG。修补Node临时容器源目录只读，从lockfile安装后格式/类型/14单元/生产构建通过（.runtime-p6-tools-frontend.log），既有图表chunk及弃用提示保留。
- 复用scarlet-nexus-p6-security-acceptance独立验收两卷重建启动，V12/PG17.12/JRE25.0.5+8；真实TLS桌面6项29.5秒、手机6项32.8秒全旅程通过（.runtime-p6-tools-browser.log），无API mock/JS异常/横向溢出。未重复默认HTTP12项；用户学习卷未触碰。
- Linux TLS信任正反/错误主机名/明文拒绝/Secure Cookie/CSRF/退出/伪造头限流通过，资源限制、monitor UP、4引用/4附件无缺失损坏孤立；延迟SSE首段0.038秒、终段2.538秒，代理头清洗通过（.runtime-p6-tools-runtime-r2.log），未调用真实模型。Windows探测未返回后只终止本轮已核对进程，未宣称Windows探测通过。
- 新配对加密备份/隔离恢复**54表、6附件逐项匹配**，应用就绪、认证读取及6图成功；恢复资源自动清理，源卷不覆盖（.runtime-p6-tools-restore.log）。Linux Python/OpenSSL13项通过，含有效目标不可移出门禁、旧配方/平台/用途/缺失镜像拒绝等验证；Python编译、CI YAML与Git diff检查通过。
- `.runtime/security-tools-final`全量v2扫描完整：Maven/Node/PG实际修补镜像零高/严重，Go二进制必须识别stdlib1.27.4；实际门禁**4次发现（Spring2严重、Ryuk2高）**，包含所有父层基线的报告合计45次。旧95是旧集合总数，不能直接与4对比；npm生产0、全依赖5 moderate，中低/未知保留。扫描没有ignore/VEX/未修项过滤；当前门禁未通过，详见SUPPLY_CHAIN_REVIEW。
- 收尾对相同实际镜像完整复核为`.runtime/security-tools-confirmed`，13项脚本测试/编译/YAML通过，扫描complete=true且明确捕获退出2、仍4次门禁发现（.runtime-p6-tools-final-checks.log）。没有用PowerShell包装层的状态替代扫描器真实退出码。
- Ryuk二进制符号证据确认含Docker client、不含daemon实现；Engine29.9.3高于公告修复版29.5.1。这是限定使用条件分析，不抑制两项高告警（verify_ryuk_scope.py，证据见runtime日志）。Spring Boot4.1.2/MyBatis4.0.1/springdoc3.2.0提案已写BOOT4_MIGRATION_PLAN，按AGENTS重大框架变更先确认，当前仍Boot3.5.16。
- 收尾仅停止本轮独立Compose与临时测试容器，保留数据库/附件卷和私有证据；用户.env.example/.gitignore改动不入提交。无推送/生产部署/付费调用/系统服务安装，GraphFlow仍未提供，定向检查，未context/plan/index。
- **下一步**：取得框架迁移确认后，在独立提交实施Boot4并重跑完整门禁；同时复核Ryuk上游SDK/公告归属，中低/未知继续分类。独立迁移作业/备份权限和生产最终门禁仍待推进，不因工具修补提前结束P6。

P6第六批（以下为历史记录）：兼容安全补丁、镜像固定与可复现扫描已实施；当时功能回归通过，漏洞门禁未通过。中断期间用户追加的Java25升级已保留并补充真实容器验证。

- 先定义P6_SUPPLY_CHAIN_ACCEPTANCE；按ADR-032保留Java25，在Boot3.5.16内显式覆盖Jackson2.21.8/Tomcat10.1.62/JDBC42.7.14。官方Nginx1.30.6/PG17.12、前端libxslt1.1.43-r4，六份构建/运行/测试镜像固定官方多架构索引并逐项registry核对，消费者/包约束检查通过；只实测linux/amd64，无已执行Flyway修改，未升级原验收/学习卷。
- Trivy0.76.2归档/checksum/二进制SHA-256校验；新增校验安装、镜像锁检查和全量扫描工具、手动安全workflow。实际JAR识别Jackson/JDBC，Java DB未识别新Tomcat的首轮覆盖失败后，从最终JAR提取三包版本/hash补充CycloneDX扫描0发现。显式空ignore、保留未修项/所有严重度，拒绝缺覆盖、过期/未来数据库，4项失败关闭单测加入后Linux Python/OpenSSL共9项通过。
- 最终.runtime/security-complete与当前锁摘要一致：完整扫描，漏洞库/Java DB当日01:11Z/00:57Z更新，95次高/严重“目标内包/CVE”发现、退出2；不是95个独立CVE，也不将基础镜像与最终镜像相加当生产暴露。最终后端2严重/4中，最终前端1中；npm生产0、全部5 moderate。Spring条件、gosu/Go、Maven、Node内置npm、Ryuk与未知项未关闭，具体台账/期限/官方来源见SUPPLY_CHAIN_REVIEW，不忽略求绿或称全栈安全。
- Node24格式/类型/14单元/构建通过（.runtime-p6-risk-frontend.log）；Linux Java25/Testcontainers首次tag@digest兼容初始化失败，集中官方兼容声明后31单元通过，51真实PG17.12集成仅SystemIT旧17.11断言失败（50通过/1失败/0错误/0跳过，7分20秒，.runtime-p6-risk-testcontainers-r2.log）。修正为容器17.12/原生17.11各自严格断言后，最终31单元+SystemIT6集成通过、0失败/错误/跳过、3分03秒（.runtime-p6-risk-system-final.log）；其它45集成沿用同生产代码前轮通过结果，不声称最终又全量跑82项。本轮不重跑Windows原生PG；前一沙箱失败记录保留。
- 新建scarlet-nexus-p6-security-acceptance隔离项目/两卷，实际JRE25.0.5+8、PG17.12、V12，构建/测试Maven镜像JDK25.0.4.1，版本边界如实区分。最终TLS桌面6项36.2秒+手机6项40.3秒全旅程通过，无API mock/JS异常/横向溢出；默认HTTP桌面/手机状态2项5.6秒通过，未重复HTTP12项（.runtime-p6-risk-browser-final.log / .runtime-p6-risk-http-browser.log）。
- 证书/主机名正反、明文拒绝、安全Cookie/CSRF/退出/网关限流通过；资源/只读/monitor UP、4附件引用/4文件无异常。Java25夹具源文件包路径错误已修正，最终延迟SSE首段0.048秒/终段2.548秒且清洗转发头（.runtime-p6-risk-runtime-final.log），仅协议，不是真实模型质量。Python编译、CI YAML及Git diff检查通过，远端CI未运行。
- 收尾隔离Compose down，ps -a空、两卷保留，临时SSE/Testcontainers容器清理。原.env.example/.gitignore与Java25专项改动、stash备份保留；无全局环境修改/生产部署/公网服务/付费调用/推送。GraphFlow工具仍未提供，定向检查，未context/plan/index。截图/报告/口令/附件样例不入Git。
- **下一步优先处置未关闭高/严重与框架生命周期风险**，再完成独立迁移作业/备份权限和最终门禁；实际生产域名/证书续期、现有库权限切换、付费支持或框架大版本迁移须独立评审/确认，不以当前补丁和条件分析提前结束P6。

Java运行时专项（2026-11-16，以下为当时历史记录）：已按用户明确要求将Java21升级为最新LTS Java25；当时完整集成验收受沙箱限制，后续真实容器结果见上文，P6整体状态仍未完成。

- 项目内Temurin25.0.5+8已下载并校验官方SHA256；Maven3.9.16保留。`backend/pom.xml`编译目标、CI、Docker构建/JRE及Testcontainers/SSE夹具镜像统一25；镜像继续固定官方registry核对的digest，同步既有`security/images.lock.json`。Boot3.5.16官方支持Java25，保留现有框架及Jackson/Tomcat/JDBC安全覆盖，不改业务逻辑或Flyway迁移。ADR-032明确替代旧Java21约束，历史验收记录不重写。
- Java21基线：先修正原有四处PG镜像长行格式；`clean compile test-compile`与`clean test`通过，31单元、0失败/错误/跳过。`verify`真实PG启动前失败，报告23初始化错误（10个集成类），`pg_ctl`无法创建受限令牌error87/启动子进程error3（`.runtime-java25-baseline-verify.log`）；不能将其视为业务断言通过。
- Java25：`clean test-compile`、`clean test`通过，31单元全通过；`javap`确认major69，Compiler3.14.1、Surefire/Failsafe3.5.6可用。最终`mvn -B clean verify -Djacoco.skip=false`完成编译/打包/31单元，但真实PG仍同样23初始化错误、0跳过，退出1（3分32秒，`.runtime-java25-final-verify.log`）；未新增绕过/跳过测试或安全控制的参数。项目未配置JaCoCo，命令没有生成覆盖率数据，不编造百分比。
- Node24前端格式/类型/14单元/生产构建全部通过；既有ECharts体积提示保留。16项解析后的直接Java依赖CVE工具扫描未发现已知CVE，仅代表本次直接依赖范围。原有安全版本覆盖、全部镜像锁消费者、PowerShell语法检查通过；预览/验证脚本先解析相对JavaHome，避免切换工作目录后失效。
- 当前策略拒绝WSL（E_ACCESSDENIED）且不允许提权，故无法完成Java25 Linux Testcontainers、Docker实构建/运行、真实网关/Playwright验收；Windows真实PG也受上述令牌限制。原生PG与WSL两种路径均有失败证据，不能通过H2/假响应/跳过测试冒充验收。编辑器另提示Boot3.5.x已于2026-06-30结束OSS支持，该生命周期风险保留，不扩展为未经请求的框架大版本迁移。
- 工作分支`appmod/java-upgrade-20261116030222`。分支工具自动stash后，全部原有工作文件已从事先私有快照恢复；原stash保留备份。`.env.example`及其他无关供应链文件逐项hash确认未被本轮改变；四个PG测试源仅有格式变化。提交工具仅支持提交全部工作区，为免混入原有用户修改，本轮未创建混合提交，未推送。GraphFlow仍不可用；不声称context/plan/index。自动审批拒绝临时安装包/旧工具副本清理，文件保留在忽略目录，不影响Java25.0.5运行。
- 计划、逐步结果与剩余门禁见[升级报告](../.github/modernize/java-upgrade/20261116030222/summary.md)。下一步在允许子数据库进程及WSL/Docker的环境中重跑Java25真实PG/容器/浏览器门禁后才能标为完整升级验收；不以原Java21历史成功代替Java25运行证据。不生产部署、公开服务、付费调用或改系统JAVA_HOME/PATH。

P6第五批：独立HTTPS、安全Cookie与资源约束已完成本批本地验收；整体P6尚未完成。

- 先定义P6_TLS_ACCEPTANCE。本批新增显式docker-compose.tls.yml：Compose !override仅保留loopback HTTPS，证书链/私钥路径必填、只读挂载，TLS1.2/1.3、HSTS/CSP、Secure Cookie；关闭应用代理解析，网关清洗转发头、按直连IP限登录。后端共享来源10次/5分钟桶保留，不冒称多用户/多级代理支持。三服务CPU/内存/PID/no-new-privileges，后端cap_drop/只读根/64MiB受限tmpfs；解释与生产边界见TLS_DEPLOYMENT、ADR-031。
- Windows Java21 Verify.ps1最终格式/编译/31单元+51真实PG17.11集成=82通过，0失败/错误/跳过，3分05秒（.runtime-p6-tls-verify.log）；Node24格式/类型/14单元/生产构建通过，既有图表体积提示保留。本批只新增网关测试源，无数据库迁移/业务Java改动，未重复Linux Testcontainers全套；最终测试源另经spotless/test-compile通过。
- 隔离Compose实际TLS构建与V12就绪；Python证书/主机名正反验证、明文拒绝、安全Cookie、真实登录/CSRF/退出、伪造头限流通过（.runtime-p6-tls-security-final.log）。Docker inspect确认资源/端口/只读配置，monitor UP，最终16附件引用/16文件无异常（.runtime-p6-tls-runtime-final.log）。独立内部SSE测试网络无发布端口，首段0.079秒/终段2.580秒、头清洗通过（.runtime-p6-tls-stream-final.log），不代表真实模型质量。
- TLS真实Nginx完整桌面6项43.9秒+手机6项44.0秒通过，无API mock/JS异常/横向溢出，练习/附件上传下载/未登录拒绝/学习恢复/计划复习/数据管理覆盖；已查看桌面/手机练习证据完整截图（.runtime-p6-tls-browser.log）。恢复默认HTTP配置后系统桌面/手机2项5.4秒及非Secure开发Cookie通过，未重复默认12项（.runtime-p6-tls-http-browser.log）。
- Linux Python3/OpenSSL 5项通过（.runtime-p6-tls-python.log）；Windows首次执行既有备份用例因缺openssl有2项环境错误，转支持环境复跑，未跳过冒充成功。Python编译、PowerShell语法、CI YAML解析通过；CI增加HTTP/TLS×两浏览器矩阵与TLS/资源/运维检查，远端未运行。
- 收尾Compose down，命名项目ps -a为空，两个数据卷保留；临时SSE容器/网络清理。GraphFlow仍未提供，按规则定向检查，未context/plan/index；原.env.example改动保持且不纳入提交。无生产部署/公网服务/系统服务安装/证书信任安装/付费调用/推送，私有证书和样例仅在忽略目录。
- **下一步P6**：依赖与容器镜像风险复核/digest固定及处置记录、独立迁移作业编排/备份权限与生产运行说明，整合最终门禁。真实域名/受信任证书续期、现有库权限切换、公网部署/外部告警等实际生产操作另行确认；当前本机TLS与角色方案不等于生产安全审计。

P6第四批：独立迁移与运行角色本批范围已验收，整体P6尚未完成。

- 新库初始化脚本只接受空schema、拒绝已有同名角色；运行角色不拥有对象，无DDL/角色创建/切换迁移角色能力。V12在专用角色存在时限制业务历史DML及Flyway历史权限，V1–V11不改。
- Spring可选Flyway独立连接，新增显式roles Compose overlay，默认本地方式兼容。Windows专用角色故事与31单元通过（.runtime-p6-roles-focused.log）；最终Linux Testcontainers为31单元+51真实PG集成=82项通过，0失败/错误/跳过（.runtime-p6-roles-testcontainers.log，6分56秒）。验证真实current_user/表owner、DDL/提权/历史写入拒绝、登录/学习/练习/证据业务。初轮Embedded PG URL内置user覆盖角色导致失败，去除URL凭据后重新实证，未把管理员连接误算受限角色。不对现有库执行角色初始化。进程同时持有迁移凭据的边界与生产独立作业建议见DATABASE_ROLES。
- 前端格式/类型/14单元/构建通过；现有独立Compose升级V12、三服务Healthy，默认方式桌面6项38秒+手机6项43.2秒全部通过（.runtime-p6-roles-browser.log）。角色overlay拒绝缺少密码配置、显式验证值可解析（.runtime-p6-role-overlay.log），未用该overlay对现有库启动。收尾Compose down保留两个数据卷，新增角色仅存在于已自动关闭的隔离PG测试实例。
- GraphFlow仍未提供，定向检查；原.env.example修改保留，不生产部署/推送/购买服务。

P6第三批推进监控、日志与只读附件审计，整体仍未验收。详见OPERATIONS_CHECKS。

- 新增operations.py：Docker状态与内部liveness/readiness/网关HTTP探测，健康0、异常2；只读扫描附件并分类缺失/损坏/孤立文件，私有清单独占保存，无删除动作，明确非一致性快照。
- Compose三服务json-file日志10m/3轮转；网关访问日志省略URI/查询/凭据并关联请求编号；PG减少错误详情和SQL语句输出。实际inspect/SHOW及查询标记未入日志的检查通过。真实10引用/10文件无异常，停止验收后端时监控正确返回2，随后恢复。
- 5项Python测试通过；Java31单元+50真实PG集成=81项通过，0失败/跳过（.runtime-p6-ops-backend.log）；前端格式/类型/14单元/构建通过。真实Nginx桌面6项25.5秒+手机6项31.3秒全部通过（.runtime-p6-ops-browser.log）。实际inspect/SHOW与查询标记检查见.runtime-p6-logging-check.log，正常和后端停止故障检查见.runtime-p6-operations.log，新增gateway探测实际UP。
- 收尾停止隔离Compose、保留数据库与附件卷，不安装服务、删除附件或推送。生产TLS、最小数据库角色、资源与全栈风险复核仍未完成；不把生产说明当作已部署/已安全验收。下一步优先准备最小数据库运行角色与独立迁移角色的可验收方案。
- GraphFlow工具未提供，使用定向检查，未context/plan/index；原.env.example改动保留，无付费调用、推送或生产部署。

P6第二批：加密备份与隔离恢复已验收，详见[BACKUP_RESTORE](BACKUP_RESTORE.md)，整体P6仍未完成。

- 本轮新增scripts/backup_restore.py：命名项目停写后配对pg_dump/附件、全表与文件清单、数据库附件引用校验、加密和密文认证；恢复只生成新目标，不覆盖源库/卷，无模型调用或新迁移。
- 最终Docker演练54张public表、10个附件全部匹配；新实例Flyway/readiness、原账户登录、学习快照、10张鉴权图片读取和checksum通过（.runtime-p6-backup-verified.log）。首轮内部网络无端口映射导致探测失败，改为Linux宿主直接访问内部桥地址后通过。
- 3项Python/OpenSSL格式安全测试通过；Windows Java21 mvn verify为31单元+50真实PG集成=81项通过，0失败/错误/跳过（.runtime-p6-backup-backend.log）。前端格式/类型/14单元/构建通过，既有图表体积告警保留。CI新增恢复演练，远端未运行。
- 密文/口令/样例仅在忽略目录，原.env.example修改保留。GraphFlow仍未提供，未context/plan/index。下一步监控、脱敏日志轮转、孤立附件只读审计与生产TLS/权限/资源/依赖风险复核，P6不提前标为完成。

P0–P5已按各阶段交付范围验收；P6开发中，尚未整体验收。P5每日规则计划/确认/调整/复习闭环通过，AI文字建议未接入。P4的一次真实导师样例不代表全面教学质量或严格单问保证。

## 本轮：P6第一批分析与JSON迁移（2026-11-16，进行中）

- 已先定义P6_DESIGN完整分步门禁，本批实现成长分析、本人学习快照导出、知识目录预览/确认新增导入；完整数据库/附件恢复、监控与生产安全仍待后续，不把目录导入冒称全量恢复。
- 分析采用明确口径：已结束时段按结束日归集，任务按创建范围/当前状态、取消不进分母；每作答只取范围结束前最近RULE，独立明确对错才进独立正确率分母；自评与规则能力分别列最近结果，提供证据与原练习追踪。时区/日期范围/技能归属校验、REPEATABLE_READ一致读取。
- 新增V11目录导入预览表，源JSON/hash及确认结果受不可变触发器保护；确认新建UUID保留跨技能共享节点，不覆盖旧记录；事务、幂等、归属、严格字段/引用/环验证。V1–V10不修改。学习快照只读导出40个白名单业务表，附件仅元数据，不含身份凭据、文件路径或二进制。
- `/analytics`、`/data`与导航已接真实API；ECharts6.1.0精确锁定，用于真实时间趋势/技能投入，SVG与richText tooltip、不执行用户HTML，按需加载。最终Node24 format:check/typecheck/14单元/构建通过（`.runtime-p6-frontend-r2.log`），主JS290.11kB/CSS79.46kB，图表懒加载546.30kB/186.09kB gzip，保留体积提示；生产依赖audit0条发现（`.runtime-p6-audit.json`），开发链原5项moderate仍在。
- 第一轮真实PG暴露测试自评说明不足既有最小长度，以及Jackson默认将数字转字符串；前者修正验收输入，后者增加Textual显式拒绝数值/布尔与禁止浮点转整数。最终Windows Java21与Linux Java21/Testcontainers均31单元+50真实PG17.11集成=81，0失败/错误/跳过；日志`.runtime-p6-backend-final.log`（2分42秒）/`.runtime-p6-testcontainers.log`（4分41秒）。
- GraphFlow工具仍未提供，按规则定向检查，不声称context/plan/index；仓库原有.env.example改动保留，无真实模型调用、远端推送、生产部署或不可逆操作。
- Compose实构建升级既有独立验收卷至V11，三个服务Healthy；P6专门桌面/手机2项通过（`.runtime-p6-analytics-data-e2e-r2.log`）。ECharts自动ARIA覆盖固定图表名称的问题通过设置真实ARIA说明修正。已查看桌面/手机分析和导入确认4张完整截图。
- 默认全旅程新增后共12项、含12次UI登录（学习中断恢复有二次登录），首轮11通过/手机学习登录429，准确触发默认10次/5分钟限流。新增Verify-Browser.ps1按桌面/手机项目重建隔离backend/web并保留卷，CI改各自独立matrix实例，不修改生产限流。最终desktop6项29.9秒+mobile6项34.3秒全部通过，无API mock/JS异常/全页横向溢出（`.runtime-p6-browser-final.log`）；CI YAML解析通过，远端未运行。
- 第一批验收见P6_SLICE1_ACCEPTANCE，整体P6仍未完成。下一步数据库与附件完整备份、完整性/加密与隔离恢复/新实例读取演练，再完善监控日志/生产安全说明；目录导入和学习JSON快照不替代完整恢复。
- 收尾已执行本轮隔离Compose down，ps -a为空，postgres-data/attachments-data两卷确认保留；临时PG由测试运行时关闭，无额外预览服务、系统服务安装、生产部署或远端推送。验收截图/日志/下载JSON及样例不入Git。

## 本轮：P5预算计划与复习（2026-11-16）

- 先编写P5_DESIGN验收标准。V10增加复习安排/周期任务/不可变调度事件、规则草案、每日确认计划版本/当前指针、预算和任务调整历史；V1–V9未修改。所有写入按当前用户锁串行化，审计与业务同事务。
- 规则按到期复习、继续任务、目标优先级/截止日期、近期结果及先修自报完成排序，跨技能轮选整项；草案不创建任务、不覆盖当前计划。确认重新检查源指纹、revision、每日与技能预算，明确确认并用幂等键保存版本；故障注入回滚快照与周期任务。
- 延期/取消/保总分钟拆分保留原任务及笔记；计时未结束时拒绝调整。复习周期任务仅在计划确认时创建并跨日复用；复测需新作答和新确认的同节点证据，规则独立正确翻倍，其余保守1天，原因/日期/间隔追加历史。
- `/planner`、`/reviews`接入真实API，首页显示今日计划与到期数量，工作台提供任务调整/复习入口。规则来源显式标注，无新依赖、模型调用或生产部署。AI文字建议未接入，不冒充AI能力。
- 最终Windows Java21 `mvn -B verify`与Linux Java21/Maven `verify -Ptestcontainers`均为31单元+43真实PG17.11集成=74项，0失败/错误/跳过，格式/编译/打包通过；日志`.runtime-p5-backend-final.log`（1分55秒）、`.runtime-p5-testcontainers.log`（4分14秒）。新增3规则单元和8集成故事覆盖预算、先修、并发、幂等、所有权、不可变历史、故障回滚、计时/暂停、新证据与周期任务复用。
- Node24最终format:check/typecheck/13单元/生产构建均通过（`.runtime-p5-frontend-final.log`），主JS275.45kB/CSS78.38kB（未gzip）；无新依赖。前端新增明确确认/409保留草案、跨时区午夜日期测试。CI V10断言更新，YAML解析通过，远端CI未运行。
- Compose真实构建并复用既有验收卷升级V10，PG/backend/web均Healthy；真实Nginx默认桌面/手机10项通过，55.6秒（`.runtime-p5-e2e-final.log`）。P5专门旅程2项通过（`.runtime-p5-planning-e2e-r2.log`）：三技能各25分钟/总75、新草案不覆盖、延期/拆分、复测1→2天。无API mock/JS异常/横向溢出，已查看桌面/手机计划及复习历史4张完整截图。
- 首轮真实PG发现Java和PG UUID排序不同导致多技能指纹误冲突，已统一排序；回滚故障按既有合同返回503，测试修正预期后通过。首轮浏览器嵌套label精确定位超时，改用combobox角色后完整通过。预算列表限高滚动并默认由用户选择范围，不自动规划全部历史技能。
- GraphFlow工具未提供，使用定向本地检查，未执行context/plan/index。用户`.env.example`修改保持原样、不纳入提交；P2/P4一次调用授权已耗用，本轮不再调用。
- 验收细节见P5_ACCEPTANCE；规则启发式未个体校准，源指纹保守覆盖相关全用户上下文，预计预算不截断实际计时。总纲3.4的AI文字建议、每周/长期高级编排仍未实现。下一步P6：分析指标、JSON导入导出、隔离备份恢复、监控与生产说明；生产部署另行确认，不自动启动。
- 收尾已对本轮独立验收项目执行Compose down，ps -a确认无容器，postgres-data/attachments-data两卷保留；Windows临时PG由测试运行时关闭。没有启动额外开发服务、安装系统服务、推送或生产部署，日志/截图/样例仅保存在Git忽略目录。

## 本轮：P4真实导师验收（2026-11-15）

- 用户明确授权deepseek-flash一次、最多1 CNY；重新核对官方人民币价格，采用高峰缓存未命中输入2/输出8元每百万tokens保守计算。请求最多20000字节、8192输出，估计上限0.113728 CNY，预留0.2、每日限额1；无自动重试/追加修复。
- 在独立临时PG/Spring、本机随机端口59331，通过真实登录/CSRF/题目与作答接口保存独立思路，再发一次同题SOCRATIC导师请求。HTTP200、SUCCEEDED，收到SSE delta，供应商报告输入187/输出83 tokens；配置费率记账0.00103800 CNY，非供应商最终账单。作答保持原样，自动证据数量0。
- 样例使用平方非负证明题。模型引导检查展开式相等与平方符号，未写完整证明；但附带第二个思考点，不能据此承诺严格只问一问。受限一步提示仍走作者准备内容，开放式AI建议继续需要审核。6个实际数学片段用现有KaTeX安全选项离线渲染通过，无新增模型请求。
- 本轮运行命令为私有验收脚本编译及单次授权入口；真实请求1、自动重试0，单次哨兵已写入，禁止无授权重跑。证据`.runtime/real-tutor-evidence.json`，SHA-256 `825CB9E0C6F2DE55482E5C9667AAF29C7ADB4E7843E71C89AE39D1CC9E61593B`；原文/ID/Key只在忽略目录，不提交个人示例数据。首轮离线数学检查的PowerShell参数引号不兼容，改为本地mjs文件执行后通过，不涉及模型重发。
- 临时应用/PG已关闭，59331无监听，运行时删除临时引导密码和附件目录，未修改已有Compose卷、真实学习数据或默认模型开关。无生产部署/推送。GraphFlow工具仍未提供，未执行context/plan/index。本轮只更新验收/进度文档，不改业务代码，沿用前轮63项Windows后端、11项前端、8项Nginx及2项导师协议证据，不冒称重新运行这些套件。
- P4门禁按总纲的独立提交、分级帮助、可复核、证据关联和失败保留记录验收；图片分析仍明确降级、不支持自动读图。下一阶段P5：规则约束预算的每日计划草案、确认、延期及可解释复习；本次单次授权已用完，未来付费调用仍需新授权。

## 本轮：P4练习、证据与SSE导师（2026-11-15）

- 先编写P4_DESIGN；V9新增11表与任务同节点FK，题目/曝光、不可变作答/复核/证据/评估、附件、对话及导师运行。V1–V8保持原样。参考答案不进入独立练习读取，先提交后提示；提示曝光影响以后提交的独立性标签。
- 证明/作品/纯图片规则返回UNSURE；人工复核追加，显式确认才形成证据，SELF_REVIEW/REVIEWED_AI均为自报。RULE仅基于不同题目规则比对，最高L2；SELF为LOW，历史与依据可查，不改任务/路线进度。
- 新增私有PNG/JPEG附件：5MiB/8000边长/1600万像素限制、magic/MIME/解码、PNG重编码、UUID键/SHA-256/鉴权下载/归档、默认每用户256MiB配额。Compose新增attachments-data卷，容器用户目录权限已处理。后端和网关重建后，认证会话与图片校验和保留，证据`.runtime/p4-attachment-persistence.json`。
- 新增KaTeX精确锁定0.19.1及锁文件，用于数学片段渲染；原文/解析错误纯文本，trust=false及扩展/尺寸上限。按需加载MathText，主JS252.75kB、数学chunk260.16kB、CSS76.96kB。格式、11前端单元、类型/构建通过，日志`.runtime-p4-final-front.log`；生产依赖audit为0，原有5项开发依赖中等级告警未用force升级掩盖。
- Java适配器新增真实Chat SSE传输，整体deadline与体积上限、UTF-8分片、stop/[DONE]终止校验，无重试。对话/提问先落库，再在事务外流式调用；共享P2日token/货币账本、费用快照、幂等snapshot、过期任务保守恢复。图片分析明确不支持，不自动发送附件；严格一步提示来自作者准备内容。接口/配置见AI_TUTOR。
- Windows最终全量28单元+35真实PG集成=63项通过（`.runtime-p4-release-native.log`，1分56秒）；Linux Java21/Maven/Testcontainers此前62项通过（`.runtime-p4-testcontainers.log`，3分45秒），该轮早于新增413用例。覆盖预算不足零调用、同键零重复计费、截断/超时/中断保留内容、终态保护、跨用户/FK及不可变历史；新增超限multipart 413/no-row也在最终Windows及真实Linux容器浏览器通过。
- 默认无AI临时PG/Vite桌面手机8项通过；导师启用独立HTTP协议桌面手机2项通过，刷新未追加调用。真实Nginx最终默认8项通过（`.runtime-p4-release-e2e-final.log`，31秒），截图已查看。上传图片通过非root容器写入私有卷，数学字体与CSP下显示正常；上述启用协议是测试数据，不代表真实模型质量。
- 复核修复：Vue多语句模板移入函数、题目切换取消时恢复原选项、资料选择清理归档引用、明确超限413。首次Docker构建读到尚未格式化测试文件而被门禁拒绝，格式化后重建；曾在容器未就绪时提前启动浏览器造成连接失败，等三服务Healthy后完整通过，未将环境失败冒充功能通过。
- 核心开发提交29b1228时，GraphFlow工具仍未提供，无法context/plan/index；无远端推送/远端CI/生产部署/新真实模型调用。`.env.example`用户改动保留不纳入提交。当时真实导师验收脚本仅编译未执行；后续授权执行结果见上方真实验收记录。
- 收尾停止临时PG/学习运行时/协议服务并删除临时附件/密码/metadata；停止Vite与验收Compose，保留postgres-data及attachments-data卷。截图、日志、验收脚本在忽略目录；真实Key未读入本轮应用、未调用真实服务。P4验收细节见P4_ACCEPTANCE。

## 本轮：P3学习闭环（2026-11-15）

- 先编写P3_DESIGN验收标准。V8新增12张核心/关联表，复合所有权FK、路线不可变版本、用户唯一ACTIVE选择、唯一未结束时段；V1–V7未修改。目标/任务/资料归档或状态保留，笔记/进度revision防覆盖，写入与审计同事务。
- 路线通过阶段和节点表单保存新版本；旧版、选择、标题快照与任务引用保留，不自动迁移历史。状态与目标等级不冒充能力评估。工作台显示节点、先修、原路线位置，计时/任务完成/已学分别确认保存。
- `/learn`学习首页、`/paths`目标路线、`/study/:id`工作台接入真实API；笔记纯文本与未保存提醒，资料http(s)链接及归档，列表分页，首页独立查询继续任务。无需AI，无新依赖或付费调用。
- 最终Linux Java21 `mvn -B verify -Ptestcontainers`：25单元+23真实PG集成=48项，0失败/0跳过、BUILD SUCCESS（`.runtime-p3-testcontainers.log`，3分44秒）。Windows最终定向`mvn -B spotless:apply verify -Dit.test=LearningIT`：25单元+7项P3 PG集成通过（`.runtime-p3-final-learning.log`）；Windows此前全量46项通过，不将分轮重复计数相加。
- Node24格式、9单元、类型/构建通过，JS227.22kB/CSS46.86kB。临时PG+Vite默认桌面/手机6项通过（`.runtime-p3-e2e.log`）；持久Compose实构建，原验收卷从V7升V8、三个服务Healthy，真实Nginx默认6项通过（`.runtime-p3-nginx-e2e.log`）。截图已查看。
- 新增测试验证退出恢复、历史不丢、两请求并发只开始一个时段、相同命令不重复计时、旧revision409、非http(s)/相对/带凭据链接422、跨用户404/复合FK、审计故障503整笔回滚。首轮状态版本断言和错误码预期已按实际合同修正；原有生产错误处理未放宽。
- CI迁移断言更新到V8，未推送、未执行远端CI。GraphFlow工具仍未提供，未执行context/plan/index。无系统服务安装/公开暴露/生产部署，`.env.example`用户修改保留，不纳入本轮提交。详细证据见P3_ACCEPTANCE.md。
- 收尾：临时学习运行时及PG已关闭，临时引导密码/元数据删除；验收Compose执行down、保留postgres-data卷，Vite预览停止。不保留外部监听服务，私有截图/日志继续位于忽略目录。

## 本轮：DeepSeek真实验收（2026-11-15）

- 增加显式`app.ai.protocol=deepseek-json`，使用JSON对象模式、关闭思考、8192输出上限；默认严格Schema模式保留。预留估计和实际HTTP请求共用序列化，服务器Schema/图检查不变。无新依赖、迁移或前端行为改动。
- 用户将API信息写入模板；真实Key已移至Git忽略的本地配置，模板只清空Key，其他用户编辑保留且不纳入本次提交。官方V4.1 Flash API ID为`deepseek-flash`。
- Java21 `mvn -B spotless:apply verify`：25单元+16真实PG集成=41项，0失败/0跳过，日志`.runtime-deepseek-compat-verify.log`。Node24 `npm run format:check`、9项单测、`npm run build`（含类型检查）通过。Linux/Testcontainers和浏览器本轮未重跑；前轮Linux40项及浏览器6项证据继续保留，不混称本轮结果。
- 一次真实生成请求经本机Spring登录/CSRF/生成API发往DeepSeek，无重试、无额外修复。请求尺寸上限20000字节，输入保守预留加8192输出按官方高峰未命中费率估计最多0.113728 CNY，单任务预留0.2、每日限额1 CNY。模型返回SUCCEEDED，供应商总用量4630 tokens；按输入2/输出8元每百万保守记账0.03455000 CNY，非最终账单。
- 生成17节点/12关系，覆盖界面、建模、材质、灯光、相机、渲染及综合实践。原输出通过Schema/引用/无环检查；确认前版本数0；通过API编辑说明、预览有效、明确确认发布后版本数1/正式位置17，并读取历史。模型先修关系偏线性、UUID呈规律排列，内容仍需人工审核，不承诺随机性、教学完整性或多样本质量。
- 独立临时真实PG执行V1–V7；本机随机端口63301及数据库已关闭，临时引导密码删除，未修改保留的开发数据卷。真实结果/单次哨兵/验收脚本留在`.runtime`，不提交学习示例或Key；首次验收脚本将创建技能201误判失败，修正后重跑，首次在发送模型请求之前退出，真实调用总计仍为1。
- 证据摘要见P2_ACCEPTANCE.md；原始私有证据`.runtime/real-model-evidence.json`。GraphFlow本轮仍未提供，未执行context/plan/index。下一阶段为P3，按目标→路线→任务→学习时段→笔记/资源推进，先定义验收标准及迁移设计；不追加付费调用或生产部署。

## 初始环境检查

- 初始目录只有总纲（26466 bytes），不是 Git 仓库，无既有代码/用户数据。
- Windows11 amd64；Node25.2.1 / npm11.6.2；Maven3.9.16；系统Java25.0.2；发现 D:/Java21 的Java21.0.12，项目使用21。
- 无可用Docker / psql / 本地PostgreSQL服务；后续检查发现遗留com.docker.service处于Stopped，但其指向的C:/Program Files/Docker/Docker目录和可执行文件均不存在。未尝试启动残留服务或更改系统配置。
- 下载Node24.22.3便携版到.tools（官方SHA256校验），解决Vitest5不支持系统Node25的问题；下载独立Compose5.7.1并校验官方SHA256，仅用于配置解析，没有Docker引擎。
- Git已有身份配置；本地初始化与提交由总纲授权，不推送。
- GraphFlow MCP未提供，无法context/plan/index；已记录ADR-007。

## 已完成

- 阅读总纲；形成 PRD、架构、目标ERD/数据库约束、API合同、AI/安全、路线、测试与运维设计。
- 建立 AGENTS.md，记录默认决策和验证边界。
- 初始化本地Git（main），保留原始总纲；不创建远端、不推送。
- Vue3/TypeScript/Vite/Router/Pinia/Element Plus/Tailwind工程；状态页真实查询后端/PG，加载、故障、请求编号、显式重试、桌面/手机布局。
- Java21/Spring Boot3.5/MyBatis/Flyway/Bean Validation/Security/Actuator/OpenAPI工程；系统状态、存活/就绪、统一安全错误、请求ID；非公开接口默认拒绝，CSRF开启，无默认账号。
- V1迁移：users及唯一性/规范化/状态约束，audit_events及JSONB/外键/索引/追加触发器。无用户或学习数据种子。
- Compose/Nginx/Dockerfiles：同源入口仅loopback、DB持久卷、健康依赖、无默认密码；实际镜像标签远端manifest均存在。
- Windows验证和P0临时真实PG预览脚本；npm锁文件；CI定义覆盖前端质量、后端Testcontainers、Compose与浏览器烟测。
- P1身份：密码文件安全引导、BCrypt12、Spring Session JDBC、JSON登录/退出/当前用户、CSRF与会话ID轮换、统一失败及有界登录限流，无默认账号。
- P1技能/知识树：技能创建编辑归档恢复、分页搜索；稳定UUID节点创建编辑归档恢复、跨技能引用、独立父节点和顺序；依赖/关联增删改查、分类/先修环检测、所有权过滤/复合FK、图写入用户行锁、事务审计。
- P1界面：私有路由和会话恢复、技能列表/搜索/编辑、知识树列表编辑、位置调整、引用节点库、依赖编辑；桌面与手机均走真实API。移动端新建/编辑可定位并聚焦表单，避免在长页面寻找编辑器。新建节点引用失败保留定义并给出恢复提示。
- 本机启动辅助：Set-LocalAdmin安全提示输入密码、拒绝覆盖；Invoke-Compose优先原生Docker/回退现有WSL，支持config/ps/up/down等，不删卷、不改.env。

## 正在执行

- P2核心功能已实现并完成本地回归，正在整理最终证据和关闭隔离验收资源。
- V1生成、V2合并拆分/来源快照、节点/关系表单、重复提示、联合发布和历史查看均可用；发布始终明确确认，事务保留旧定义和版本。
- 生成默认关闭，token/货币策略和价格快照在服务端；V7保留私有主题用于最多两次显式修复，未知费用失败不自动重发。
- 最后门禁为用户授权后的真实供应商/模型质量验收。当前没有真实Key，不调用付费服务。P3–P6未开始。

## 已执行验证

### P2核心功能最终本地验收（2026-11-15）

| 范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B spotless:apply verify` | 24单元+16真实PG集成=40项，0失败/0跳过；`.runtime-p2-final-native.log` |
| Linux Java21/Maven `verify -Ptestcontainers` | 同40项全部通过，真实PG17.11容器/Ryuk，V1–V7迁移；`.runtime-p2-final-testcontainers.log` |
| Node24格式/类型/构建/单测 | 9项单元通过；生产JS196.97kB、CSS46.29kB（未gzip），无前端新依赖 |
| Compose重建/V7升级 | 复用隔离验收卷，V1–V6校验不变、V7新增请求关联表，DB/backend/web均Healthy；`.runtime-p2-final-compose-guard.log` |
| 默认关闭配置Nginx浏览器 | 桌面/手机4项通过：任务页、手动图谱/草案/版本、状态V7；`.runtime-p2-final-default-guard.log` |
| 启用配置本地协议浏览器 | 桌面/手机2项通过：Blender主题生成→拆分→关系编辑→合并→撤销重做→重命名→预览发布→非法结果→确认修复→新任务成功；`.runtime-p2-final-enabled-guard.log` |

新增后端故事验证V2映射持久化、revision409、旧节点/版本保留、重复名称识别、真实Schema执行、来源/时间字段、共享节点默认保护、联合发布第二技能故障注入全部回滚及重试幂等、有限修复成功/重复不重发/最多两次/原发布版本不变。修复测试使用真实适配器向loopback测试HTTP发送，不替换生产Provider。

最后补充模型来源防伪：模型请求V1，返回即使结构有效的V2人工映射也必须失败。新增协议用例覆盖，Windows/Linux仍各40项通过，之后重新构建并复跑全部6项浏览器测试。没有把模型声称的人工来源当作可信记录。

容器构建曾发现联合确认的Vue内联多语句被格式化后无法解析，改为独立事件函数；状态页旧AI文案已改为读取真实配置，并新增启用状态单测。修正后生产构建、9单测和Nginx4项复跑通过。

真实供应商、模型质量、供应商账单核对与远端CI未验证。已确认项目无.env且无APP_AI_KEY；不得拿本地测试协议响应冒充真实模型。不生产部署、不进入P3。GraphFlow未提供，未声称context/plan/index已执行。完整设计/门禁见P2_DESIGN/P2_ACCEPTANCE。

收尾已关闭临时Spring/协议服务/PG/Vite，确认临时密码及元数据删除；停止Compose验收容器/网络，postgres-data卷保留。生产jar确认包含Schema引擎、不包含GenerationTestRuntime。发布/生成写入过程中冻结JSON编辑、联合范围和切换草案，避免响应覆盖用户正在输入的内容；冻结修正后重建并再次通过9单测/构建和全部6项浏览器回归。

### P2启用生成完整链路验收

- 新增GenerationChainIT及测试源集GenerationTestRuntime：真实Spring Boot、PG、登录/CSRF、完整服务器配置和真实loopback HTTP传输，协议响应是确定性测试数据，未调用模型。生产源代码/迁移/实际配置未改，不打包测试协议服务。
- Java21 `mvn -B spotless:apply verify`最终20单元+16真实PG集成=36项，无失败/跳过，日志`.runtime-p2-enabled-verify-final.log`。新故事验证生成成功候选落库、同键仅调用一次、非法结构/截断失败、费用快照、预算结算；未确认前无发布版本/正式节点位置。
- 前端8单元、类型/构建通过；`GENERATION_ACCEPTANCE=1`选择独立启用套件，Vite15173→真实后端18080→PG/本地HTTP服务，桌面和手机共2项通过（`.runtime-p2-enabled-browser-final.log`）。验证费用确认、生成→编辑器→预览→明确发布、非法结果提示/任务列表、无JS异常/横向溢出；两张截图已查看。默认套件通过`--list`确认仍4项，本轮未重跑关闭配置的Nginx4项。
- 首轮浏览器断言误用文本节点读取textarea值，改为toHaveValue后用全新隔离库复跑成功；协议响应补齐required的可空parent字段。没有修改生产逻辑来迎合测试。
- 本地协议验收不代表真实供应商Schema接受、模型质量或生产部署成功；未重跑Linux Testcontainers/远端CI。GraphFlow仍不可用，未声称context/index已执行。复现与资源清理步骤见GENERATION_ACCEPTANCE.md。
- 收尾确认18080/15173端口无监听，Spring/协议服务/Vite/临时PG均关闭，临时密码及元数据已删除；未触碰保留的Compose数据卷或实际配置。

### P2 Schema与受控生成入口

- 最终Windows Java21 `mvn -B spotless:apply verify`：20单元+15真实PG集成=35项，无失败/跳过；日志`.runtime-p2-generation-verify-final.log`。新单测覆盖供应商Schema required/nullable/enum转换、原始合同不变、UTF-8和转义请求字节预留、超大输入、未配置零调用。原Provider loopback HTTP测试现发送实际转换后的Schema。
- HTTP故事验证关闭状态、受保护生成请求503且无任务记录；原PG故事继续验证生成候选/预算事务。供应商Schema转换和服务端图规则独立保留，未增加通用JSON Schema引擎，不声称支持任意Schema。
- Node24格式、前端8单元、类型和生产构建通过；Compose三个服务Healthy，仍为V6、无迁移修改；Nginx桌面/手机4项E2E通过（`.runtime-p2-generation-e2e.log`），新增未配置提示断言，再走手动草案发布旅程。桌面草案页截图已查看。最后系统AI状态配置读取改动由Windows完整回归验证，容器E2E为该状态小改前的同一UI/默认关闭配置。
- 未对启用状态页面执行实际模型生成，未证明供应商接受Schema或生成质量；本地HTTP/测试Provider不等于真实模型联调。未运行新一轮Linux Testcontainers，GraphFlow仍未暴露。无付费调用、无生产部署。
- 隔离验收容器和网络已停止，postgres-data卷确认保留；本轮无迁移修改，原总纲未改，不推送。

### P2货币预算切片

- Windows Java21 `mvn -B spotless:apply verify`，最终17单元+15真实PG集成=32项，无失败/跳过；日志`.runtime-p2-money-verify-final.log`。新增单测覆盖精确计算、8位向上舍入、负费率/精度/预算非法、未配货币策略不得启用Provider。
- PG故事覆盖价格快照、已知用量计算、未知费用保守占用、重复结算、超预算拒绝、跨用户费用不可见；完整编排测试确认货币不足时任务/草案/token预留一起回滚且Provider未调用。HTTP覆盖无费用记录显式null、未知任务404。价格均为测试数据，无供应商报价或付费调用。
- Node24前端8单元、类型和构建通过。Compose保留验收卷从V5迁移V6，三个服务Healthy；真实Nginx桌面/手机4项E2E通过（`.runtime-p2-money-e2e.log`），覆盖任务空页和原有草案旅程，不冒充费用页面或生成旅程验收。未重跑Linux Testcontainers整套。
- 当前预算基于服务器配置的单次预留；已知费用可超过预留并如实入账，未知用量也可能超过预留，不承诺供应商硬支出上限。多次调用的成本尚未实现，因此真实Provider启用时要求retries=0。仍需可信token策略、Schema兼容、供应商证据对账和生成入口。
- 隔离验收容器和网络已停止移除，确认postgres-data卷保留；原总纲及V1–V5迁移未改动，不推送、不生产部署。GraphFlow仍未提供，未声称已context/index。

### P2任务查询与保守恢复

- Windows Java21 `mvn -B spotless:apply verify`最终14单元+15真实PG集成=29项通过，无失败/跳过（`.runtime-p2-recovery-verify-final.log`）。扩展HTTP故事验证列表/详情、参数上限、未确认422、活跃409、过期恢复、重复结算幂等、未知ID404；应用故事验证恢复后迟到结果不写草案、不再次结算。
- Node24格式、8单元、类型和构建通过；Compose重建三个服务Healthy，无新迁移；真实Nginx桌面/手机4项E2E通过（`.runtime-p2-recovery-e2e.log`），增加任务页导航、真实空状态和刷新、无横向溢出，再走原有草案发布旅程。桌面/手机任务页截图已查看；恢复表单尚未通过浏览器实操，恢复语义由真实PG/API测试验证。最后的任务查询缓存修复由Windows完整回归验证，容器E2E为修复前同一UI代码。
- 迟到结果测试曾发现事务暂停期间MyBatis会话缓存返回旧RUNNING状态；对按键查任务显式flushCache后保留回归验证，不能仅以数据库已提交判断调用返回正确。
- 未调用任何外部模型，未添加生成任务写入口，未运行新一轮Linux Testcontainers。GraphFlow仍不可用，未声称context/index已执行。预算是token配额，恢复不释放资金或中止供应商请求；迟到用量对账留待货币策略实现。
- 收尾已停止隔离验收容器和网络，确认postgres-data卷保留；未推送或部署生产环境。

### P2任务账本切片

- Windows Java21 `mvn -B spotless:apply verify`最终14单元+15真实PG集成=29项通过，0失败/0跳过，日志`.runtime-p2-jobs-verify.log`。首轮测试账号哈希长度不符合既有约束，修正测试数据后复跑通过，未放宽数据库约束。
- 新集成故事验证任务幂等、无外层数据库事务的Provider调用、非法图失败、合法候选保存草案但不发布、已知用量结算、未配置释放、超时保守占用、超预算拒绝和两线程并发仅一项获准。Provider为测试方法内注入，不冒充真实模型联调。
- 前端8单测、类型/构建通过。Compose保留验收卷从V4迁移至V5，三个服务Healthy；本轮未重跑Linux Testcontainers整套。真实Nginx桌面/手机共4项E2E最终通过（`.runtime-p2-jobs-e2e.log`），覆盖既有登录、图谱、草案发布历史；首轮旧V4页面文本断言修正为V5后复跑成功。没有生成UI，本次浏览器测试不作为生成旅程验收。验收容器/网络已停止移除，数据卷保留。
- token预算只限制预留，不承诺实际模型支出的货币上限；实际用量超预留仍如实记账，未知用量保守占用。进程中断保留RUNNING/额度，恢复入口和货币策略完成前不开放浏览器生成。

### P2 Provider传输切片（2026-11-15后续）

- Windows Java21 `mvn -B spotless:apply verify`通过12单元+14真实PG集成=26项，0失败/0跳过，包含默认禁用Bean下应用启动、原有草案事务回滚；日志`.runtime-p2-provider-verify.log`。
- 随后仅新增超时/超大响应测试，`mvn -B spotless:apply test`最终14单元全部通过（其中8项Provider测试）；日志`.runtime-p2-provider-unit-final.log`。最终生产代码与上一轮verify相同，14项集成无需重复。
- Provider测试使用真实loopback HTTP传输，检查请求Schema/模型/授权头、返回用量、缺失用量UNKNOWN、429重试上限/401不重试、拒绝/截断/非法/尾随JSON、禁用零请求、HTTPS限制、超时不重发、2MB响应限制。无供应商联调、无付费请求，不把本地协议结果当作模型生成成功。
- 前端8单元、类型检查/生产构建通过；本轮无UI或数据库迁移变更，未重跑浏览器/Compose/Testcontainers，上一切片证据保留。GraphFlow仍未暴露，未声称索引。
- 首轮测试响应转义错误导致2项失败，改用Jackson生成测试响应后全套通过。Provider不持久化任何业务数据；生成任务/预算及完整P2验收仍待完成。

### P2手动草案切片验证

| 命令/范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B verify` | 最终后端源代码6单元+14真实PG集成=20项通过，0失败/0跳过；日志`.runtime-p2-backend-final.log` |
| Linux Java21/Maven `verify -Ptestcontainers` | 6单元+14真实PG容器集成通过，0失败/0跳过；日志`.runtime-p2-testcontainers.log`。随后新增的规范UUID/标题快照归一化、Graph.versioned字段已在Windows全套验证，未重复执行容器全套 |
| Node24 `npm run format` / `npm test` / `npm run build` | 8项单元通过，类型检查和生产构建通过；JS179.95kB/CSS45.48kB（未gzip） |
| Compose `up --build -d --wait` | V4迁移及前后端镜像重建成功，DB/backend/web均Healthy；验收使用独立项目/卷与密码文件，无真实用户数据 |
| 真实Nginx `npm run test:e2e` | 最终4项通过，桌面/手机手动旅程覆盖草案新增、撤销重做、保存、预览、确认发布、历史读取、返回知识树只读保护、退出；无API mock/JS异常/横向溢出；日志`.runtime-p2-e2e-final.log` |
| 截图/清理 | 已查看桌面和手机draft-published完整截图，响应式排版可用；验收容器和网络已停止移除，独立postgres-data卷确认保留；未配置真实用户.env，未生产部署 |

新增集成故事覆盖草案与正式图谱隔离、revision过期409、确认发布、幂等重试、快照不可改、发布后旧编辑API拒绝、源变化409、发布中途故障注入后完整回滚、共享已发布节点变更拒绝。GraphValidator单测覆盖非法类型/字段/版本/UUID、标题归一化、重复引用和跨图先修环。浏览器回归发现只读提示写入为问号，修正后重建容器并全套复跑通过。

### P1本轮验证

| 命令/范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B spotless:apply verify` | 3项单元+13项真实PG集成=16项通过，无失败/跳过；包括登录后旧CSRF拒绝 |
| Linux Java21/Maven容器 `mvn -B verify -Ptestcontainers`（受测shell入口） | 同16项通过，无失败/跳过，真实PG17.11容器与Ryuk；最后追加的旧CSRF断言另在Windows全套验证通过 |
| `npm run format`/typecheck/build | 通过；最终构建JS约170kB、CSS约45kB（未gzip）；无新增前端依赖 |
| `npm test` | 8项通过：状态/错误/重试、写前CSRF、凭据失败不写入、保留请求ID、提交值快照 |
| Compose `up --build -d --wait` | 通过，V1保留原checksum，V2/V3从P0持久库迁移成功，管理员文件引导、三个服务就绪 |
| 真实Nginx `npm run test:e2e` | 4项通过：桌面/手机各系统状态+登录创建Java技能/层级节点/依赖/拒绝环/刷新恢复/重命名/退出；无API mock，HttpOnly/Lax cookie、无JS异常、无横向溢出 |
| 截图视觉检查 | 已查看桌面和手机的完整知识树页面，表单/导航/关系/层级可见、无裁切；状态页截图同步 |
| Set-LocalAdmin测试 | 创建测试密码文件通过；已有文件写入被CreateNew拒绝；临时脚本测试文件已删除，不覆盖真实账号/配置 |
| Invoke-Compose本机WSL分支 | config/ps/up/down通过；保留密码和独立项目名，原生Docker分支本机未单独运行 |
| 依赖审计与结束清理 | 本轮复查npm生产依赖0、开发链5项moderate；已停止验收容器/网络，确认独立PostgreSQL卷保留；秘密/构建/截图均未入Git |
| CI配置 | YAML解析通过；加入随机隔离管理员密码文件和E2E配置；远端CI未运行 |

后端8个P1集成故事覆盖：安全管理员引导/重复不重置、Cookie/CSRF/会话轮换/退出、会话跨新应用实例、双用户资源404和SQL复合FK拒绝、共享节点顺序独立/重命名UUID不变/分类环/叶节点引用移除、先修环及相反边并发仅一条成功/拒绝后不写审计、分页字面搜索和归档恢复、登录限流。保留原5项系统/迁移/宕机集成与2项请求ID单元，新增1项凭据日志脱敏单元。

### P0历史验证（保留阶段证据）

| 命令 / 范围 | 实际结果 |
| --- | --- |
| Java21 `mvn -B spotless:apply verify` | 后端编译/打包/格式通过；2项JUnit单元+5项真实PG17.11集成全部通过，无跳过 |
| `scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64` | 最终端到端脚本退出0：npm ci、格式、类型、4项前端单测、构建和Maven verify全部通过 |
| `npm run typecheck` / `npm run build`（隔离Node24） | 通过；产物JS约147.74kB、CSS约41.72kB（未压缩） |
| `npm test` | 4项通过：真实合同解析、503后重试、网络失败、非法响应；单元环境允许stub fetch |
| `npm run test:e2e`（真实P0Preview后端） | 桌面+390px手机共2项通过，无API拦截；后端与PG均实际连接，刷新、无水平溢出、无页面JS异常；两张截图已逐张查看 |
| 独立Compose `config --quiet`（临时仅验证密码） | 通过；缺少密码被明确拒绝；未保存.env |
| Docker Hub manifest HEAD | Maven / Temurin / Node / Nginx / PostgreSQL所用5个标签均返回200；这不代表镜像构建/运行通过 |
| `npm audit --omit=dev` | 生产依赖0漏洞 |
| `npm audit` | 5项moderate（开发依赖链Vue Test Utils/js-beautify/glob/minimatch/editorconfig），0 high/critical；ADR-008记录，未强制跨版本覆盖 |
| Docker构建 / Compose空卷启动 | 本轮通过：WSL2 Ubuntu隔离Docker29.9.3、Compose5.7.1，前后端镜像构建成功、三个服务Healthy |
| PostgreSQL容器重建 / 后端重启 | 本轮通过：新建验收审计记录后重建DB容器并复用卷，记录保留；Flyway历史仍1条；后端重启后再次就绪 |
| `mvn -B verify -Ptestcontainers`（Java21/Maven容器） | 本轮通过：2单元+5真实PG容器集成，0失败/0跳过，Ryuk正常启用 |
| `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8088 npm run test:e2e` | 本轮通过：2项桌面/手机直接访问Nginx生产构建，未启动Vite、未拦截API；API与HTML均200并含CSP |
| 远端CI | 未运行：未配置远端仓库；本轮在本地执行实际容器和测试，不冒充远端结果 |

集成测试真实覆盖：空库V1迁移、validate与重复migrate幂等、规范化email唯一约束、非法状态、审计JSON约束/不可改删/外键保护、未登录401/CSRF403/Basic无效、requestId、应用实例关闭后重新启动保留记录、实际OpenAPI只含已实现端点、停PG后status/readiness503且liveness200、不泄漏连接信息。

首轮问题均已定位并修复：SpringApplication默认属性优先级不足导致新实例连错库（改用显式启动参数）；健康响应包含groups（改为检查无敏感components/details）；Element Plus样式从页面移至入口解决测试CSS加载；使用延迟响应验证Vue加载状态；系统Node25不受Vitest支持（隔离Node24）；Windows临时文件锁导致一次构建失败后复跑通过；PowerShell5.1将npm stderr警告视为异常（检查真实退出码，保留告警输出）。

## 阻塞与下一步

- P0无剩余验收阻塞。本轮发现现成WSL2 Ubuntu；提取Docker静态二进制和iptables运行依赖到专用缓存，没有安装系统包/服务、改全局PATH或修复残留Docker Desktop。发行版首次检查为Stopped，本轮仅启动Ubuntu，未触碰docker-desktop发行版。运行时详情见OPERATIONS。
- 本机可按README使用临时真实PG预览；它不保留用户数据，不能作为长期使用部署。截图和日志在忽略目录，正式证据摘要进仓库。
- 开发依赖5项中等级告警待上游兼容修复；生产依赖审计无发现不代表全栈安全审计完成。生产前重新评估Java依赖和容器漏洞。
- GraphFlow恢复可用后执行context/plan/index，当前不能声称已索引。
- **当前P6后续**：分析/迁移/恢复、监控日志、独立迁移/只读备份身份、TLS/资源、Java25及SDK补丁已有本地证据，最终安全扫描见本批验收文档。继续复核中低/未知与生产前验收；实际生产域名/续期、权限切换、公网部署、付费支持与外部告警另行确认。P5的AI文字建议、P4图片自动识别和供应商账单对账局限保留。
- 现有边界：源指纹保守覆盖整个用户目录，其他技能变化也可能要求新草案，不自动冲突合并。改变其他已发布技能必须明确联合范围、重新预览及确认。表单/JSON编辑器没有拖拽画布；来源映射保留节点与快照，不自动迁移能力证据。登录限流仍为单实例内存，节点库最多100项并提供搜索；无密码重置/多用户注册。部署是私有本地开发配置，不是生产安全验收。
- P2与本轮P4各一次付费调用授权均已使用；不追加调用、不生产部署、不自动安装系统服务、不执行不可逆数据操作。
