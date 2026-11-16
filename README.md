# Scarlet Nexus

Windows桌面版已开始[D1开发](desktop/README.md)：独立Electron窗口及本机网关可连接真实业务界面，已验证登录、分析、单实例和退出清理。目前仍需显式开发后端；内置Java/PostgreSQL及安装包尚未交付，不能当作可独立下载安装的成品。完整阶段见[DESKTOP_PLAN](docs/DESKTOP_PLAN.md)。

**P0–P6核心Web版本已验收通过（2026-11-17）**，关闭依据和保留局限见[P6_CLOSEOUT](docs/P6_CLOSEOUT.md)。远端六项CI作业、HTTP/TLS桌面手机旅程和既定安全门禁通过。实际生产部署与Windows桌面安装包尚未完成；当前需按下述Web部署方式使用，不能下载后双击图标直接使用。

当前总验收结论见[P6最终复核矩阵](docs/P6_FINAL_REVIEW.md)：本地核心流程已有实证，HTTP/TLS桌面与手机均覆盖；第十二批实际产物扫描及离线复核为0高/严重，安全扫描门禁已通过，中低/未知风险和生产前验收仍保留。可用`python3 scripts/review_security_report.py --report .runtime/YOUR_REPORT`离线复核扫描证据，P6关闭另有逐项证据，扫描通过不等于生产安全通过。

P6已迁移Spring Boot4.1.2，保留Java25及严格JSON业务解析；原Spring严重告警已消除，Ryuk通过固定源码上的[官方拆分SDK适配](docs/P6_RYUK_SDK_MIGRATION.md)消除原两项高告警，仍有1项中等级。该工具是可审查的本地补丁，并非上游新发行版；没有ignore/VEX或禁用清理。完整来源与计数见[SUPPLY_CHAIN_REVIEW](docs/SUPPLY_CHAIN_REVIEW.md)，框架迁移见[P6_BOOT4_ACCEPTANCE](docs/P6_BOOT4_ACCEPTANCE.md)。

P6新增[独立数据库迁移作业](docs/P6_MIGRATION_JOB_ACCEPTANCE.md)：新库可显式合并docker-compose.migrate.yml，迁移成功退出后才启动后端；后端只持受限运行身份，不注入迁移凭据。先按[角色说明](docs/DATABASE_ROLES.md)provision新库，不对现有卷自动切换权限。

新角色项目已支持[只读备份与受限恢复](docs/P6_BACKUP_ROLE_ACCEPTANCE.md)：backup须提供独立数据库口令文件，使用scarlet_backup的TCP认证；v2归档恢复角色与权限后以runtime启动，旧工具明确拒绝v2，新工具兼容旧v1。密码/归档不提交，默认本地legacy-admin模式不冒充最小权限。

P6新增独立HTTPS与资源配置：显式合并`docker-compose.tls.yml`，证书/私钥必填、仅loopback HTTPS，Secure Cookie、登录入口限流、三服务资源上限与后端只读根目录。默认HTTP不变，配置和隔离验证见[TLS_DEPLOYMENT](docs/TLS_DEPLOYMENT.md)；不代表生产部署。

P6数据库角色方案：新空库可采用独立Flyway连接与受限运行角色，见[DATABASE_ROLES](docs/DATABASE_ROLES.md)。现有库未自动更换角色，默认本地配置保留；双连接不等于长期进程不持有迁移权限，生产优先使用独立迁移作业。P6 Web范围已关闭，具体生产切换仍需独立验证。

P6运维检查：`scripts/operations.py`支持本机Docker健康探测和只读附件审计，Compose已加入有界日志轮转；命令、真实验收和未完成的生产门禁见[OPERATIONS_CHECKS](docs/OPERATIONS_CHECKS.md)。没有自动删除附件、安装告警服务或生产部署。

P6第二批已完成加密备份与隔离恢复演练：数据库全部表与私有附件配对备份、新实例真实登录和图片读取，工具与边界见[BACKUP_RESTORE](docs/BACKUP_RESTORE.md)。这不代表生产部署完成。

AI 原生个人终身学习系统。正式需求见 [开发总纲](Scarlet_Nexus_Codex_Master_Plan.md)，阶段实况见 [PROGRESS](docs/PROGRESS.md)。

