# D3 Windows 安装版验收

本页保留d3.2第一批实证；d3.3流式备份与新失败/业务回归见 [D3第二批](DESKTOP_D3_RECOVERY_ACCEPTANCE.md)。

2026-11-17，从 04c2078 继续，用户授权下一批。GraphFlow 未暴露工具，采用定向检查；保留用户 .env.example/.gitignore。不推送、不发布、不购买证书。

## 实现前验收标准

1. 固定构建工具和锁文件，离线 NSIS Windows x64 安装包内含 Electron、已核验 Java/PG、真实 JAR/Vue 和许可；白名单收包，不带学习数据/密钥/开发依赖。不运行自动更新。
2. 当前用户安装、桌面和开始菜单快捷方式、产品图标；不安装系统服务、不要求全局 Java/PG/Node/Docker，卸载保留学习数据和密钥。安装目录与数据目录分离，拒绝把安装目录作为学习存储。
3. 实际安装到隔离中文路径，真实已安装程序完成首次设置/登录/业务写入、关闭重开、同版本覆盖安装、卸载保留数据、重装后原账号和业务数据仍可用；识别本程序所属进程，不广泛结束进程。
4. 产物 SHA-256 和复现命令、依赖审计、桌面单测/格式/语法及适用回归记录；高风险或安装生命周期失败必须保留。
5. 干净 Windows 普通用户、中文用户名、无全局工具、离线安装/启动与完整业务/升级故障矩阵需实测；缺少干净系统时明确保持 D3 未全部完成。签名和完整制品扫描/Release 属 D4。

## 实现与已确认本机结果

- 构建：新增 build.cjs、builder.config.cjs、自定义 NSIS include、自制 SN SVG/多尺寸 ICO。当前用户安装、禁止提权/系统服务，完整离线 x64 NSIS，桌面/开始菜单快捷方式；资源和数据分离。安装器在 uninstallOldVersion 之前检查 owner.json/PG_VERSION 祖先链，拒绝把数据树当安装目录；运行中的产品拒绝安装/卸载，未使用 taskkill 分支。默认学习目录保持 D2 路径；Electron45 实测 setName 后默认配置根兼容，`.runtime-desktop-d3-default-profile-confirmed.log` 为 true。
- 最终预览构建：`DESKTOP_RUNTIME_DIR=.runtime/desktop-bundle-d2-verified`、`DESKTOP_DIST_DIR=.runtime/desktop-d3-preview`（实际命令均使用绝对路径），`npm run dist:win --prefix desktop` 成功，见 `.runtime-desktop-d3-build-preview.log`。安装包 `.runtime/desktop-d3-preview/ScarletNexus-0.1.0-d3.2-windows-x64-setup.exe`，312017988 字节，SHA-256 `72285cfcca6d9603e3e7aa76b014d3d14425cfa0c4b4251b77d12631fa963758`，旁置 SHA256SUMS.txt。Authenticode 未签名，不代表已通过 D4。
- 已检查运行文件 PE machine=0x8664；app.asar 只有 gateway/main/package/runtime/setup preload/css/html/js 共8文件，没有 node_modules、测试、.env、账户或数据库。运行时只复制 D2 已验证清单文件，保留 Java/PG/Electron 许可，外部完整清单本身未签名。已从 EXE 提取并查看 SN 图标。
- 7 项桌面单测、CJS 语法和适用 Prettier 检查通过。前端 format/typecheck/14单测/build 通过，保留既有 >500kB 图表警告。没有改后端/迁移或前端业务代码；沿用 D2 最终 JAR（84项含真实 PG 全量 verify 通过）的相同字节，不把本批说成新跑84项。
- 构建依赖固定 electron-builder26.18.0；初报6中风险均链自 minimatch GHSA-g3hv-75wg-m249。仅覆盖受影响 >=7 <11.0.1 范围到11.0.9，避免改变3/5的旧API分支。初次增量安装仍留无效嵌套9.0.9，删改锁的历史副本保存在 .runtime 后重新生成锁并 npm ci，才确认 npm ls 无无效版本、npm audit 零发现（`.runtime-desktop-d3-audit-confirmed.json`）。无 audit fix --force；只代表 npm，不代表 NSIS/7zip/Electron/Java/PG 二进制安全。
- 初次程序化构建误传字符串架构，产物标为 windows-1，保留失败/开发产物，修正使用官方 Arch.x64，后续构建和实际 PE 均为 x64。干净 npm ci 后宿主脚本策略未落盘 Electron dist，构建拒绝；显式运行固定 electron/install.js 官方校验下载后重建，增加前置缺失检查，不跳过 TLS/归档校验。此类开发构建前置不属于普通用户安装要求。
- 第一轮同版安装往返已通过（`.runtime-desktop-d3-installer-roundtrip.log`）：真实中文安装与独立配置目录、首次设置/登录/中文技能、关闭重启、同版覆盖、卸载保留密钥、重装保留原账号和技能、最终卸载。快捷方式目标实际核对为本次安装 EXE；没有用开发 Electron 代替已安装主程序。业务阶段 PATH 仅系统目录且 JAVA_HOME 指向不存在位置，仍通过内置 Java/PG；这证明不依赖全局工具搜索路径，不等于干净系统或断网验证。
- 增补运行中拒绝测试时，标准 NSIS uninstaller 的外层复制进程先返回0，最初错误地只断言其外层退出码，出现 Missing expected rejection（`.runtime-desktop-d3-installer-confirmed.log`）。产品文件和数据仍在，未发生删除；改用 NSIS 标准 `_?=安装目录` 直接等待实际卸载脚本，已确认安装/卸载都返回2且页面仍活着。原测试目录保留，用范围核验后的本产品卸载器清理安装，未手工递归删除数据。

