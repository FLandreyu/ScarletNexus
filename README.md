# Scarlet Nexus

AI 原生个人终身学习系统。正式需求见 [开发总纲](Scarlet_Nexus_Codex_Master_Plan.md)，阶段实况见 [PROGRESS](docs/PROGRESS.md)。

**当前 P0 工程基线**：Vue 前端通过 Spring Boot / MyBatis 查询真实 PostgreSQL 与 Flyway 状态；默认保护私有接口。登录、技能管理与 AI 等业务尚未开放。无预置账号、模拟学习进度或真实模型调用。

## 启动方式 A：Docker 本地持久环境

前置：Docker Engine/Desktop 已运行，Docker Compose v2，Git；首次构建需要网络。无需宿主机 Java / Node / PostgreSQL。仅监听本机127.0.0.1，不是生产配置。

```powershell
Copy-Item .env.example .env
# 编辑 .env，设置自行生成的 POSTGRES_PASSWORD；不能留空或提交该文件
docker compose config --quiet
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:8088/api/v1/system/status
```

打开 http://127.0.0.1:8088 。首次构建需要下载依赖和镜像；就绪后页面显示数据库已连接、迁移V1。数据库初始化后密码来自持久卷中的角色，不能只改.env就完成轮换。

```powershell
docker compose logs --tail=100 backend
docker compose restart
docker compose down
```

`down` 保留数据库卷；不要使用 `down -v`。后端与数据库不映射宿主机端口，迁移启动时自动执行；迁移失败则启动失败。本机无Docker，容器启动实测状态必须以PROGRESS为准。

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

后端默认测试使用隔离真实PG随机端口，包含数据库宕机503、迁移约束和应用重新启动的数据保留；不是H2。每次使用新集群且自动关闭。默认测试不调用外部AI。格式修正：前端 `npm run format`；后端 `mvn spotless:apply`。

浏览器验收：先启动方式B后端（或 `API_PROXY_TARGET` 指向方式A入口），再执行：

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

Playwright自动启动Vite，真实请求后端，不拦截为假响应；覆盖桌面与手机，产物在test-results/playwright-report（不入Git）。首次需安装浏览器。CI包含前端检查、后端Testcontainers、Compose烟测和真实API浏览器测试；未推送并执行前不能声称远端CI已通过。

本机默认Node25不在当前Vitest支持范围内；可在项目根目录终端执行 `$env:PATH = (Resolve-Path './.tools/node-v24.22.3-win-x64').Path + ';' + $env:PATH`，再运行上述npm命令。`.tools`为当前机器隔离工具，不入Git；新环境应自行安装Node24 LTS。

## 设计与下一步

- [PRD](docs/PRD.md)、[架构](docs/ARCHITECTURE.md)、[数据库与ERD](docs/DATABASE.md)、[API合同](docs/API.md)
- [AI设计](docs/AI_DESIGN.md)、[安全](docs/SECURITY.md)、[决策](docs/DECISIONS.md)
- [路线](docs/ROADMAP.md)、[测试](docs/TEST_PLAN.md)、[运维](docs/OPERATIONS.md)、[进度](docs/PROGRESS.md)

开发OpenAPI：http://127.0.0.1:8080/v3/api-docs 。只包含实际端点，Compose默认关闭。P0验收通过后进入P1身份、技能和手动知识树；生产部署、付费AI及不可逆操作另行确认。
