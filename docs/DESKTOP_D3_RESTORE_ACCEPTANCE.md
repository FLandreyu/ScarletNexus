# D3 第三批：只恢复到新库

2026-11-17，从2dd37cc继续。GraphFlow无工具，定向检查；保留.env.example/.gitignore，不推送/发布/修改系统功能。

实施前标准：

1. 桌面程序通过明确恢复参数读取本软件升级备份与对应密钥文件，先复制到私有新目标并验证完成清单、路径、SHA及GCM认证；无效密钥/篡改/缺失附件不能启动数据库恢复。
2. 只允许全新空数据目录，原数据/备份/密钥只读；正常启动遇到未完成恢复标记必须拒绝，失败保留现场。无需系统Node/Java/PG。
3. 解密数据库直接送入内置pg_restore，仅新库、单事务、迁移身份、禁止恢复原所有者；保留本软件备份内的对象授权并复核最小权限，清除旧登录会话，恢复原账号和附件，再迁移并启动业务。没有覆盖/自动降级或跨PG主版升级。
4. 真正PG验证：用户/技能/附件与原口令恢复、重开保留、新角色口令/加密密钥轮换、runtime越权仍拒绝；错误密钥/篡改/非空目标拒绝，原库可继续打开。
5. 打包新预览，用真实已打包窗口经恢复入口登录查看旧数据，再跑适用安装升级回归。保留干净系统/真实断电磁盘满门禁；恢复CLI不代表完整恢复GUI或D4完成。

## 实现与原生实证

- 新增restore.cjs和只订阅状态的沙箱恢复窗口。桌面EXE成对接受restore-backup/restore-key-file，目标由原生user-data-dir/learning选择；明确维护入口，无通用路径IPC。先拒绝非空/链接/嵌套目标，建立私有pending目录，复制密文快照后验证路径、完成清单、SHA和GCM。清单内所有文件通过之前不初始化数据库。失败保留目标，普通启动拒绝pending。
- 新目标使用全新角色口令及加密密钥，内置pg_restore以迁移身份、单事务、no-owner、stdin恢复；不恢复全局角色密码、不生成明文dump。恢复前清除初始化宽默认授权，再回放原对象ACL。复核CREATE/迁移表写入/历史修改/备份写入和角色高权限；清除旧Spring登录会话，恢复原账号、业务ID与图片。图片须对应READY数据库引用、SHA与长度，Flyway/服务ready后写complete并最后移除pending。
- 11项Node测试、语法/格式通过；前端格式/类型/14测试/构建通过，保留既有图表chunk提醒。生产Java/Flyway没有改，沿用D2已通过84项的同字节JAR；本批不宣称重跑84。npm审计0（`.runtime-desktop-d3-restore-audit.json`），非全制品扫描。
- 原生Windows：`npm run test:restore --prefix desktop`最终通过，`.runtime-desktop-d3-restore-native-r3.log`退出0。新集群实际恢复原账号/技能/任务和经过服务正规上传的PNG，HTTP读取图片SHA一致；旧会话401、原密码重新登录成功；五项新角色/加密密钥均不同于旧值；runtime建表/修改历史确实拒绝。关闭重开保持，源库也能继续原账号读取图片，源密钥和备份密文SHA不变。错误密钥/篡改在PG初始化前拒绝、非空目标拒绝、未完成目标普通启动拒绝。
- 初次脚本把fetch的ok属性误当函数，`.runtime-desktop-d3-restore-native.log`失败后源库正常停止；修正测试后第二轮真实权限检查拦截新默认授权叠加（`.runtime-desktop-d3-restore-native-r2.log`）。目标未开放业务，失败现场保留，原库未覆盖。清除新库默认授权后第三轮通过，不跳过权限复核。
- `.runtime-desktop-d3-restore-missing.log`：私有副本保留认证有效的数据库密文，移除附件清单声明；真实恢复后查出READY图片缺失，拒绝开放业务并停止新PG，pending阻止普通启动。未修改原备份。不是只检查JSON存在。
- 512MiB真实文件用生产encryptStream/decryptInto做全量认证解密和SHA，增量峰值RSS72757248字节（约69.4MiB），`.runtime-desktop-d3-restore-large.log`。只代表这一规模和本机测量，不代替真实磁盘满/断电。

## 桌面入口与新制品

已打包EXE的恢复窗口/原账号/原技能/原图片/普通重开通过（`.runtime-desktop-d3-restore-ui.log`）。测试使用独立中文profile，移除全局工具PATH、无Node渲染权限；恢复后新窗口可正常登录，旧口令和图片SHA一致，无页面异常。操作步骤见 [DESKTOP_RECOVERY](DESKTOP_RECOVERY.md)。

新安装包 `.runtime/desktop-d3-restore-preview/ScarletNexus-0.1.0-d3.4-windows-x64-setup.exe`：312021752字节，SHA-256 `01932a1d2151ab34c4edaa7f8150b689dce319f124b10bf1465d1c6c0703d7d8`，旁置SHA256SUMS；构建日志 `.runtime-desktop-d3-restore-build.log`。13个应用白名单文件（原9项+restore及3个progress文件），不含测试fixture/随机账号/私有数据。未签名，未发布。

实际安装回归：

```powershell
powershell -NoProfile -File desktop/test/installer-smoke.ps1 -Installer .runtime/desktop-d3-stream-preview/ScarletNexus-0.1.0-d3.3-windows-x64-setup.exe -UpgradeInstaller .runtime/desktop-d3-restore-preview/ScarletNexus-0.1.0-d3.4-windows-x64-setup.exe -BusinessRegression -RestoreFixture .runtime/restore-native-e7d9354e-8b7e-49cd-8df4-25e53d98e043/source/secrets/restore-acceptance.json
```

日志 `.runtime-desktop-d3-restore-installed.log`；最终收尾结果追加在下方。测试资料均在私有.runtime；GraphFlow未提供，未context/plan/index，无推送或Release，用户.env.example/.gitignore保留。

## 剩余门禁

D3仍进行：干净Windows普通账户/中文用户名/断网无全局工具、真实断电/磁盘满与更多容量边界尚未验证；没有擅自启用系统功能或创建系统账户。恢复入口只向新库，不提供覆盖/自动降级/跨PG主版本或自动删除失败现场。密文和SHA/GCM校验不证明来源签名，只恢复可信本软件备份。D4安全扫描、签名/发布未提前进行。

## 最终收尾

`.runtime-desktop-d3-restore-installed.log`退出0：installed_business_regression=passed、installed_restore_entry=passed、installer_roundtrip=passed。已安装d3.4完成六条既有业务旅程，再通过CLI恢复到独立中文配置，原密码登录、原技能与原图片SHA一致，关闭后普通重开成功；卸载/重装仍保留安装验收账号与技能，密钥SHA保持。最终卸载安装/快捷方式/注册项，测试数据保留于 `.runtime/desktop-d3-install-2999ecfc46b04d99ad696707dadc04ad/独立用户`。

加固恢复窗口的显式sandbox/contextIsolation/noNode断言后再次通过（`.runtime-desktop-d3-restore-ui-confirmed.log`）；已查看恢复进度和原技能截图。最终项目所属DesktopApplication/PG/pg_restore/产品进程与卸载项检查无残留，未触碰其他工具进程。13文件白名单与当前源码字节一致，release3.4、SHA256SUMS一致，JAR仍为 `3e160d55a5715c047457dcf411dddcda8130ae8af338bee7fd2a58bbf57fdb6b`；Authenticode NotSigned。

本机第三批交付完成；干净Windows资源问题已向用户询问，不据此关闭D3，不自动推送/发布。
