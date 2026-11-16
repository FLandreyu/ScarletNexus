# 本地运维与生产准备边界

日常启动见 README。本地 Compose 仅 loopback，持久卷 `postgres-data`；无自动数据清理。数据库连接密码必填于忽略的 .env，修改密码不会自动更新已有卷中的角色密码，需独立安全迁移，不能删卷绕过。

健康：`/actuator/health/liveness` 检查进程，`readiness` 包含数据库；`/api/v1/system/status` 检查 MyBatis/Flyway 查询。日志看 requestId，排错不要公开 `.env` 或完整连接配置。后端启动必须 Flyway 成功，不自动 baseline 或忽略校验失败。

停止：`docker compose down` 保留卷；重启：`docker compose restart`；查看日志：`docker compose logs --tail=100 backend`。不要执行 `down -v` 或删除数据目录。隔离测试使用临时测试集群，绝不指向用户数据库。

当前 Docker 配置是开发环境，非生产方案。P6 必须补齐：域名/TLS、Secure Cookie、CSP 与 SSE 超时复核、镜像 digest、非root和资源限制、最小数据库角色、外部存储权限、脱敏轮转日志、健康告警、备份加密及隔离恢复演练、JSON导出/导入、安全扫描和回归。生产部署/公网暴露/购买资源需用户明确确认。

## 本机隔离WSL验收运行时（2026-11-15）

Ubuntu已有WSL2，原生Windows只有失效Docker服务。依据[Docker官方静态二进制文档](https://docs.docker.com/engine/install/binaries/)下载Docker29.9.3，并用专用`/root/.cache/scarlet-nexus-p0-runtime`存放bin/config/data/exec及Unix socket；Compose5.7.1和Buildx0.39.0下载后校验官方SHA256，iptables依赖仅dpkg-deb提取，无apt安装/系统服务/全局PATH修改。工具包在项目.tools，运行辅助脚本在.runtime，均不入Git。

本机复测入口（项目实际Linux路径）：`wsl -d Ubuntu --cd /tmp -- sh /mnt/d/MyStudy/幽默项目/ScarletNexus/.runtime/docker.sh info`。Compose入口将末尾改为`.runtime/compose.sh ps`；验收项目名`scarlet-nexus-p0-acceptance`，本机入口127.0.0.1:8088，密码保存在忽略的.runtime/compose-acceptance.env，不输出到聊天/仓库。该项目数据是验收数据，正式使用另建默认Compose项目和用户自行配置的凭据。

WSL显式`--cd /tmp`避免本机Unicode当前目录导致的WSL启动E_UNEXPECTED。发行版初始Stopped，本轮曾仅重启自己启动的Ubuntu以排错，未触碰docker-desktop或修改WSL持久设置。WSL NAT不能使用Windows loopback代理，.runtime中的标准输入输出转发器连接现有127.0.0.1代理；Linux代理只监听127.0.0.1，Docker只开放Unix socket。

在Linux Docker中运行`sh scripts/verify-testcontainers-in-docker.sh`可复用受测入口；非默认socket通过DOCKER_HOST传入，MAVEN_REPOSITORY可选缓存目录。它向测试容器挂载socket（仅限受信任测试），保持Ryuk启用，不用于运行不可信代码。停止验收Compose用其`down`且保留卷；不要清理整个Docker数据目录或其他项目。

P6第二批已完成真实加密备份与隔离恢复：54张表/10个附件校验一致，新实例登录、学习快照与10张鉴权图片读取通过。命令、口令保管、停写窗口和生产边界见[BACKUP_RESTORE](BACKUP_RESTORE.md)。定时备份、监控告警与生产恢复方案仍待后续；不自动部署或清理源数据。