P6已交付，新增“成长分析”和“数据管理”：真实已结束时长/任务完成/独立规则正确率、按日/技能图表、分来源能力与证据追踪；本人学习快照JSON导出；知识目录JSON校验预览后明确确认、新增UUID导入，保留跨技能共享定义，不覆盖原知识或学习历史。目录导入不包含学习记录/附件，学习快照不是可直接恢复的数据库备份。加密备份与隔离恢复已验证，监控工具及既定安全门禁已验证，实际生产运维尚未执行。边界见[P6设计](docs/P6_DESIGN.md)。

**当前P0–P6核心Web版本已验收**：技能/知识树、AI草案与版本、目标/路线/任务/学习记录、练习/证据、私有图片、数学及SSE导师，以及预算计划与复习。正式数据写入PostgreSQL并审计，不预置学习进度。验收实况与局限见[进度](docs/PROGRESS.md)，后续为桌面发行版独立交付。

P5使用方法：在“每日计划”选择日期、时区、总预算和本次技能上限，生成规则草案；检查原因、保存选择，勾选明确确认后计划才生效。原任务截止日期不会被草案覆盖。未完成任务可延期/取消/保总分钟拆分；提前重新排期可在学习工作台编辑日期与状态。已确认计划内任务改变预计分钟前须取消对应计划。

“到期复习”可手动安排或参考已确认的节点证据，按安排自身时区09:00调度；先做新练习并确认证据，再提交复测，历史记录前后日期、间隔与原因。规则独立正确延长间隔，其余保守1天；不自动提高能力等级。无Key可运行，未接入AI文字建议。详见[P5设计](docs/P5_DESIGN.md)与[P5验收](docs/P5_ACCEPTANCE.md)。

P4已加入工作台“练习与能力证据”、独立作答/准备提示/人工复核、确认后的证据和保守能力历史、私有PNG/JPEG附件、KaTeX数学，以及默认关闭的SSE导师。已按授权完成一次真实DeepSeek导师验收，单样例不代表全面教学质量，详见[P4_ACCEPTANCE](docs/P4_ACCEPTANCE.md)。

AI生成默认关闭，无Key也可完成P3学习闭环。P2已按授权完成一次DeepSeek真实Blender候选→编辑→发布验收，详见[P2验收](docs/P2_ACCEPTANCE.md)；单一样例不代表全面教学质量。模型配置与预算见[AI_GENERATION.md](docs/AI_GENERATION.md)和[Provider协议](docs/AI_PROVIDER.md)。

登录后可从“生成任务”查看已有任务，并确认结束超过五分钟的中断任务：草案不变，预算按未知用量保守占用，不自动重新生成。

启用状态已通过独立本地HTTP协议、真实PG及桌面/手机验收，复现见[GENERATION_ACCEPTANCE.md](docs/GENERATION_ACCEPTANCE.md)。协议响应是测试数据，不代表真实模型产出或供应商联调完成。

## 启动方式 A：Docker 本地持久环境

前置：Docker Engine/Desktop 已运行，Docker Compose v2+、Git、Python3.10+；首次构建需要网络。受测平台为Linux/amd64，无需宿主机 Java / Node / PostgreSQL。仅监听本机127.0.0.1，不是生产配置。

```powershell
Copy-Item .env.example .env
# 编辑 .env，设置自行生成的 POSTGRES_PASSWORD；不能留空或提交该文件
./scripts/Set-LocalAdmin.ps1 -Email 'you@example.com'
# 根据脚本提示设置.env中的ADMIN_EMAIL和ADMIN_PASSWORD_FILE；密码通过本地安全提示输入
docker compose config --quiet
python scripts/security_images.py --build
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:8088/api/v1/system/status
```

打开 http://127.0.0.1:8088 ，使用自己配置的邮箱/密码登录。首次构建需要下载依赖和镜像；/system显示数据库已连接、迁移V12。已有用户不会因重启或更换密码文件被重置。初始化后可取消ADMIN_EMAIL/ADMIN_PASSWORD_FILE环境变量；当前不提供密码重置接口，不能靠修改引导文件重置。数据库密码同样来自持久卷中的角色，不能只改.env就完成轮换。

技能详情中的“草案与版本历史”可创建图谱副本，编辑节点/关系、合并拆分、撤销重做、保存、校验并审核发布。共享修改须显式选择联合范围、重新预览并确认；目录变化保守返回冲突，不自动合并。

从“学习路线”创建目标、阶段及节点并选择学习，在“学习首页”为路线节点建立任务，进入工作台计时、写笔记、添加资料、更新任务和自报进度。路线修订追加新版本，旧任务仍关联旧版；不会自动迁移进度。计时离开页面后继续，休息请暂停；刷新/退出后从首页恢复。结束时段与完成任务分别保存。笔记须点击保存，未保存离开会提示；资料链接不在服务端抓取，P4附件与导师入口见下方。

