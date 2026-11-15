# Scarlet Nexus

AI 原生个人终身学习系统。正式需求见 [开发总纲](Scarlet_Nexus_Codex_Master_Plan.md)，阶段实况见 [PROGRESS](docs/PROGRESS.md)。

**当前 P1 + P2手动草案基础**：已实现私有登录、技能/知识树管理，以及独立草案、校验、确认发布和不可变版本历史。P2整体仍进行中：AI生成、专用合并拆分及联合发布尚未实现；路线和学习记录属于后续阶段。所有业务数据真实写入PostgreSQL并审计，不预置学习进度。

后端已加入默认禁用的结构化AI Provider传输适配器及内部生成任务/token配额账本，见[AI_PROVIDER.md](docs/AI_PROVIDER.md)和[AI_JOBS.md](docs/AI_JOBS.md)。货币预算和页面生成入口尚未接入，当前不提供端到端AI生成。

登录后可从“生成任务”查看已有任务，并确认结束超过五分钟的中断任务：草案不变，预算按未知用量保守占用，不自动重新生成。

## 启动方式 A：Docker 本地持久环境

前置：Docker Engine/Desktop 已运行，Docker Compose v2+，Git；首次构建需要网络。无需宿主机 Java / Node / PostgreSQL。仅监听本机127.0.0.1，不是生产配置。

```powershell
Copy-Item .env.example .env
# 编辑 .env，设置自行生成的 POSTGRES_PASSWORD；不能留空或提交该文件
./scripts/Set-LocalAdmin.ps1 -Email 'you@example.com'
# 根据脚本提示设置.env中的ADMIN_EMAIL和ADMIN_PASSWORD_FILE；密码通过本地安全提示输入
docker compose config --quiet
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:8088/api/v1/system/status
```

打开 http://127.0.0.1:8088 ，使用自己配置的邮箱/密码登录。首次构建需要下载依赖和镜像；/system显示数据库已连接、迁移V5。已有用户不会因重启或更换密码文件被重置。初始化后可取消ADMIN_EMAIL/ADMIN_PASSWORD_FILE环境变量；当前不提供密码重置接口，不能靠修改引导文件重置。数据库密码同样来自持久卷中的角色，不能只改.env就完成轮换。

技能详情中的“草案与版本历史”可创建当前图谱副本，添加节点或通过高级JSON编辑节点/关系，撤销重做并保存，再校验、审核变更和影响范围、勾选确认发布。保存草案不改变正式图谱；发布后不能用旧编辑入口绕过草案。源目录发生任何变化会保守返回冲突，需新建草案并手动合并旧内容。共享内容若涉及其他已发布技能，当前拒绝单技能改写，联合发布尚未开放。

本机原生docker不在PATH时，可用 `./scripts/Invoke-Compose.ps1 -Action up`，脚本优先使用原生Docker，否则使用已准备的隔离WSL引擎；其他Action包括config/ps/logs/restart/down。它不自动安装服务、不覆盖.env、不删除卷。需要先按上述步骤准备.env和管理员文件。测试项目使用独立名称/卷，不是你的正式工作空间。

密码文件是仅开发机持有的UTF-8单行文件（12+字符、最多72字节）；保存在忽略目录.runtime/dev-secrets并只读挂载，不粘贴到聊天、不写入.env、不入Git。Set-LocalAdmin不会覆盖已有文件。HTTPS部署时SESSION_COOKIE_SECURE=true，本地HTTP保持false。

```powershell
docker compose logs --tail=100 backend
docker compose restart
docker compose down
```

`down` 保留数据库卷；不要使用 `down -v`。后端与数据库不映射宿主机端口，迁移启动时自动执行；迁移失败则启动失败。本机已在隔离WSL2 Docker引擎通过容器构建、空卷启动、数据库容器重建后的持久化与浏览器验收，证据见PROGRESS。

## 启动方式 B：无 Docker 的 P0 验收预览

