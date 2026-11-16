# P6 独立迁移作业验收

2026-11-16。先定义标准，起点6d88582；仅新建隔离数据库，原用户.env.example/.gitignore与学习卷不变。GraphFlow不可用，定向检查。

1. 使用同一已打包应用中的独立入口执行Flyway，不启动Spring/Web/引导用户/AI；显式迁移角色及私有口令，禁clean/baseline，不接受URL凭据。失败非0、输出不含口令。
2. 新Compose overlay在迁移成功后才允许后端启动；后端禁Flyway、只持运行身份，实际容器环境无迁移凭据。迁移容器无公开端口/附件挂载、只读根、有限资源，成功退出不常驻。不自动初始化现有库角色。
3. 真实PG验证错误口令失败且空库不变、同一进程入口重复迁移幂等、迁移所有权、运行DDL/提权/历史改写拒绝及完整业务故事；真实Compose/浏览器验证。保留格式、前端/后端/脚本及完整扫描证据，未关闭Ryuk两高不标P6通过。
4. 保留父层/有效产物的原始扫描；Ryuk新SDK兼容试验仍仅在忽略目录，未替换原受测清理工具。记录API变化和上游无兼容修复的边界，不做ignore/VEX，不伪称告警已修。
5. 更新进度/决策/复现、本地提交不推送；仅停止本轮隔离资源、保留数据卷，不生产部署/付费调用/更换用户库权限。

## 实际结果

本批完成独立迁移作业，整体P6仍未完成；Ryuk调查没有替换受测工具或关闭告警，详见P6_RYUK_ACCEPTANCE。

| 范围 | 实际结果 |
| --- | --- |
| Java25 patched Maven最终全量 | 容器内部独立构建目录`verify -Ptestcontainers`，格式/编译/打包、**31单元+53真实PG17.12集成=84项通过**，0失败/错误/跳过，3分01秒；`.runtime-job-backend-isolated.log` |
| 新独立迁移故事 | 新空PG provision角色后，独立JVM以实际Boot PropertiesLauncher执行同一入口；错误口令返回1且库无业务表，正确迁移及第二次幂等运行都返回0，作业未创建用户。运行Spring禁Flyway且无Flyway bean，仅运行密码；验证current_user/owner、DDL/提权/历史改写拒绝、真实登录/技能/学习/练习/证据/快照读取。DatabaseRolesIT两故事均通过 |
| 首轮共享目录失败 | 原runner31单元通过，53集成出现3错误，IdentityCatalogIT重启时PlanningGuard.class读取缺失，随后检查文件存在，原因未证实；`.runtime-job-backend.log`保留（9分06秒）。未修改业务断言/跳过测试；runner改只读源快照、容器内编译，仅导出JAR与报告后全量通过，不归咎于编辑器 |
| 新隔离Compose | scarlet-nexus-p6-job-acceptance新建两卷并provision，不触碰旧卷；作业完成退出0，后端runtime身份、环境无迁移用户名/密码，表owner为migrator，迁移容器只读根/无公开端口；`.runtime-job-compose.log`、`.runtime-job-shared-image.log` |
| 真正的编排失败门禁 | 停止本轮Web/backend，用错误迁移配置执行完整up，退出1且backend不运行；finally恢复正确配置后健康。共享镜像改动前后均验证，`.runtime-job-negative.log`、`.runtime-job-final-checks.log`。不是只执行单个失败命令后推测依赖有效 |
| 产物一致性 | 首轮migrate/backend分别build产生不同image ID，扫描正确拒绝（`.runtime-job-scan.log`）。修正显式共用`${COMPOSE_PROJECT_NAME}-backend`、migrate禁止拉取，最终两者实际ID为360459d907dc...；扫描记录migrationImage并核对已退出0、环境隔离，缺少一致性不能宣称已覆盖 |
| 前端 | 受测Node容器format/typecheck/14单元/build通过，`.runtime-job-frontend.log`，保留既有chunk/弃用提示，无新增依赖 |
| 新角色TLS全旅程 | 桌面6项37.1秒、手机6项36.3秒全通过，无API mock/JS异常/横向溢出；`.runtime-job-browser.log`。未重跑默认HTTP完整12项 |
| TLS/运维/脚本 | CA/主机名正反、明文拒绝、Secure Cookie/CSRF/退出/限流，资源/monitor与只读附件审计、延迟SSE/代理头清洗通过；Linux Python/OpenSSL17项通过，`.runtime-job-final-checks.log`。CI YAML解析/Git diff通过，CI增加overlay配置解析步骤，远端未运行 |
| 最终完整扫描 | `.runtime/security-job-final`complete=true，明确scanner_exit=2；门禁仍Ryuk2高、0严重，父层合计43次；实际后端2中、前端1中，npm生产0/全部5 moderate。migrationImage与后端一致，共用同一实际JAR/SBOM扫描，不重复计算同一产物；无ignore/VEX/未修项过滤 |
| 收尾 | 本轮Compose四服务down，ps -a空、Testcontainers无残留；新PG/附件两卷保留，旧验收卷/学习数据未改。无推送/生产部署/付费模型或全局服务修改，秘密/SDK候选/样例只在忽略目录 |

## 复现与后续

新库步骤见DATABASE_ROLES：先配置私有角色密码、只启动db并provision，再合并docker-compose.migrate.yml执行完整up。backend与migrate共享构建镜像；只restart后端不代替迁移发布。`sh scripts/verify-testcontainers-in-docker.sh`自动执行包含进程隔离迁移在内的全量真实PG故事，源目录只读，构建产物/报告导出backend/target。

新overlay仅用于新库、需要支持!reset的Compose，本地5.7.1已验证；尚未进行Windows原生PG、生产权限切换/证书续期、专用备份角色、真实模型或生产部署。本次未重新演练新角色库的备份权限，不把上一批默认身份备份验收当作本批备份角色通过。下一步明确备份身份/权限并验证恢复，再继续P6最终门禁；Ryuk2高继续单独跟踪。