P4练习页可手动创建题目，答案仅用于服务端比对或确认后查看解析；先提交作答，再选择提示/复核。证明、作品和纯图片不自动判对，人工意见标记为自评。确认复核记录为证据后，可追加有依据的能力评估。图片安全处理后存入attachments-data私有卷；`down`保留数据库及附件卷，备份时二者必须一起保留。当前不支持模型读图，需手动转录。导师默认关闭，启用策略、价格与恢复见[AI_TUTOR](docs/AI_TUTOR.md)；无Key不影响这些手动功能。

本机原生docker不在PATH时，可用 `./scripts/Invoke-Compose.ps1 -Action up`，脚本优先使用原生Docker，否则使用已准备的隔离WSL引擎；其他Action包括config/ps/logs/restart/down。它不自动安装服务、不覆盖.env、不删除卷。需要先按上述步骤准备.env和管理员文件。测试项目使用独立名称/卷，不是你的正式工作空间。

密码文件是仅开发机持有的UTF-8单行文件（12+字符、最多72字节）；保存在忽略目录.runtime/dev-secrets并只读挂载，不粘贴到聊天、不写入.env、不入Git。Set-LocalAdmin不会覆盖已有文件。HTTPS部署时SESSION_COOKIE_SECURE=true，本地HTTP保持false。

```powershell
docker compose logs --tail=100 backend
docker compose restart
docker compose down
```

`down` 保留数据库卷；不要使用 `down -v`。后端与数据库不映射宿主机端口，迁移启动时自动执行；迁移失败则启动失败。本机已在隔离WSL2 Docker引擎通过容器构建、空卷启动、数据库容器重建后的持久化与浏览器验收，证据见PROGRESS。

## 启动方式 B：无 Docker 的 P0 验收预览

前置：Java25 JDK、Maven3.9+、Node24 LTS与npm、网络。此方式下载测试依赖并启动**临时真实 PostgreSQL 17.11**，仅用于P0验收；使用本机loopback信任认证，不得保存个人数据。进程退出后不保留预览数据。需要长期使用时选择方式A或配置独立的安全PostgreSQL。

本机已在忽略的`.tools/jdks/jdk-25.0.5+8`安装并校验Temurin 25.0.5+8；下面的`-JavaHome`也可以替换为自己的Java25 JDK目录。工具不会自动安装系统JDK或修改全局环境变量。

终端一（Windows）：

```powershell
./scripts/Start-P0Preview.ps1 -JavaHome ./.tools/jdks/jdk-25.0.5+8
```

终端二：

```powershell
cd frontend
npm ci
npm run dev
```

打开 http://127.0.0.1:5173 。API通过Vite代理到127.0.0.1:8080，不需要跨域授权。停止预览使用终端Ctrl+C，停止前端同理。不修改系统默认Java或注册数据库服务。

Linux/macOS（不能以root运行隔离PG）：

```bash
cd backend
mvn -B test-compile exec:java -Dexec.mainClass=dev.scarletnexus.P0Preview -Dexec.classpathScope=test
```