前置：Java21 JDK、Maven3.9+、Node24 LTS与npm、网络。此方式下载测试依赖并启动**临时真实 PostgreSQL 17.11**，仅用于P0验收；使用本机loopback信任认证，不得保存个人数据。进程退出后不保留预览数据。需要长期使用时选择方式A或配置独立的安全PostgreSQL。

终端一（Windows）：

```powershell
./scripts/Start-P0Preview.ps1 -JavaHome D:/Java21
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

独立数据库开发：先安全创建空库和专用账号，再在后端进程设置 `DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/scarlet_nexus`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`，以Java21执行 `mvn spring-boot:run`。后端不会自动读取根目录.env（Compose才读取），不要在命令行或日志写真实密码。源码默认只绑定loopback。

## 测试与构建

Windows一键（不含浏览器与Compose）：

```powershell
./scripts/Verify.ps1 -JavaHome D:/Java21
# 本机隔离Node24已经放在.tools时，可指定，不修改系统PATH：
./scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64
# 已有可用Docker时，改用Testcontainers：
./scripts/Verify.ps1 -JavaHome D:/Java21 -Testcontainers
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
# 或 mvn -B verify -Ptestcontainers
```

项目`.mvn/settings.xml`将发行版依赖解析限定到Maven Central，避免上游POM引入的快照/里程碑仓库影响干净环境构建。Docker构建使用BuildKit缓存Maven依赖，不改全局Maven配置。

Linux上只有Docker而没有Java/Maven时，可运行 `sh scripts/verify-testcontainers-in-docker.sh`；它在Java21/Maven容器中执行同一套真实测试，正确传递自定义Unix socket供Ryuk清理资源。需要已有本地Linux Docker权限；不会修改宿主机权限或停用Ryuk。

后端默认测试使用隔离真实PG随机端口，包含数据库宕机503、迁移约束和应用重新启动的数据保留；不是H2。每次使用新集群且自动关闭。默认测试不调用外部AI。格式修正：前端 `npm run format`；后端 `mvn spotless:apply`。

浏览器验收：先启动方式B后端（或 `API_PROXY_TARGET` 指向方式A入口），再执行：

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

完整Playwright需要**隔离验收数据库/账号**，在终端设置E2E_EMAIL，并从本地验收密码文件读取E2E_PASSWORD（不要在命令中粘贴真实密码）。它会创建验收技能和节点，不要对个人真实库运行。默认启动Vite并真实请求后端；覆盖桌面/手机登录、手动图谱、环拒绝、刷新持久化、退出，以及系统状态。产物在test-results/playwright-report（不入Git）。CI使用临时随机密码文件/独立数据库；未推送并执行前不能声称远端CI已通过。

验证Compose构建后的页面时，先在同一终端设置 `$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:8088'` 再运行 `npm run test:e2e`。此模式直接访问Nginx和生产构建产物，不启动Vite；CI也使用此模式。恢复开发模式可移除该环境变量。

本机默认Node25不在当前Vitest支持范围内；可在项目根目录终端执行 `$env:PATH = (Resolve-Path './.tools/node-v24.22.3-win-x64').Path + ';' + $env:PATH`，再运行上述npm命令。`.tools`为当前机器隔离工具，不入Git；新环境应自行安装Node24 LTS。

## 设计与下一步

- [PRD](docs/PRD.md)、[架构](docs/ARCHITECTURE.md)、[数据库与ERD](docs/DATABASE.md)、[API合同](docs/API.md)
- [AI设计](docs/AI_DESIGN.md)、[安全](docs/SECURITY.md)、[决策](docs/DECISIONS.md)
- [路线](docs/ROADMAP.md)、[测试](docs/TEST_PLAN.md)、[运维](docs/OPERATIONS.md)、[进度](docs/PROGRESS.md)

开发OpenAPI：http://127.0.0.1:8080/v3/api-docs 。只包含实际端点，Compose默认关闭。接下来P2为可编辑草案、校验、版本发布与可配置AI；生产部署、付费AI及不可逆操作另行确认。
