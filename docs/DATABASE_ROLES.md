# P6 独立迁移与受限运行角色

此方案仅用于新建、空public schema的隔离数据库；不对现有学习库执行。初始化脚本检测到业务表或同名角色会拒绝，避免擅自改写已有权限。既有库的对象所有权、角色迁移和生产切换仍需独立方案与授权。

scarlet_migrator/scarlet_runtime为部署保留角色名。共享集群必须先审核同名角色的归属，不能把可选角色迁移直接用于未经审核的多租户集群。

## 新库步骤

1. 以集群管理员连接指定的新数据库，运行 `psql -X -v ON_ERROR_STOP=1 -d NEW_DATABASE -f scripts/provision-database-roles.sql`。脚本事务创建scarlet_migrator与scarlet_runtime，均无superuser/createdb/createrole/replication/bypassrls，无角色互相继承。仅迁移角色拥有public schema；撤销PUBLIC数据库权限和schema权限，再分别授予CONNECT及必要USAGE。
2. 用交互式psql `\password scarlet_migrator` / `\password scarlet_runtime`或secret manager设置不同密码，不在SQL文件或聊天写密码。不修改本项目用户已有.env.example。
3. Spring运行连接使用DATABASE_USERNAME/PASSWORD，独立Flyway连接使用MIGRATION_USERNAME/PASSWORD。不配置后两项时保持原有本地连接行为。容器可显式合并docker-compose.roles.yml，RUNTIME_PASSWORD/MIGRATOR_PASSWORD必须私有配置；仅写入overlay不代表已经生产部署。
4. Flyway以迁移角色执行V1–V12，表属于迁移角色，运行池使用受限角色。V12在角色存在时授予业务DML、Flyway历史仅SELECT、追加型历史撤销UPDATE/DELETE。catalog_imports等状态型记录仍由不可变触发器限制已确认结果。V1–V11不改。

运行角色无建表、改表、创建角色或切换迁移角色能力。默认权限由迁移角色授予未来表所需DML；未来新增不可变历史表须在对应新迁移中撤销UPDATE/DELETE，新增迁移不得改旧文件。内置触发器仍保护不可变事实。API用户隔离继续由服务owner过滤/复合外键实现，本方案不等同数据库RLS或每用户角色。

应用管理员引导、登录持久会话、用户行锁、技能、笔记、时段、练习与证据需要正常业务DML，因此运行角色不是只读角色。不能撤销所有UPDATE后用跳过测试宣称可用。

## 生产边界

连接URL不得携带user/password参数：驱动可能让URL凭据覆盖独立用户名。验收初轮正是Embedded PG的URL内置user=postgres导致实际连成管理员，去除后通过current_user与对象所有者断言验证；不能仅凭配置文本宣称权限隔离生效。

应用内双连接用于兼容开发及验收；若后端进程同时持有迁移凭据，不能宣称进程整体不具备DDL。正式环境优先用独立迁移作业，完成Flyway后长期运行进程不注入迁移密码并设SPRING_FLYWAY_ENABLED=false；数据库健康查询仍可读取Flyway历史。独立作业编排、既有库切换、备份专用角色、TLS/Secure Cookie、资源与全栈风险门禁仍需完成。初始化/迁移角色不向浏览器暴露，禁止调试日志输出连接属性。

验收采用全新临时真实PostgreSQL，验证迁移角色拥有表、运行角色DDL/改历史/提权失败，同时真正登录并创建技能、学习任务/笔记/时段、提交练习、确认证据与导出快照。具体结果维护于PROGRESS，不能把本文步骤当作生产部署实证。

2026-11-16实证：Windows31单元+角色故事定向通过，Linux容器全量31单元+51真实PG集成=82通过；默认Compose V12兼容桌面/手机12项通过，前端14单元及类型/构建通过。角色overlay只做缺失凭据拒绝/配置解析，不代表已部署专用角色Compose。初轮URL覆盖身份问题已修正，原始失败与最终日志保留在忽略目录，详见PROGRESS。
