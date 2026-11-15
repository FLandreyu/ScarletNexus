# 测试策略与 P0 验收

P5实际门禁见[P5_ACCEPTANCE](P5_ACCEPTANCE.md)：最终Linux Java21/Testcontainers共31单元+43真实PG集成；前端13单元、格式/类型/构建，真实Nginx桌面/手机默认10项通过。覆盖三技能75分钟预算、未确认隔离、过期/并发冲突、幂等、不可变快照、故障回滚、先修、所有权、延期/拆分/计时保护，以及周期任务复用、新证据复测/暂停/原因历史。截图已查看，远端CI未运行，AI文字建议未接入、没有新模型调用。

P4实际结果见[P4_ACCEPTANCE](P4_ACCEPTANCE.md)：Windows最终28单元+35PG集成=63，Linux Testcontainers62（新增413用例前），前端11单元，真实Nginx桌面/手机8项、导师启用独立协议2项。新增超限行为在Windows及真实Linux容器浏览器覆盖；预算/恢复/隔离/不可变历史、UTF-8分片与deadline、数学HTML攻击输入均有实证。另按新授权完成真实deepseek-flash单次SSE验收，作答保留、无自动证据，单样例不代替全面教学质量；本次仅文档更新，未重复执行整套本地测试。

P3实际门禁和结果见[P3_ACCEPTANCE](P3_ACCEPTANCE.md)：最终Linux25单元+23真实PG集成，前端9单元，真实Nginx桌面/手机6项E2E；覆盖退出恢复、路线历史、计时并发与幂等、初次写入和旧revision防覆盖、所有权FK及故障回滚。后续阶段不能用本轮结果冒充AI导师/附件/证据验收。

使用真实 PostgreSQL，不用内存 SQL 替身。所有测试结果以 PROGRESS 记录和工具报告为准，本文件是计划。

## P0 门禁

1. `npm ci`、格式检查、vue-tsc、Vitest、生产构建：验证状态页实际请求、成功/失败/重试语义，禁止预置学习数据。
2. Java21 `mvn verify`：格式门禁、编译、JUnit、真实 PG 迁移与 Spring API；默认隔离 PostgreSQL，CI 可使用 Testcontainers profile。
3. 迁移：空库创建用户/审计表；Flyway validate；重复 migrate 不变；email 唯一/规范化、状态/外键、JSON约束和审计不可改；真实记录跨新连接/应用重启仍存在。
4. API：status 真实数据库读取；健康/就绪；私有请求401，非法变更403，requestId可追踪，错误不泄露配置，数据库断连后 readiness/status503。
5. Playwright：真实启动页面访问 API，无请求拦截；桌面和390px手机宽度，健康内容和无水平溢出。截图/报告位于忽略目录，CI 失败上传证据。
6. `docker compose config` 与干净卷启动 `docker compose up --build -d`，轮询健康后访问页面/状态，重启后迁移不重跑。`down` 保留卷；禁止默认 `down -v`。当前机器无 Docker 时此项明确待验收。
7. Git diff / secret 扫描：不含真实Key、.env、编译产物、数据库、个人学习内容。CI 使用仅测试的临时数据库密码，不包含生产凭据。

## 后续高价值回归

P1：双技能共享节点顺序独立；A→B→C→A拒绝；分类环拒绝；资源越权；登录/退出/CSRF/限流。P2：草案中途发布失败回滚；同revision并发冲突；非法JSON/超时不污染数据；幂等发布。P3：节点重命名和路线更新不损证据；暂停恢复计时；重复stop。P4：AI断线不丢作答、提示等级不泄题、图片校验/权限、评估关联证据。P5：预算硬约束、草案确认幂等、复习原因。P6：导出恢复记录数量/关联/checksum一致、全旅程窄屏回归。

Fake AI 只用于测试；真实 Provider 联调需要授权和安全配置 Key，不将 Fake 通过视为真实模型调用通过。CI 配置未在远端运行前不得宣称远端 CI 通过。

P1实际已覆盖3后端单元+13真实PG集成、8前端单元、4真实Nginx桌面/手机E2E。以P1_ACCEPTANCE和PROGRESS为证据，不将其视为P2草案回滚、真实AI或生产安全测试。