独立数据库开发：先安全创建空库和专用账号，再在后端进程设置 `DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/scarlet_nexus`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`，以Java25执行 `mvn spring-boot:run`。后端不会自动读取根目录.env（Compose才读取），不要在命令行或日志写真实密码。源码默认只绑定loopback。

## 测试与构建

Windows一键（不含浏览器与Compose）：

```powershell
./scripts/Verify.ps1 -JavaHome ./.tools/jdks/jdk-25.0.5+8
# 本机隔离Node24已经放在.tools时，可指定，不修改系统PATH：
./scripts/Verify.ps1 -JavaHome ./.tools/jdks/jdk-25.0.5+8 -NodeHome ./.tools/node-v24.22.3-win-x64
# 已有可用Docker时，改用Testcontainers：
./scripts/Verify.ps1 -JavaHome ./.tools/jdks/jdk-25.0.5+8 -Testcontainers
```

等价独立命令：

```bash
cd frontend
npm ci
npm run format:check
npm run typecheck
npm test
npm run build
cd ../backend
mvn -B verify
# Linux容器测试推荐回到项目根目录运行下述专用脚本
```

项目`.mvn/settings.xml`将发行版依赖解析限定到Maven Central，避免上游POM引入的快照/里程碑仓库影响干净环境构建。Docker构建使用BuildKit缓存Maven依赖，不改全局Maven配置。

Linux上可运行 `sh scripts/verify-testcontainers-in-docker.sh`；它先构建并校验五个工具镜像（含Ryuk测试构建目标），在受测Java25/Maven容器中执行`verify -Ptestcontainers`，正确传递自定义Unix socket供Ryuk清理资源。需要已有本地Linux Docker权限和Python3.10+；不会修改宿主机权限或停用Ryuk。直接从宿主执行该profile前，须先在根目录运行`python3 scripts/security_images.py --build`并校验镜像；默认原生PG测试不依赖这些容器。

该容器测试入口只读挂载源目录，在容器内部生成独立编译输出，结束后将JAR及Surefire/Failsafe报告导出到backend/target，避免共享Windows编译目录的文件变化影响重启故事；失败日志与退出码照常保留。

`python3 scripts/verify_ryuk.py`从锁定原始归档和补丁重建独立测试目标，运行上游全部Go测试及不匹配标签资源保留检查，需要可信本地Linux Docker socket；CI在后端集成前执行。源码、模块校验和与MIT版权在security/ryuk，实际兼容边界和复现见[P6_RYUK_SDK_MIGRATION](docs/P6_RYUK_SDK_MIGRATION.md)。

后端默认测试使用隔离真实PG随机端口，包含数据库宕机503、迁移约束和应用重新启动的数据保留；不是H2。每次使用新集群且自动关闭。默认测试不调用外部AI。格式修正：前端 `npm run format`；后端 `mvn spotless:apply`。

浏览器验收：先启动方式B后端（或 `API_PROXY_TARGET` 指向方式A入口），再执行：

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e -- --project=desktop
# 手机项目使用重新启动的隔离验收应用，或等待原登录限流窗口结束后再运行：
npm run test:e2e -- --project=mobile
```

完整Playwright需要**隔离验收数据库/账号**，在终端设置E2E_EMAIL，并从本地验收密码文件读取E2E_PASSWORD（不要在命令中粘贴真实密码）。它会创建验收技能、任务、复习和证据，不要对个人真实库运行。默认启动Vite并真实请求后端；覆盖桌面/手机登录、手动图谱、路线计时、练习附件、三技能计划/任务调整/复测、刷新持久化和系统状态。产物在test-results/playwright-report（不入Git）。CI使用临时随机密码文件/独立数据库；未推送并执行前不能声称远端CI已通过。

验证Compose构建后的页面时，先在同一终端设置 `$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:8088'` 再运行 `npm run test:e2e -- --project=desktop`。此模式直接访问Nginx和生产构建产物，不启动Vite；完整桌面/手机回归使用下方隔离脚本。恢复开发模式可移除该环境变量。

默认全旅程在两个浏览器项目合计会超过单实例“5分钟10次登录”限额，不能为回归调高正式限流。Compose隔离验收可在根目录运行 `./scripts/Verify-Browser.ps1 -EnvFile .runtime/compose-acceptance.env -ProjectName scarlet-nexus-p0-acceptance -NodeHome .tools/node-v24.22.3-win-x64 -OutputRoot .runtime/browser-results`：须先设置隔离的E2E_EMAIL/E2E_PASSWORD并启动该验收Compose；脚本在每个项目前只重建命名项目的backend/web并等待Healthy，保留数据库/附件卷，输出仅允许.runtime子目录。新环境请替换为自己的隔离验收配置，不对真实学习库运行。CI通过桌面/手机matrix各自独立应用执行，远端CI尚未运行。

本机默认Node25不在当前Vitest支持范围内；可在项目根目录终端执行 `$env:PATH = (Resolve-Path './.tools/node-v24.22.3-win-x64').Path + ';' + $env:PATH`，再运行上述npm命令。`.tools`为当前机器隔离工具，不入Git；新环境应自行安装Node24 LTS。

## 设计与下一步

- [PRD](docs/PRD.md)、[架构](docs/ARCHITECTURE.md)、[数据库与ERD](docs/DATABASE.md)、[API合同](docs/API.md)
- [AI设计](docs/AI_DESIGN.md)、[安全](docs/SECURITY.md)、[决策](docs/DECISIONS.md)
- [路线](docs/ROADMAP.md)、[测试](docs/TEST_PLAN.md)、[运维](docs/OPERATIONS.md)、[进度](docs/PROGRESS.md)

开发OpenAPI：http://127.0.0.1:8080/v3/api-docs 。只包含实际端点，Compose默认关闭。P6已交付分析、导入导出、备份恢复与部署准备；生产部署、付费AI及不可逆操作另行确认。
