# 开发进度

更新：2026-11-15（Asia/Shanghai）。正式需求：根目录总纲 v1.0。

## 当前阶段

P0 工程和本地功能验证已落地，整体状态为 **待环境验收**；P1–P6 未开始。剩余门禁是 Docker 引擎下的容器构建 / 干净卷启动和 Testcontainers；不将其标记完成，不跨过门禁。

## 初始环境检查

- 初始目录只有总纲（26466 bytes），不是 Git 仓库，无既有代码/用户数据。
- Windows11 amd64；Node25.2.1 / npm11.6.2；Maven3.9.16；系统Java25.0.2；发现 D:/Java21 的Java21.0.12，项目使用21。
- 无可用Docker / psql / 本地PostgreSQL服务；后续检查发现遗留com.docker.service处于Stopped，但其指向的C:/Program Files/Docker/Docker目录和可执行文件均不存在。未尝试启动残留服务或更改系统配置。
- 下载Node24.22.3便携版到.tools（官方SHA256校验），解决Vitest5不支持系统Node25的问题；下载独立Compose5.7.1并校验官方SHA256，仅用于配置解析，没有Docker引擎。
- Git已有身份配置；本地初始化与提交由总纲授权，不推送。
- GraphFlow MCP未提供，无法context/plan/index；已记录ADR-007。

## 已完成

- 阅读总纲；形成 PRD、架构、目标ERD/数据库约束、API合同、AI/安全、路线、测试与运维设计。
- 建立 AGENTS.md，记录默认决策和验证边界。
- 初始化本地Git（main），保留原始总纲；不创建远端、不推送。
- Vue3/TypeScript/Vite/Router/Pinia/Element Plus/Tailwind工程；状态页真实查询后端/PG，加载、故障、请求编号、显式重试、桌面/手机布局。
- Java21/Spring Boot3.5/MyBatis/Flyway/Bean Validation/Security/Actuator/OpenAPI工程；系统状态、存活/就绪、统一安全错误、请求ID；非公开接口默认拒绝，CSRF开启，无默认账号。
- V1迁移：users及唯一性/规范化/状态约束，audit_events及JSONB/外键/索引/追加触发器。无用户或学习数据种子。
- Compose/Nginx/Dockerfiles：同源入口仅loopback、DB持久卷、健康依赖、无默认密码；实际镜像标签远端manifest均存在。
- Windows验证和P0临时真实PG预览脚本；npm锁文件；CI定义覆盖前端质量、后端Testcontainers、Compose与浏览器烟测。

## 正在执行

- 当前无业务功能开发中；等待Docker环境关闭P0门禁。工程已形成可回滚本地检查点，详见P0_ACCEPTANCE。

## 已执行验证

| 命令 / 范围 | 实际结果 |
| --- | --- |
| Java21 `mvn -B spotless:apply verify` | 后端编译/打包/格式通过；2项JUnit单元+5项真实PG17.11集成全部通过，无跳过 |
| `scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64` | 最终端到端脚本退出0：npm ci、格式、类型、4项前端单测、构建和Maven verify全部通过 |
| `npm run typecheck` / `npm run build`（隔离Node24） | 通过；产物JS约147.74kB、CSS约41.72kB（未压缩） |
| `npm test` | 4项通过：真实合同解析、503后重试、网络失败、非法响应；单元环境允许stub fetch |
| `npm run test:e2e`（真实P0Preview后端） | 桌面+390px手机共2项通过，无API拦截；后端与PG均实际连接，刷新、无水平溢出、无页面JS异常；两张截图已逐张查看 |
| 独立Compose `config --quiet`（临时仅验证密码） | 通过；缺少密码被明确拒绝；未保存.env |
| Docker Hub manifest HEAD | Maven / Temurin / Node / Nginx / PostgreSQL所用5个标签均返回200；这不代表镜像构建/运行通过 |
| `npm audit --omit=dev` | 生产依赖0漏洞 |
| `npm audit` | 5项moderate（开发依赖链Vue Test Utils/js-beautify/glob/minimatch/editorconfig），0 high/critical；ADR-008记录，未强制跨版本覆盖 |
| Docker构建 / Testcontainers / 远端CI | 未运行：本机无引擎，也未配置远端仓库；CI仅完成定义 |

集成测试真实覆盖：空库V1迁移、validate与重复migrate幂等、规范化email唯一约束、非法状态、审计JSON约束/不可改删/外键保护、未登录401/CSRF403/Basic无效、requestId、应用实例关闭后重新启动保留记录、实际OpenAPI只含已实现端点、停PG后status/readiness503且liveness200、不泄漏连接信息。

首轮问题均已定位并修复：SpringApplication默认属性优先级不足导致新实例连错库（改用显式启动参数）；健康响应包含groups（改为检查无敏感components/details）；Element Plus样式从页面移至入口解决测试CSS加载；使用延迟响应验证Vue加载状态；系统Node25不受Vitest支持（隔离Node24）；Windows临时文件锁导致一次构建失败后复跑通过；PowerShell5.1将npm stderr警告视为异常（检查真实退出码，保留告警输出）。

## 阻塞与下一步

- **P0剩余环境门禁**：在已有可用Docker引擎的环境运行 `mvn -B verify -Ptestcontainers`，再按README执行Compose空卷启动、网关健康、重启和浏览器回归；无需重复需求设计。安装/启用系统级虚拟化或Docker Desktop不在本轮自动变更范围。
- 本机可按README使用临时真实PG预览；它不保留用户数据，不能作为长期使用部署。截图和日志在忽略目录，正式证据摘要进仓库。
- 开发依赖5项中等级告警待上游兼容修复；生产依赖审计无发现不代表全栈安全审计完成。生产前重新评估Java依赖和容器漏洞。
- GraphFlow恢复可用后执行context/plan/index，当前不能声称已索引。
- P0全部门禁通过后进入P1：管理员安全引导/数据库会话/CSRF和限流 → 技能归档CRUD → 共享稳定节点/分类树/依赖DAG → 搜索与移动端 → 持久化和双用户隔离回归。
- 暂不调用付费AI，不生产部署，不自动安装系统服务，不执行不可逆数据操作。
