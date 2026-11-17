# 桌面升级备份恢复到新库

适用于 d3.4 及之后支持该入口的 Windows 预览版。本功能只恢复到全新的独立配置，不替换原库；原桌面快捷方式仍打开原库。当前内部升级备份不是 Web SNBACK，也不是知识目录/学习快照 JSON。

## 准备

保留这两项，放在你自己的私有位置：

- 一份完整的学习目录 backups/随机编号：必须有 manifest.json、database.dump.enc 和清单中的全部 attachments/*.enc。
- 生成该备份时学习目录 secrets/database.json；它包含解密密钥及数据库凭证，不要上传或发给他人。复制备份时也要保留对应密钥。密钥丢失不能用登录密码代替解密。

默认原学习目录为 `%APPDATA%/ScarletNexus/learning`，但自定义配置根的用户应按实际目录查找。仅使用你信任、由本软件生成并自行保管的备份；数据库归档恢复会执行SQL，认证与摘要验证不能证明发布者来源。参考 [PostgreSQL pg_restore说明](https://www.postgresql.org/docs/17/app-pgrestore.html)。不需要另外安装 Node、Java、PostgreSQL或Docker。

## 执行一次恢复

先关闭正在恢复目标中的 Scarlet Nexus。可从桌面快捷方式属性的“目标”取得exe路径。打开 PowerShell，将前三个路径改成你的实际路径：

```powershell
$exe = 'C:/Users/你的用户名/AppData/Local/Programs/Scarlet Nexus/Scarlet Nexus.exe'
$backup = 'D:/我的私有备份/一份完整的升级备份目录'
$keyFile = 'D:/我的私有备份/对应的database.json'
$newProfile = Join-Path $env:LOCALAPPDATA ('ScarletNexus-Recovered-' + [guid]::NewGuid().ToString('N'))
& $exe "--user-data-dir=$newProfile" "--restore-backup=$backup" "--restore-key-file=$keyFile"
```

软件显示恢复进度，校验清单内全部密文SHA、GCM认证标签和明文SHA后，使用内置工具恢复到独立新库。恢复角色和加密密钥重新生成，保留备份时的账号/密码与业务数据，旧登录会话清除。READY图片须与数据库存储键、校验和及长度吻合；恢复授权必须保留历史表限制；所有检查和迁移通过后才打开登录页面。清单超过16MiB、密钥文件超过64KiB会拒绝。

新配置根由Electron创建，实际学习库在 `$newProfile/learning`。既有非空学习目录、原备份树和其他现有受管理数据树都不能作为目标。请不要把 `$newProfile` 设置为原学习目录。

## 以后打开恢复库

恢复仅执行一次。后续不带恢复参数：

```powershell
& $exe "--user-data-dir=$newProfile"
```

使用备份时的原账号密码登录，核对技能、学习记录与图片。如果要在桌面固定入口，可在 Windows 新建一个**单独的**快捷方式，目标为上述exe并追加 `--user-data-dir="新配置完整路径"`；保留原快捷方式。完成核对之前，不删除原库、备份或密钥。

新库未来生成的升级备份使用新库自己的 `learning/secrets/database.json`，应另外保留，原备份继续使用原密钥。

## 恢复失败

软件会保留新目标及 restore.pending.json，阻止把未完成库正常打开。不要手动删除此标记来绕过检查，也不要覆盖原库。保留日志与该目录，核对备份/密钥后选另一个全新 `$newProfile` 重试。完成恢复有 restore.completed.json 且pending标记移除。

只复制了 database.dump.enc、缺少清单或图片、密钥不配对、文件篡改、权限不符合要求、归档来自不支持的版本等情况都会拒绝。没有自动降级、跨 PostgreSQL 主版本升级、覆盖已有库或自动删除失败现场。断电/磁盘满、干净普通用户Windows环境仍在D3剩余验收范围，不用当前本机结果代替。
