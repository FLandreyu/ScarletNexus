# 加密备份与隔离恢复

范围：应用PostgreSQL全部public表与私有附件卷。依赖Linux Docker宿主（本机WSL Ubuntu）、Python≥3.11、OpenSSL与已有本地PG/backend镜像。原生Windows Python、跨PG版本与生产恢复未验收。恢复只生成新资源，不接受覆盖目标。

最新角色感知验收见[P6_BACKUP_ROLE_ACCEPTANCE](P6_BACKUP_ROLE_ACCEPTANCE.md)：分离角色项目使用scarlet_backup/TCP口令备份，v2清单恢复时重建三种角色；旧默认本地v1仍明确标为legacy-admin，不代表最小权限。下文历史验收保留。

## 操作

先暂停/结束计时、处理未结束AI运行，并确保没有其他SQL写入者。备份必须找到命名Compose项目各一个运行中的db/backend/web；维护窗口内暂时停止该项目web/backend，finally恢复被停止服务。失败后仍应检查源服务健康。

口令使用随机高熵、单行32..1024字节私有文件，另存且不与备份共用存储。建议Linux文件0600/目录0700；Windows挂载的chmod不替代NTFS ACL。不要将口令粘贴到命令中，丢失口令不能恢复。配置Docker PATH/DOCKER_HOST后执行：

```sh
python3 scripts/backup_restore.py backup --project YOUR_PROJECT --password-file /private/backup.key --output /private/new-backup.snb
python3 scripts/backup_restore.py verify --password-file /private/backup.key --input /private/new-backup.snb
python3 scripts/backup_restore.py drill --password-file /private/backup.key --input /private/new-backup.snb --login-email ACCEPTANCE_EMAIL --login-password-file /private/account.password
python3 -m unittest discover -s scripts -p test_backup_restore.py -v
```

输出采用独占创建，不覆盖旧备份。临时明文目录私有并自动清理；断电/强制杀进程后需检查残余临时目录和资源。只恢复可信备份，数据库转储和镜像不是不可信代码沙箱。

使用独立角色的新库时，backup必须追加`--database-password-file /private/database-backup.password`。这是scarlet_backup的数据库密码，和加密用`--password-file`不同；均为私有单行32..1024字节文件，不把值写入命令行或Web容器。缺失/错误口令、trust认证或过量/不足权限会在停服务前拒绝，不回退管理员。默认本地未采用分离角色时，旧备份保留兼容并标明legacy-admin；正式权限方案应使用分离身份。

客户端使用源PG相同镜像内容ID，只读根/无cap/短生命周期、仅挂私有0600 pgpass，通过源DB非loopback地址TCP连接并验证无密码不能登录。Docker管理员仍有容器和卷访问能力，数据库只读身份不消除该宿主边界。备份仍须排除其他SQL写入者；pg_dump的单次一致性不保证多个清单查询及附件文件的全局一致性。

角色感知清单为`scarlet-nexus-full-backup/v2`，加密信封/算法保持原版本。旧脚本拒绝v2，避免忽略角色信息后当管理员恢复；新脚本支持旧v1。v2恢复在新网络/卷重新创建三角色及随机新密码，以migrator拥有对象、恢复V12权限、独立validate后以runtime且禁Flyway启动，并核对实际连接用户/所有权、只读全表摘要及DDL/历史写入拒绝。归档不包含角色密码，恢复不覆盖源库；当前验证同版本V12布局，现有库切换与生产灾备另行确认。

官方语义依据：[pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html)、[pgpass文件权限与转义](https://www.postgresql.org/docs/17/libpq-pgpass.html)、[SELECT及序列权限](https://www.postgresql.org/docs/17/ddl-priv.html)。

框架升级兼容性演练可在`drill`显式追加`--backend-image sha256:...`，仅接受已存在本地的不可变image ID。数据库仍使用备份原PG镜像，恢复只写新生成的隔离卷；输出同时记录备份后端与受测后端ID，原备份认证内容不改。默认不覆盖时仍使用备份所记录后端。此选项不适用于backup/verify，不接受浮动标签，不触碰源库。

## 保证与边界

- pg_dump自定义格式+附件tar+manifest共同封装。每张表行数/UTC排序内容SHA-256、附件名/大小/SHA-256及镜像内容ID均入清单。READY/ARCHIVED图片须与数据库checksum一致；孤立文件也保留，不自动删除。
- AES-256-CBC/OpenSSL PBKDF2-SHA256（200000次、随机盐）加密；独立随机盐的PBKDF2密钥对版本头与密文做HMAC-SHA256，encrypt-then-MAC，恒时比较后才解密。口令不出现在命令行内容。格式未经过独立密码学审计，不代表生产安全验收。
- verify认证、校验转储/附件摘要与tar路径/类型，不创建恢复资源。错误口令/篡改在解密前拒绝，路径穿越、符号链接、重复条目拒绝。
- drill只创建随机scarlet-restore-*新内部网络、两个新卷、PG和backend容器；不发布端口、不挂载源卷、不传模型Key。Linux宿主访问内部桥地址，HTTP禁用环境代理。pg_restore --exit-on-error后比对全部表与UUID/行数，恢复附件后比对所有文件与DB引用；新实例须通过Flyway/readiness。提供账户时实际登录、读取学习快照、下载该账户全部READY图片并校验。
- finally仅清理本次创建的目标资源，不删源卷和备份。备份含数据库身份哈希、会话和私有文本，不能公开；不含宿主配置、Provider Key、TLS材料、镜像二进制、PG集群全局角色。镜像按内容ID须已存在。生产灾备还需这些部署材料、角色权限与撤销旧会话方案，另行确认。

## 2026-11-16实际验收

独立项目scarlet-nexus-p0-acceptance，54张表、10个附件：备份/认证/全表内容和UUID/全文件/DB引用校验通过；新实例readiness、原账户登录、学习快照及10张鉴权PNG校验通过。3项离线格式安全测试通过，不代替真实PG演练。首轮内部网络无端口映射导致探测失败，改为宿主直接访问内部地址后通过。

证据`.runtime-p6-backup-verified.log`，密文`.runtime/p6-full-backup-verified.snb`，口令另存本地不提交。CI加入同样演练，远端未运行。P6监控、日志轮转与生产TLS/权限/资源/漏洞复核仍待完成。
