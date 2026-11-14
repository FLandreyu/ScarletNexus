# 本地运维与生产准备边界

日常启动见 README。本地 Compose 仅 loopback，持久卷 `postgres-data`；无自动数据清理。数据库连接密码必填于忽略的 .env，修改密码不会自动更新已有卷中的角色密码，需独立安全迁移，不能删卷绕过。

健康：`/actuator/health/liveness` 检查进程，`readiness` 包含数据库；`/api/v1/system/status` 检查 MyBatis/Flyway 查询。日志看 requestId，排错不要公开 `.env` 或完整连接配置。后端启动必须 Flyway 成功，不自动 baseline 或忽略校验失败。

停止：`docker compose down` 保留卷；重启：`docker compose restart`；查看日志：`docker compose logs --tail=100 backend`。不要执行 `down -v` 或删除数据目录。隔离测试使用临时测试集群，绝不指向用户数据库。

当前 Docker 配置是开发环境，非生产方案。P6 必须补齐：域名/TLS、Secure Cookie、CSP 与 SSE 超时复核、镜像 digest、非root和资源限制、最小数据库角色、外部存储权限、脱敏轮转日志、健康告警、备份加密及隔离恢复演练、JSON导出/导入、安全扫描和回归。生产部署/公网暴露/购买资源需用户明确确认。

恢复演练计划：先备份数据库与附件清单 → 新隔离数据库恢复 → 校验 schema、数量、外键、UUID、文件checksum → 应用读路径验收 → 记录结果。未演练前不得声称备份可恢复。P0 未包含真实学习数据，不创建定时备份任务。
