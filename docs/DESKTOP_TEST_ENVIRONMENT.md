# D3 干净 Windows 测试环境方案

2026-11-17；用户已确认没有现成虚拟机/第二台测试电脑，本批只准备方案和测试介质，不安装虚拟化软件、系统镜像、驱动或账户。GraphFlow工具未提供，定向只读检查；D3未完成，不推进D4。

## 本机结论

- CIM实际系统：Microsoft Windows 11 家庭版 中文版，x64，10.0.26300；EditionID=CoreCountrySpecific。旧注册表ProductName仍显示Windows10，不据此误判实际系统。
- 处理器 i5-1340P，物理内存约32GB。HypervisorPresent=true；CPU的部分虚拟化能力字段为false，可能被已运行的hypervisor屏蔽，不能据此判断BIOS虚拟化关闭。
- C盘可用约9.6GiB，D盘约163.6GiB（准备时快照，实施前重查）。VM、ISO、下载和快照均规划放D盘，保留宿主至少30GiB可用空间。
- WindowsSandbox.exe、现成VirtualBox/VMware/Hyper-V管理环境未发现。微软明确Home不支持Sandbox，不能简单靠启用该功能解决；不使用绕过版本限制的脚本，也不为此直接购买Pro升级。[微软Sandbox版本要求](https://learn.microsoft.com/en-us/windows/security/application-security/application-isolation/windows-sandbox/)

## 推荐配置

采用 Oracle VirtualBox **7.2.22基础包** + 独立Windows11 x64客体。版本来自准备时的[官方稳定版标识](https://download.virtualbox.org/virtualbox/LATEST-STABLE.TXT)，实际安装前固定官方安装器SHA并复核版本。仅需基础包，不装单独许可的Extension Pack。[Oracle组件说明](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/Introduction.html)

| 项目 | 方案 |
| --- | --- |
| VM名 | ScarletNexus-D3-Clean |
| 位置 | D:/VMs/ScarletNexus-D3-Clean，实施后才创建 |
| 资源 | 4 vCPU、8GiB RAM、100GiB动态系统盘、EFI/TPM2.0，满足官方Win11安装条件 |
| 安装方式 | 手动安装，勾选跳过无人值守安装，禁止向基线自动安装Guest Additions |
| 客体语言 | 简体中文；另建真正非管理员本地账户，实际配置文件夹含中文和空格 |
| 系统来源 | 微软官方Windows11 x64镜像及适当客体许可；没有时按官方用途/条款考虑90天Enterprise评估，不能当永久授权 |
| 文件传入 | 本批准备的只读测试ISO；不映射宿主学习库/密钥/整个磁盘 |
| 初始阶段 | OS安装/激活/更新按官方流程，完成后准备标准账户并断开客体网卡 |
| 验收阶段 | 不装Node/Java/PG/Docker、Guest Additions、额外VC++运行库；剪贴板/拖放/共享文件夹/USB透传关闭 |

微软评估镜像需要注册并遵守90天评估条件，官方当前页面列出Microsoft账户要求；账号/条款由用户自行处理，不自动注册或绕过OOBE。系统只装在虚拟磁盘，绝不替换宿主系统。[官方评估入口](https://www.microsoft.com/en-us/evalcenter/evaluate-windows-11-enterprise)

本机已有hypervisor，VirtualBox与Hyper-V/VBS共存可能影响性能。安装后先验证能否在原配置下启动客体；不能启动/性能不足就保留日志再定方案，不自动关闭Hyper-V/VBS/WSL、修改bcdedit或重启宿主。虚拟化驱动安装可能要求管理员或短暂网络变化，属于后续明确实施范围。[Oracle已知问题](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/Troubleshooting.html)

## 分步实施与快照

1. 后续用户授权实施时，核验VirtualBox官方包/版本/SHA，规划安装与可能重启；保留VS Code、WSL和Docker现状。安装完成不等于VM可用，先试启动。
2. 创建VM时跳过无人值守安装，关闭自动安装Guest Additions；使用官方授权镜像手动安装客体，记录镜像SHA、版本、VM配置和安装过程。初始管理员仅用于客体准备，不用于普通用户验收。
3. **只在客体**通过Windows设置创建本地标准账户，例如“芙兰 测试”，首次登录后确认真实USERPROFILE路径含中文和空格且不属于Administrators；不能只改显示名，不能用“管理员未提权”代替标准账户。
4. 不装Guest Additions或开发工具，不预装VC++运行库来掩盖缺失依赖。挂载本批只读ISO，将安装包和检查脚本复制到客体自有目录；断开客体网卡，关闭共享/剪贴板/拖放，记录基线检查。
5. 关机保存快照 **S0-clean-offline-standard**（完整可回滚干净基线），并记录用户类型、OS/镜像/ISO SHA。已有快照不靠口头描述证明干净。
6. 在标准账户下，按CHECKLIST安装d3.3、首次设置、手动业务，关闭/重开。保存 **S1-d33-with-fixture**。
7. 覆盖安装d3.4，确认自动升级备份、旧账号/图片保留；向新profile执行恢复，验证权限与旧数据、退出重开、卸载/重装保留库，收集证据。全部通过才关闭相应环境门禁。

快照期间的新增磁盘写入占用宿主D空间；100GiB是虚拟容量上限，不是立即分配量。低于30GiB余量时暂停，不让宿主盘耗尽。

## 故障测试单独分支

基础环境通过后从S1克隆故障分支，不在宿主/原学习库做实验。磁盘满仅针对客体另加的**独立小测试盘**（例如8GiB、卷标SN_D3_SCRATCH），将测试profile放该盘，先另存备份/密钥到客体其他私有位置。不得填充宿主C/D或客体系统盘。

在备份/迁移/恢复关键阶段，分别测试小盘写入失败和虚拟机“强制断电”，再启动核对源数据、pending标记、原版本可用性与恢复。记录具体时间点与预期/实际，失败就保留现场并还原测试快照。虚拟机强制关机模拟客体异常断电，不等于证明宿主硬件掉电/磁盘控制器缓存场景；磁盘满也必须是真实受限测试卷，不能把抛出的模拟异常记成物理实测。本批未执行这些操作。

## 已准备工具与介质

- Audit-WindowsEnvironment.ps1：只读采集Host/Guest环境，写入新的报告文件；不安装、启用/禁用网卡、修改账户或开关系统功能。Guest报告检查管理员组成员而不仅是当前提权，记录路径Unicode/空格、全局工具/已装运行库和连接状态；不能自动证明全新OS或业务通过。
- Prepare-CleanWindowsKit.ps1：按版本与SHA白名单复制d3.3/d3.4安装器、检查脚本、说明与验收表，可用Windows内置IMAPI生成只读ISO；不包含源码依赖、学习数据、备份或密钥。它生成的manifest状态始终NOT_RUN。

在项目根目录准备介质（输出目录必须尚不存在）：

```powershell
powershell -NoProfile -File desktop/test/Prepare-CleanWindowsKit.ps1 -OutputRoot .runtime/desktop-clean-windows-kit -CreateIso
powershell -NoProfile -File desktop/test/Audit-WindowsEnvironment.ps1 -Mode Host -OutputFile "$PWD/.runtime/desktop-clean-windows-host.json"
```

ISO校验可用 `python desktop/test/verify-clean-windows-iso.py 完整ISO路径 对应media路径`，只读解析Joliet并逐文件比较，不挂载、不执行安装器。Python仅用于宿主验证，不进客体介质。

ISO只是软件测试介质，不是Windows系统安装镜像，也不证明客体已经创建。完整验收表见 [DESKTOP_GUEST_CHECKLIST](DESKTOP_GUEST_CHECKLIST.md)。所有条件、安装/业务结果和故障结果要分别记录，不能用本机/PATH移除结果冒充客体成功。

## 当前边界和下一步

本批可交付方案、宿主诊断和离线测试ISO；未下载/安装VirtualBox、Windows镜像、Guest Additions或证书，未创建账户、VM或修改宿主功能。下一步是按此方案选择合法系统镜像并安装虚拟化基础包、验证现有hypervisor共存；这会涉及系统驱动/可能重启，不能从“先准备方案”推定已授权执行。
