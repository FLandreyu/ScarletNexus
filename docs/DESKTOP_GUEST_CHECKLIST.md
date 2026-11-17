# Scarlet Nexus D3 客体验收表

状态为进行中，已取得身份、环境、安装前快照元数据和首次设置/登录/系统连接截图，完整业务、升级恢复仍待执行。用于新建、可回滚的Windows虚拟机。先读TEST-ENVIRONMENT.txt，按实际截图和运行结果填写。

| 条目 | 操作与通过标准 | 实际结果/证据 |
| --- | --- | --- |
| G0 来源 | 官方OS镜像SHA、实际虚拟化平台版本与来源、VM配置、S0基线快照编号 | 来源与配置记录见DESKTOP_EMPTY_VM_SETUP。vmsd确认S0-before-install/Snapshot1，1个快照。尚未验证此快照实际回滚 |
| G1 身份 | 普通账户不属于Administrators；真实USERPROFILE含中文和空格 | PARTIAL。2026-11-17 21:02用户截图显示C:/Users/芙兰、BUILTIN/Users、Medium Mandatory Level，无S-1-5-32-544。普通账户和中文已确认，空格路径仍待补测 |
| G2 干净 | 基线无Node/Java/PG/Docker/Guest Additions；记录系统自带VC运行库，禁止为了通过安装而先补运行库 | 2026-11-17环境截图确认七个工具均detected=false、匹配包列表为空。限脚本检测范围，完整纯净来源仍结合安装/快照记录 |
| G3 离线 | 客体网卡断开、无共享/剪贴板/拖放；从只读ISO复制到客体后断网安装 | 环境报告upAdapterCount=0/allAdaptersDisconnected=true，宿主配置禁共享/复制/粘贴/拖放。其后安装与系统状态截图已取得，任务栏保持断网图标 |
| G4 安装 | 核对安装器SHA；标准账户安装d3.3、桌面和开始菜单快捷方式，无额外安装运行库 | PARTIAL。用户按d3.3流程完成，21:19截图可见桌面快捷方式和首次设置窗口。客体安装版本、是否有授权提示及开始菜单仍待单独核验 |
| G5 首次运行 | 点击快捷方式，设置测试账号，真实PG/V12、无默认密码/无AI Key仍可手动用 | PARTIAL。已截图首次设置、登录页及登录后状态，后端/PostgreSQL已连接、迁移V12、生成未启用。手动业务仍待验证 |
| G6 业务 | 知识树/草案/版本，任务暂停继续与完成，独立作答/提示/图片，三技能计划复习，分析/JSON导出导入 | PARTIAL。已获技能/节点、手动草案版本1发布截图。21:49截图增加“离线学习目标”和路线版本1保存提示。任务计时/笔记/图片、计划复习、分析及导入导出仍待执行 |
| G7 重开 | 关闭后本程序PG/Java停止，重开原账号/技能/图片保留 | PARTIAL。用户按重开流程回传21:27截图，技能“离线安装测试”仍在。服务退出清理及图片保留尚待核验 |
| G8 升级 | S1快照后安装d3.4，备份目录完整、加密，原数据保持 | NOT_RUN |
| G9 恢复 | 按RECOVERY.txt向全新profile恢复，原密码/技能/图片保留、旧会话失效；错误key/篡改/非空目标拒绝 | NOT_RUN |
| G10 生命周期 | 运行中安装/卸载拒绝；卸载只清软件/快捷方式/注册，学习库与密钥保留；重装可用 | NOT_RUN |
| F1 客体磁盘满 | 只在独立小测试盘与故障克隆，写失败不污染源数据/完成清单；现场和快照编号 | NOT_RUN |
| F2 客体断电 | 只强制关故障VM，在明确备份/迁移/恢复时间点记录异常恢复；不等同宿主硬件断电 | NOT_RUN |

环境报告命令（在客体创建空的证据目录后运行；不需安装Node）：

```powershell
$evidence = Join-Path $env:USERPROFILE 'Desktop/ScarletNexus-Evidence'
New-Item -ItemType Directory -Path $evidence -ErrorAction Stop
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\Audit-WindowsEnvironment.ps1 -Mode Guest -OutputFile "$evidence/environment-before.json"
```

上述RemoteSigned仅影响这一次PowerShell进程，不写全局策略；先核对脚本SHA并检查代码。组策略仍禁止执行时保留错误，按该客体管理流程处理，不改宿主或全局策略。

证据应包括：带虚拟机窗口边框的基线/身份/断网截图、安装版本、系统状态、技能和图片、升级备份、恢复成功、卸载重装，以及错误日志。账户/密码仅测试用，不使用真实学习数据；密钥和完整备份不得放进公开报告。关闭VM前保存客体证据；从独立证据盘或关机后的受控导出取得，验收期间不开可写宿主共享目录。

汇总：通过/失败/未运行分别填写，任何失败不删测试；D3阶段结论由项目验收记录给出。本介质默认全部NOT_RUN。
