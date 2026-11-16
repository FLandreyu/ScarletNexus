# P6 备份专用身份验收

2026-11-16，起点ae02964，先定义标准。GraphFlow未提供，定向检查；保留用户.env.example/.gitignore，既有学习库和旧卷权限不改。

1. 新库provision创建无特权scarlet_backup，仅CONNECT/schema USAGE及现有/未来表、序列SELECT；无成员关系/DDL/DML/文件读取/执行程序权限。沿用现有新库provision边界，不改V1–V12或业务schema。
2. 分离角色项目必须显式提供备份数据库口令文件，不回退管理员；私有pgpass经非loopback地址TCP连接，验证无口令不能登录、current_user和权限。预检在停Web前完成；错误/缺失口令拒绝，不输出秘密或生成备份。
3. 保留全表/文件/UUID/摘要配对验证，失败恢复服务；清单区分只读备份身份与恢复bootstrap身份，不含密码。旧默认本地备份保留兼容且明确legacy-admin模式。
4. 分离角色备份隔离恢复时重建角色/随机新密码、迁移角色拥有对象、恢复权限、独立验证再用runtime启动；验证认证读取/私有图片与权限负向，不把管理员恢复当最小权限恢复。
5. 真实PG、前端格式/类型/14单元/构建、后端全量、TLS旅程、Python安全检查与完整扫描；Ryuk2高不忽略、不称P6通过。更新进度/文档/本地提交不推送；停止仅本轮资源并保留验收卷，无付费/生产或个人数据操作。

## 实际结果

本批备份身份和角色感知恢复已完成，**P6仍因Ryuk2高未通过**。没有修改V1–V12或业务schema，仅按现有新库provision约定扩展角色/默认SELECT；未对旧卷授权。

| 验证 | 实际结果 |
| --- | --- |
| 真实PG权限 | DatabaseRolesIT两个故事验证scarlet_backup当前身份、读全项目、DML/DDL/改角色/文件读取/服务端执行被SQLSTATE42501拒绝；迁移后新表/序列可SELECT而nextval拒绝。真实runtime业务与独立迁移故事仍通过 |
| 后端全量 | Java25 patched Maven隔离构建`verify -Ptestcontainers`，格式/编译/打包、31单元+53真实PG17.12集成=**84项通过**，0失败/错误/跳过，2分50秒，`.runtime-backup-role-backend.log`；Windows原生PG未重跑 |
| 新库/前端/TLS | 新scarlet-nexus-p6-backup-role-acceptance三角色、V12、三服务Healthy且迁移退出0；前端格式/类型/14单元/构建通过，既有chunk/弃用提示保留。真实TLS桌面6项26.2秒、手机6项39.8秒全部通过，无API mock/JS异常/横向溢出；`.runtime-backup-role-compose-r2.log`、`.runtime-backup-role-frontend.log`、`.runtime-backup-role-browser.log` |
| 错误/不安全预检 | 缺失口令、错误口令、临时过量INSERT、临时缺SELECT四种情况均拒绝，输出文件不存在，源Web/backend仍运行且StartedAt未改变，数据库口令未出现在输出中。测试临时权限在finally恢复；Web没有备份/迁移凭据，`.runtime-backup-role-preflight.log`及最终v2复核 |
| 专用身份实际备份 | scarlet_backup、非loopback TCP/password，54表/2附件完整加密备份及认证成功。最终v2文件`.runtime/backup-role-final-v2.snb`，SHA256 1c1432f205676dbcb22f8c53a266c50aa8c74fd8a4731bf43cd2dd655a93354e；文件/数据库密码/密钥只在忽略目录，不入Git |
| v2隔离恢复 | 新网络/卷重建三角色和随机密码；pg_restore以migrator拥有全部表、恢复原V12权限、独立迁移validate。只读身份复算54表内容匹配、2文件匹配、实际pg_stat_activity仅runtime、运行DDL/历史写入拒绝；登录/学习快照/2鉴权图片读取成功，`.runtime-backup-role-final-v2.log`，明确backup_checks_exit=0 |
| 兼容与防降级 | 新工具真实恢复旧Boot4 v1备份，54表/8附件、认证和8图通过，输出明确legacy/管理员身份且不称受限恢复。取ae02964旧读取器验证拒绝新v2且仍认证旧v1，未创建恢复资源，`.runtime-backup-role-reader-compat.log` |
| 脚本/静态 | Linux Python/OpenSSL最终22项通过，增加缺凭据不停止服务、trust拒绝/清理、pgpass转义、v1不能降级角色、v2必需身份信息测试。Python编译、CI YAML/静态锁与Git diff通过；远端CI未运行 |
| 运维/扫描 | 串行TLS正反/主机名/明文/Secure Cookie/CSRF/退出/限流、资源/monitor/只读附件审计与SSE/头清洗通过。最终`.runtime/security-backup-role-final`complete=true、退出2，仍Ryuk2高/0严重、父层合计43次；后端2中/前端1中、npm生产0/全部5 moderate。无ignore/VEX，作业和后端同一镜像f1341870... |

首轮私有启动脚本CRLF被Linux sh拒绝，换LF后成功，未安装服务；第一次运行探测/扫描与浏览器重建交叠，末尾exec137/找不到运行中Web，原日志保留，不作为完整通过证据。串行探测、备份/恢复及扫描重跑完成（`.runtime-backup-role-final.log`），Python最终更改后v2/旧v1实际恢复与22测试再复核。初期内部v1角色草案不作为交付存档，新读取器明确拒绝这种降级标记。

收尾仅本轮Compose down，四服务/临时客户端/恢复资源已移除，ps -a空，新PG/附件两卷保留，旧卷/个人学习数据不改。用户.env.example/.gitignore不入提交，无推送/生产部署/付费模型/全局服务或信任库修改。

## 后续与边界

新角色初始化、双密码文件和恢复方式见DATABASE_ROLES/BACKUP_RESTORE；默认legacy-admin模式仅兼容本地开发，不冒充受限备份。Docker/宿主/密钥文件权限仍需保护，本批不是生产灾备、NTFS ACL或密码学审计；没有全局角色/证书/供应商配置归档，不涵盖外部SQL写入者的全局一致性。下一步汇总P6最终门禁并继续跟踪Ryuk2高及中低/未知风险，既有库/真实生产切换仍需另行确认。