## D3 尚未通过的门禁

当前仅交付本机预览安装切片，**D3 保持进行中**：宿主 WindowsSandbox.exe 不存在，不能冒充干净 Windows 或普通非管理员账户成功；未启用系统功能/创建系统账户/修改系统服务。后续需要全新 Win11 x64、普通账户与中文用户名，在没有全局 Java/Node/Docker、网络断开时完成安装、首次设置、完整手动学习旅程、附件/导出恢复、关闭、升级、卸载重装。真实模型凭证/AI配置、跨 schema 迁移失败/断电/磁盘不足、备份大容量与恢复 UI、ProgramData/junction 普通用户权限及卸载边界仍须验收。

D4 的全制品供应链扫描、签名/证书和 GitHub Release 不提前开展或宣称通过；安装包仅本地未签名预览，不公开发布、不自动推送。GraphFlow工具未提供，未执行 context/plan/index。个人配置/学习目录未用作测试，所有账户与学习证明在隔离 .runtime 内保留。

## 最终往返与跨版本复核

实际执行：

```powershell
powershell -NoProfile -File desktop/test/installer-smoke.ps1 -Installer .runtime/desktop-d3-dist-ready/ScarletNexus-0.1.0-d3.1-windows-x64-setup.exe -UpgradeInstaller .runtime/desktop-d3-preview/ScarletNexus-0.1.0-d3.2-windows-x64-setup.exe
```

`.runtime-desktop-d3-installer-upgrade.log` 最终退出0、installer_roundtrip=passed：

- learning_directory_install_refused=true：新版在写入或清旧版之前拒绝数据树祖先标记，哨兵内容保留。
- 在初版和升级后的产品运行期间，安装/卸载都拒绝，业务窗口继续有效。
- 初始、重启、覆盖升级、重装四个阶段均为 packaged=true、actualSkill=true、globalToolsRemovedFromPath=true、ownedServicesStopped=true、pageErrors=[]。
- cross_version_upgrade_backup=true：真实 d3.1→d3.2 版本标识变化触发库/附件升级前加密备份，然后仍能读取原技能。内部恢复格式与解密/真实数据库恢复证明沿用 D2，未声称模拟未来 schema 变化失败。
- 卸载后数据库密钥 SHA-256 未变化，重装继续使用原账号/技能；最终卸载产品快捷方式与注册项消失，学习目录保留在 `.runtime/desktop-d3-install-ee56a7d0c10f47f8b61003278bf782a5/独立用户`。已查看 installed-reinstall.png，中文技能布局正常。没有本批拥有的 Java/PG/产品进程残留，未停止其他工具进程。
- 最终包 app.asar 中七个源码/设置资源与当前工作树字节一致，另一个文件为生产 package.json；文件白名单共8项，运行时 release=0.1.0-d3.2，SHA256SUMS 与实际安装包字节匹配；Authenticode 状态 NotSigned。最终发布锁再次 audit 为0（`.runtime-desktop-d3-audit-release.json`）。

本机 D3 第一批安装切片可交付，本阶段其余门禁继续保持未通过，不转入 D4。
