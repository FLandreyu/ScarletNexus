# P6 独立迁移与受限运行角色

此方案仅用于新建、空public schema的隔离数据库；不对现有学习库执行。初始化脚本检测到业务表或同名角色会拒绝，避免擅自改写已有权限。既有库的对象所有权、角色迁移和生产切换仍需独立方案与授权。

scarlet_migrator/scarlet_runtime/scarlet_backup为部署保留角色名。共享集群必须先审核同名角色的归属，不能把可选角色迁移直接用于未经审核的多租户集群。

## 新库步骤

1. 以集群管理员连接指定的新数据库，运行 `psql -X -v ON_ERROR_STOP=1 -d NEW_DATABASE -f scripts/provision-database-roles.sql`。脚本事务创建scarlet_migrator、scarlet_runtime与scarlet_backup，均无superuser/createdb/createrole/replication/bypassrls，无成员关系。仅迁移角色拥有public schema；撤销PUBLIC数据库权限和schema权限，再分别授予CONNECT及必要USAGE。
2. 用交互式psql `\password scarlet_migrator` / `\password scarlet_runtime` / `\password scarlet_backup`或secret manager设置不同密码，不在SQL文件或聊天写密码。不修改本项目用户已有.env.example。备份密码单独保存私有文件，Web/migrate容器均不注入该值。
3. Spring运行连接使用DATABASE_USERNAME/PASSWORD，独立Flyway连接使用MIGRATION_USERNAME/PASSWORD。不配置后两项时保持原有本地连接行为。容器可显式合并docker-compose.roles.yml，RUNTIME_PASSWORD/MIGRATOR_PASSWORD必须私有配置；仅写入overlay不代表已经生产部署。
4. Flyway以迁移角色执行V1–V12，表属于迁移角色，运行池使用受限角色。V12在角色存在时授予业务DML、Flyway历史仅SELECT、追加型历史撤销UPDATE/DELETE。catalog_imports等状态型记录仍由不可变触发器限制已确认结果。V1–V11不改。

运行角色无建表、改表、创建角色或切换迁移角色能力。默认权限由迁移角色授予未来表所需DML；未来新增不可变历史表须在对应新迁移中撤销UPDATE/DELETE，新增迁移不得改旧文件。内置触发器仍保护不可变事实。API用户隔离继续由服务owner过滤/复合外键实现，本方案不等同数据库RLS或每用户角色。

备份角色仅获迁移角色未来表和序列的SELECT（角色provision先于Flyway，因此涵盖全部现有业务对象及历史表），无USAGE/UPDATE序列权限、不允许nextval，无DML/DDL/文件读取/服务端执行权限。它可以读取全项目各用户数据，密码和产物须按完整私有备份保护；不是API用户身份。新角色只在新空库初始化，不通过修改已执行V1–V12或自动给旧库授权来启用。恢复时按v2清单显式重建角色与权限，见BACKUP_RESTORE/P6_BACKUP_ROLE_ACCEPTANCE。

应用管理员引导、登录持久会话、用户行锁、技能、笔记、时段、练习与证据需要正常业务DML，因此运行角色不是只读角色。不能撤销所有UPDATE后用跳过测试宣称可用。

## 独立迁移作业（新库）

`docker-compose.migrate.yml`提供一次性migrate服务，使用同一应用JAR的DatabaseMigration入口，不启动Spring/Web/管理员引导。只接受明确的scarlet_migrator身份与MIGRATION_PASSWORD，URL不接受查询参数或内嵌凭据；此版本不提供通过URL设置生产SSL选项的能力。迁移/validate失败退出1，禁clean与baseline；默认本地双连接方式保持兼容。

1. 私有.env先配置数据库、RUNTIME_PASSWORD与MIGRATOR_PASSWORD，两个角色密码不同；不要提交。合并base与migrate文件，先只`up -d --wait db`。
2. 仅在新空库，按前述脚本provision角色并交互设置密码；不对现有卷运行初始化或权限切换。
3. `docker compose -f docker-compose.yml -f docker-compose.migrate.yml up --build -d --wait`。backend依赖migrate成功退出；失败时必须处理迁移原因，不能关闭依赖检查强行启动。TLS可继续额外合并docker-compose.tls.yml。
4. 检查migrate退出0、后端Healthy及/system版本12。新迁移发布前再次执行完整up让作业运行，不能只restart旧后端冒充已迁移。

不要同时合并旧docker-compose.roles.yml。新overlay后端只注入运行密码，移除迁移用户名/密码并设SPRING_FLYWAY_ENABLED=false；迁移容器只读根、无附件卷/公开端口、有资源上限，不常驻。Compose需要支持!reset的版本（本地5.7.1实测）。Docker管理员与私有.env仍可读取迁移配置，此方案只消除长期Web进程的迁移凭据，不代替secret manager或宿主权限控制。实际门禁见P6_MIGRATION_JOB_ACCEPTANCE。

## 生产边界（续）

连接URL不得携带user/password参数：驱动可能让URL凭据覆盖独立用户名。验收初轮正是Embedded PG的URL内置user=postgres导致实际连成管理员，去除后通过current_user与对象所有者断言验证；不能仅凭配置文本宣称权限隔离生效。

应用内双连接用于兼容开发及验收；若后端进程同时持有迁移凭据，不能宣称进程整体不具备DDL。正式环境优先用独立迁移作业，完成Flyway后长期运行进程不注入迁移密码并设SPRING_FLYWAY_ENABLED=false；数据库健康查询仍可读取Flyway历史。独立作业和专用备份角色已有本地新库实证，既有库切换及整体生产门禁仍未完成；TLS/资源/备份的本机证据不代表生产部署。角色密码不向浏览器暴露，禁止调试日志输出连接属性。

验收采用全新临时真实PostgreSQL，验证迁移角色拥有表、运行角色DDL/改历史/提权失败，同时真正登录并创建技能、学习任务/笔记/时段、提交练习、确认证据与导出快照。具体结果维护于PROGRESS，不能把本文步骤当作生产部署实证。

2026-11-16实证：Windows31单元+角色故事定向通过，Linux容器全量31单元+51真实PG集成=82通过；默认Compose V12兼容桌面/手机12项通过，前端14单元及类型/构建通过。角色overlay只做缺失凭据拒绝/配置解析，不代表已部署专用角色Compose。初轮URL覆盖身份问题已修正，原始失败与最终日志保留在忽略目录，详见PROGRESS。
