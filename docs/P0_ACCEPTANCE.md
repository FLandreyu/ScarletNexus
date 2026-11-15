# P0 验收记录 — 2026-11-15

结论：**P0验收通过**（2907fc9检查点）。本文件保留P0当时的验收范围，当前P1结果见P1_ACCEPTANCE和PROGRESS；这不是全部业务或生产部署完成。

## 可复现命令

```powershell
./scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64
./scripts/Start-P0Preview.ps1 -JavaHome D:/Java21
# 另一个终端使用Node24：
cd frontend
npx playwright install chromium
npm run test:e2e
```

隔离Node工具仅当前机器存在；新环境请先安装Node24与Java21/Maven。长期持久部署使用README方式A，不使用临时测试PG保存真实资料。

## 交付对照

| 总纲P0要求 | 证据与状态 |
| --- | --- |
| PRD / ERD / API / AI / 安全 / AGENTS | docs全部设计与根AGENTS已创建；设计明确标记尚未实现的业务 |
| 工程目录与真实链路 | Vue状态页 → 同源代理 → Spring Boot → MyBatis → PG17.11；没有mock学习数据 |
| Flyway基线 | V1及真实空库迁移、重复迁移、约束、审计保护、应用实例重启持久化集成测试通过 |
| 基础测试 | 后端2单元+5集成、前端4单元；类型/格式/构建门禁；桌面和手机2项Playwright真实链路通过 |
| 一键验证脚本 | 最终Verify.ps1退出0，含npm ci/格式/类型/单测/构建与Maven verify；告警被保留，不将stderr等同失败 |
| README / .env.example | 含持久Compose与无Docker临时PG两种启动、测试、停止、配置边界；没有默认密码/Key |
| Docker开发环境 | WSL2隔离Docker29.9.3 + Compose5.7.1；前后端真实构建成功；新卷创建、三服务Healthy；本机loopback网关API/HTML200 |
| 持久化容器验收 | 写入验收专用审计记录后重建PG容器，复用卷记录仍为1条；Flyway历史1条；后端重启后就绪 |
| 容器测试与真实生产前端 | Testcontainers 2单元+5集成通过，Ryuk启用；Playwright直接访问Nginx产物，桌面/手机2项通过 |
| CI | 三个job已定义，尚无远端仓库运行记录；不得称远端CI已通过 |
| 密钥隔离 | .env / .tools / 数据 / 构建 / 测试截图与日志不入Git；本轮无真实账号/密钥/外部AI调用 |

## 本地证据位置（不入Git）

- `.runtime-verification.log`：一键门禁日志。
- `backend/target/surefire-reports` / `failsafe-reports`：JUnit计数和结果。
- `frontend/test-results/*/system-status.png`：桌面、手机截图，已逐张视觉检查。
- `frontend/playwright-report`：浏览器验收报告。
- `.runtime-npm-audit.json` / `.runtime-npm-production-audit.json`：依赖审计；生产0，开发链5项moderate，0 high/critical，参见ADR-008。
- `.runtime-compose-build.log` / `.runtime-compose-up.log` / `.runtime-compose-persistence.log`：实际构建、空卷启动、PG容器重建/后端重启。
- `.runtime-testcontainers.log`：Java21/Maven容器内真实Testcontainers测试。

## 本轮关闭的环境门禁

Windows原生Docker仍未安装，但已有WSL2 Ubuntu可用。本轮采用Docker官方静态二进制与专用数据目录，不安装Docker Desktop或系统服务；iptables依赖仅提取到隔离目录。为WSL访问现有本机代理使用loopback/标准输入输出转发，不开放TCP Docker API或公网端口。镜像、容器和卷均为P0验收专用。

同时修复两个干净环境问题：项目Maven Central镜像避免上游POM仓库超时；嵌套Testcontainers明确传递宿主机Unix socket路径，使Ryuk正常启动。CI浏览器改为直接访问Nginx产物，防止仅验证Vite开发页面。

此外GraphFlow MCP本会话不可调用，索引待恢复。生产依赖npm审计为0不等于全系统安全审计通过；生产部署、备份恢复、真实AI联调均属于后续阶段。
