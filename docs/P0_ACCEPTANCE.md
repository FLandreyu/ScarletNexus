# P0 验收记录 — 2026-11-15

结论：**工程交付和本地真实链路已验证，P0整体待Docker环境验收**。这不是全部业务完成，也不是生产部署完成。P1–P6未开始。

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
| Docker开发环境 | 配置解析、缺密码拒绝、5个镜像标签检查通过；引擎构建/运行尚未验证 |
| CI | 三个job已定义，尚无远端仓库运行记录；不得称远端CI已通过 |
| 密钥隔离 | .env / .tools / 数据 / 构建 / 测试截图与日志不入Git；本轮无真实账号/密钥/外部AI调用 |

## 本地证据位置（不入Git）

- `.runtime-verification.log`：一键门禁日志。
- `backend/target/surefire-reports` / `failsafe-reports`：JUnit计数和结果。
- `frontend/test-results/*/system-status.png`：桌面、手机截图，已逐张视觉检查。
- `frontend/playwright-report`：浏览器验收报告。
- `.runtime-npm-audit.json` / `.runtime-npm-production-audit.json`：依赖审计；生产0，开发链5项moderate，0 high/critical，参见ADR-008。

## 待关闭的P0门禁

本机只有指向不存在安装路径的已停止Docker遗留服务，无Docker引擎。没有启动该服务、安装虚拟化或伪造容器结果。可用Docker环境下运行后端Testcontainers，然后Compose干净卷构建/健康/重启及浏览器验收。未验证前保持P0待环境验收，不进入P1。

此外GraphFlow MCP本会话不可调用，索引待恢复。生产依赖npm审计为0不等于全系统安全审计通过；生产部署、备份恢复、真实AI联调均属于后续阶段。
