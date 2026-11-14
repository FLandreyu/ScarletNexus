# Scarlet Nexus — Codex 完整开发总纲

> 版本：1.0 | 日期：2026-11-15 | 定位：AI 原生个人终身学习操作系统

## 0. 给 Codex 的执行指令

你是 Scarlet Nexus 的首席架构师、全栈工程师、测试工程师和技术文档维护者。你的目标是交付一个可长期真实使用、可测试、可备份、可持续演进的产品，而非仅制作演示界面。

**先检查仓库和运行环境。** 如果是空仓库，按照本文创建工程；若已有代码，先盘点现状、保留有效实现、提出迁移方案。不得假定已有文件、服务、密钥或数据库。

按照本文的阶段顺序自主实施；每阶段完成后执行验证、记录结果、提交可回滚的 Git commit，然后继续下一阶段。遇到缺少密钥、付费资源、不可逆操作、重大架构冲突或无法安全判断的产品决策时暂停并向用户提出最少量的明确问题。不得虚构测试结果、部署状态或已实现功能。

**优先级**：数据正确性与安全 > 核心学习闭环 > 可用性 > 性能 > 视觉装饰。必须实现真实的前后端和持久化，不得以静态 mock 页面代替业务功能。允许在测试环境使用模拟 AI 提供者，但生产 UI 必须明确标记模拟状态，未配置模型时应给出配置提示，不得冒充真实 AI。

### 0.1 项目工作纪律

1. 阅读并维护根目录 `AGENTS.md`、`docs/PRD.md`、`docs/ARCHITECTURE.md`、`docs/DECISIONS.md`、`docs/ROADMAP.md`、`docs/PROGRESS.md`。
2. 每个阶段先定义可验证的验收标准，再实现，再测试；禁止无关大规模重构。
3. 对不明确但非关键的细节采用本文默认值，写入 `docs/DECISIONS.md`，不要反复打断用户。
4. 不要依赖 Codex 聊天历史作为项目唯一记忆。所有重要决定、进度、已知问题、下一步都写入仓库。
5. 每次工作结束更新 `docs/PROGRESS.md`，包括已完成、正在做、阻塞项、验证命令与结果、下一步。
6. 代码、数据库迁移、API 文档、测试同步更新。所有数据库变更使用 Flyway，禁止手工改生产表结构。
7. 外部服务需要密钥时提供 `.env.example`、配置说明和安全的本地替代方案；禁止提交真实密钥。
8. 生产部署、购买资源、外部付费调用、删除真实数据、修改权限或暴露服务到公网前必须取得用户明确授权。
9. 在可用环境中运行测试；环境无法满足时报告具体限制，不得声称通过。
10. 阶段验收未通过，不得把该阶段标记为完成；若继续其他独立工作，必须记录未解决问题。

## 1. 产品定位与目标

Scarlet Nexus 是个人 AI 原生学习操作系统。用户可以管理任何技能、学科、知识领域或长期学习目标，例如：11408 考研（数学一、408、英语一、政治）、Java 后端、AI Agent、Blender、VRChat 角色制作、外语、艺术、硬件等。

核心链路：**技能 → 知识体系 → 目标 → 路线 → 任务 → 学习活动 → 证据 → 掌握评估 → 复习与下一步建议**。

必须解决：
- 每个技能拥有多层级知识树、跨节点依赖图、可选择的学习路线。
- 输入目标后由 AI 生成知识体系草案，用户可人工修改，审核后发布。
- 知识定义、学习路线、个人学习进度、AI 对话分离。
- 同一知识节点可被不同技能引用；各路线要求的目标深度可不同。
- 学习完成率不等于掌握程度，能力等级由可追溯证据支持。
- AI 导师能够根据当前节点、学习目标和历史证据答疑、出题、批改、分级提示。
- 每日任务和复习计划自动建议、用户确认、可编辑、可追溯。
- 桌面和移动端可用，支持数据导出、备份、恢复。

初期为单用户私有产品，但所有用户私有业务表保留 `user_id` 或明确的所有权约束，以支持未来多用户。**第一版不做**社交、商城、课程交易、排行榜、公开社区、复杂权限组织、移动原生 App、微服务。

### 1.1 成功指标

- 真实使用中，创建技能、生成/编辑/发布知识树、进入节点学习、记录进度、刷新恢复，形成完整闭环。
- 能在 3 次点击以内从首页进入当前学习任务（合理例外须说明）。
- 任务、学习证据、图谱发布均可审计，页面刷新或重新登录后不丢失。
- AI 不得未经用户确认修改已发布知识图谱、删除证据或直接宣称能力已掌握。
- 没有 AI API Key 时，手动技能管理、路线、任务、进度、复习和导出仍可使用。
- 个人学习数据可导出为结构化 JSON；备份可通过测试环境恢复演练。

## 2. 技术栈与仓库结构

- 前端：Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus + Tailwind CSS。
- 图谱：知识树优先；依赖图使用 Vue Flow；数学公式使用 KaTeX；图表使用 ECharts。
- 后端：Java 21 + Spring Boot（选择兼容的稳定版本）+ Spring Security + Bean Validation + MyBatis。
- 数据库：PostgreSQL + Flyway；主键 UUID；时间 `timestamptz`；必要时 JSONB。
- AI：服务端统一 Provider 接口，支持可配置模型、流式输出、结构化 JSON、图片输入、调用超时和成本记录。具体供应商通过配置接入，不硬编码。
- 文件：开发时本地受控存储，生产时 S3 兼容对象存储；附件权限校验。
- 部署：Docker Compose + 反向代理（Nginx/Caddy 二选一并记录）；HTTPS；环境变量注入密钥。
- 测试：后端 JUnit 5 + Spring Boot Test + Testcontainers（若环境支持），前端 Vitest + Vue Test Utils，关键流程 Playwright E2E。
- API：REST + OpenAPI；AI 流式响应可用 SSE。

推荐结构：
```
scarlet-nexus/
  AGENTS.md
  README.md
  .env.example
  .gitignore
  docker-compose.yml
  docs/
    PRD.md
    ARCHITECTURE.md
    DATABASE.md
    API.md
    AI_DESIGN.md
    SECURITY.md
    ROADMAP.md
    DECISIONS.md
    PROGRESS.md
    TEST_PLAN.md
    OPERATIONS.md
  frontend/
    src/{api,components,composables,layouts,pages,router,stores,types,utils}
  backend/
    src/main/java/.../{common,identity,skill,knowledge,path,learning,assessment,review,ai,resource,analytics}
    src/main/resources/db/migration/
    src/test/
  scripts/
```

每个后端业务模块可采用 controller/application/domain/infrastructure 分层，模块之间通过明确服务接口交互。保持单体部署，不要为了分层引入分布式复杂度。

## 3. 产品页面与用户流程

### 3.1 全局导航

1. **首页 / Dashboard**：继续学习、今日任务、当前重点技能、近期复习、学习时长、待处理草案。
2. **技能空间 / Skills**：技能卡片、搜索、分类、创建、归档、技能详情。
3. **知识地图 / Knowledge**：知识树、依赖图、节点详情、草案编辑器、版本历史。
4. **学习路线 / Paths**：路线列表、阶段、先修依赖、目标等级、里程碑。
5. **学习工作台 / Study**：当前节点、材料、AI 导师、笔记、练习、计时、学习记录。
6. **今日计划 / Planner**：任务清单、时间预算、优先级、延期、自动建议、确认。
7. **复习与练习 / Reviews**：到期复习、错题、重测、间隔复习。
8. **成长分析 / Analytics**：技能进度、能力等级、时间投入、独立完成率、趋势。
9. **设置 / Settings**：模型配置、教学偏好、数据导入导出、备份、安全、外观。

响应式：桌面侧边栏，移动端简化导航；不要求移动端完整拖拽图谱，需提供可用的列表编辑替代方案。

### 3.2 创建技能（最关键流程）

- 输入技能名称、学习目标、已有基础、可用时间、期望深度、语言偏好。
- 选择「AI 生成草案」或「手动创建」。
- AI 生成节点、分类、先修依赖、路线建议、实践项目和能力标准的结构化草案。
- 系统执行 JSON Schema 校验、ID 引用检查、重复候选识别、先修依赖环检测。
- 草案编辑器支持增删改、移动、合并、拆分、撤销重做、关系编辑、草案保存。
- 发布前显示变更摘要、潜在冲突与影响范围；用户确认后以事务发布版本。
- 发布后可继续创建新草案，历史版本和学习记录保持可追溯。

AI 生成的“完整”知识体系只能理解为目标范围内尽可能全面的候选结构，不能宣称覆盖所有知识。生成结果必须可编辑、可扩展，并标记来源与更新时间（如有）。

### 3.3 学习工作台

- 左侧/顶部：知识点信息、目标、先修节点、学习资料、当前路线位置。
- 主区：学习笔记、题目、代码或作品任务、AI 对话。
- 操作：开始/暂停计时、标记已学、提交解答/附件、请求提示、完成任务、生成复习建议。
- 每次学习活动持久化；可中断并继续；同一节点可以多次学习。
- AI 教学模式：讲解、苏格拉底式引导、独立练习、仅判对错、指出首个错误、一步提示、完整解析。
- 数学/408 强调独立作答；编程强调运行结果、测试与项目；建模强调作品与过程记录。

### 3.4 计划与复习

- 计划以目标、截止日期、优先级、先修条件、预计用时、历史表现、复习到期情况为输入。
- 规则引擎先约束时间预算与冲突，AI 再建议内容；生成**草案**，用户确认后生效。
- 避免无限滚动积压：未完成任务可顺延、拆分、重新排期或取消，保留历史。
- 支持长期目标、阶段计划、每周计划、每日计划，初期重点做好每日任务。
- 复习采用可解释的简单调度策略，后期再升级算法；记录调度原因和实际复测结果。

## 4. 领域模型与数据库规范

### 4.1 核心实体

| 表 | 关键字段（除通用 id/created_at/updated_at） | 说明 |
|---|---|---|
| users | email, password_hash, status | 私有部署也要有身份校验；单用户可初始化管理员 |
| skills | owner_id, name, description, category, status | 技能领域 |
| knowledge_nodes | owner_id, title, node_type, description, metadata_json | 可复用知识节点；稳定 UUID |
| skill_nodes | skill_id, node_id, category_parent_id, display_order | 技能中的分类位置；支持同节点跨技能引用 |
| knowledge_relations | owner_id, source_node_id, target_node_id, relation_type | PREREQUISITE_OF / RELATED_TO 等；关系方向固定 |
| knowledge_graph_versions | skill_id, version_number, status, published_at, snapshot_json | 已发布版本/快照 |
| graph_drafts | skill_id, base_version_id, draft_json, revision, status | 待审草案；乐观锁 |
| graph_change_sets | draft_id, actor_type, changes_json, validation_json | 变更审计 |
| learning_goals | user_id, skill_id, title, target_description, deadline, priority | 学习目标 |
| learning_paths | skill_id, goal_id, version_number, title, status | 路线定义 |
| learning_path_stages | path_id, title, display_order | 路线阶段 |
| learning_path_nodes | stage_id, node_id, display_order, target_level, required | 阶段节点 |
| path_enrollments | user_id, path_id, status, started_at | 用户路线选择 |
| learning_tasks | user_id, goal_id, node_id, title, planned_minutes, due_date, status | 任务 |
| learning_sessions | user_id, task_id, node_id, started_at, ended_at, duration_seconds, activity_type | 学习记录 |
| questions | owner_id, node_id, prompt, question_type, answer_key_ref | 练习题 |
| learning_attempts | user_id, question_id, submitted_content, result, hint_count, reviewer_type | 作答 |
| learning_evidence | user_id, node_id, evidence_type, source_type, source_id, verified_status, observed_at | 能力证据 |
| mastery_assessments | user_id, node_id, level, confidence, basis_json, assessed_at, assessor_type | 评估历史 |
| review_schedules | user_id, node_id, next_review_at, interval_days, state_json | 复习调度 |
| learning_resources | owner_id, title, resource_type, url, metadata_json | 资源 |
| node_resources | node_id, resource_id, relevance | 节点资源 |
| ai_conversations | user_id, context_type, context_id, title | 上下文关联对话 |
| ai_messages | conversation_id, role, content_json, created_at | 消息 |
| ai_generation_jobs | user_id, job_type, status, model, prompt_version, output_ref, error_code | AI 生成任务 |
| ai_usage_records | user_id, provider, model, input_tokens, output_tokens, estimated_cost, created_at | 用量统计 |
| attachments | owner_id, storage_key, mime_type, size_bytes, checksum, status | 附件 |
| audit_events | user_id, entity_type, entity_id, action, before_json, after_json, created_at | 审计 |

此表为概念基线，Codex 需要补充字段、外键、唯一约束、索引、枚举、软删除策略、迁移脚本，并在 `docs/DATABASE.md` 说明。

### 4.2 强制数据约束

- 统一 UUID 主键；外键和查询索引明确；所有私有查询按当前认证用户限定。
- 知识节点稳定 ID；重命名不改变 ID；合并/拆分记录映射与来源，不直接丢弃证据。
- `PREREQUISITE_OF` 有向无环；禁止自引用，发布前做全图环检测。
- 分类父子关系也不得成环；同一技能下节点位置与关系约束清晰。
- 图谱版本发布使用数据库事务；草案采用乐观锁避免覆盖并发编辑。
- 历史评估不可静默覆盖，新评估追加记录；可单独维护最新评估视图。
- 软删除/归档优先，永久删除须明确确认并提供导出/备份提示。
- 数据库 `jsonb` 用于扩展字段和快照，核心关系、权限、状态不应全部塞入 JSON。
- 避免在 AI 对话里存储唯一的业务状态；对话不是事实源。

### 4.3 能力评价

等级 L0 未接触；L1 了解；L2 理解；L3 独立应用；L4 熟练解决复杂问题；L5 可设计、优化并解释深层原理。各技能可定义专属 rubric。

分别呈现：路线完成率、当前能力等级、评估可信度、最近验证时间、目标等级差距。能力评价必须关联证据：独立正确、提示后正确、复测、代码测试、作品评价、人工自评等。**自评与验证结果必须分开标注**。初期不要用任意权重拼出伪精确的“掌握度 87%”；可先以规则和证据等级进行保守评价。

## 5. AI 系统详细规范

### 5.1 AI 服务接口

设计 `AIProvider` 抽象：`generateStructured`、`chatStream`、`analyzeImage`（能力不支持时明确降级）、`estimateUsage`；提供至少一个真实 Provider 适配器和一个仅用于测试的 Fake Provider。配置 API base URL、model、key、超时、重试、预算。对外部模型调用失败给出可恢复错误。

### 5.2 AI 工作流

1. **知识图谱生成**：目标与背景 → 结构化草案 → schema 校验 → 引用校验 → 环检测 → 人工编辑 → 发布。
2. **路线生成**：目标、现有基础、时间预算、知识依赖 → 分阶段路线草案 → 用户确认。
3. **节点教学**：节点定义、前置知识、目标、历史证据、教学偏好 → 模型回复；禁止越权更改学习状态。
4. **作答批改**：题目 + 用户作答 + 允许提示级别 → 批改意见与不确定性 → 用户确认/复核 → 证据。
5. **复习建议**：到期计划、错误类型、最近验证时间 → 推荐复测任务。
6. **每日规划**：固定规则先做约束，AI 再建议；冲突/超时需提示，用户确认才生效。

所有结构化输出必须使用版本化 Schema；模型不合格输出最多进行有限次数的修复尝试，失败时保留草案和错误信息，不写入正式图谱。模型输出是**不可信数据**，不得作为 SQL、脚本或前端 HTML 直接执行。

### 5.3 上下文与记忆

每次教学构建最小上下文包：当前用户目标、节点定义、路线位置、先修知识、最近相关学习证据、教学偏好、必要的对话片段。长期事实来自数据库，不依赖模型“记忆”。上下文裁剪与 token 成本监控必须存在。初期不引入向量数据库，只有检索质量不足时再评估 pgvector。

### 5.4 安全与可信度

- AI 生成知识结构仅为草案，发布需确认。
- AI 批改允许「不确定」与「需要人工复核」，图片识别错误可纠正。
- 独立练习模式严格遵守提示等级，不能提前泄露完整答案。
- 不能凭一次答对自动断言 L5；能力变化有证据链。
- 外部学习资料视为不可信内容，防范提示词注入。
- AI 操作建议必须由后端校验并经过授权后执行。
- API 失败、超限或未配置时不影响基础学习数据读写。

## 6. API 设计基线

统一前缀 `/api/v1`；认证采用安全会话或受保护 token 方案（选择一种并记录）；统一错误体包含 `code`, `message`, `details`, `requestId`。列表分页、参数校验、权限检查、幂等写入（对关键操作）和 OpenAPI 文档。

关键端点建议：
```
POST   /auth/login
POST   /auth/logout
GET    /me
GET    /skills
POST   /skills
GET    /skills/{id}
PATCH  /skills/{id}
GET    /skills/{id}/graph
POST   /skills/{id}/graph/drafts
GET    /graph/drafts/{id}
PATCH  /graph/drafts/{id}
POST   /graph/drafts/{id}/validate
POST   /graph/drafts/{id}/publish
GET    /skills/{id}/graph/versions
GET    /knowledge/nodes/{id}
GET    /skills/{id}/paths
POST   /skills/{id}/paths
GET    /paths/{id}
POST   /paths/{id}/enroll
GET    /tasks?date=YYYY-MM-DD
POST   /tasks
PATCH  /tasks/{id}
POST   /sessions/start
POST   /sessions/{id}/stop
POST   /attempts
GET    /knowledge/nodes/{id}/evidence
GET    /knowledge/nodes/{id}/mastery
GET    /reviews/due
POST   /reviews/{id}/complete
POST   /ai/knowledge-drafts
POST   /ai/path-drafts
POST   /ai/conversations
POST   /ai/conversations/{id}/messages
POST   /ai/plans/draft
POST   /plans/{id}/confirm
POST   /attachments
GET    /analytics/overview
GET    /exports
POST   /imports/preview
POST   /imports/confirm
```
具体请求响应、流式协议、上传策略和状态码由 Codex 写入 `docs/API.md`，不要直接将上述端点当成完整 API 实现。

## 7. 分阶段开发路线（必须按依赖顺序）

### P0 — 规格、架构与工程基线

交付：PRD、ERD、API 设计、AI 设计、安全方案、AGENTS.md、工程目录、Docker 本地开发环境、Flyway 基线、CI 基础测试、README 和 `.env.example`。
验收：空环境按 README 可启动前后端与数据库；健康检查可用；数据库迁移与基本测试通过；无真实密钥进入仓库。

### P1 — 身份、技能与手动知识树

交付：单用户登录/退出、技能 CRUD、节点 CRUD、技能分类树、先修关系 CRUD、基础搜索、移动端浏览、权限校验。
验收：创建 Java 后端技能、添加层级节点、建立依赖、刷新保留；错误引用和依赖环被拒绝；未授权请求被拒绝。

### P2 — AI 知识体系草案与版本发布

交付：AI 生成结构化草案、可编辑草案、变更预览、撤销重做（可先会话级）、图校验、发布事务、版本历史、失败恢复。
验收：输入 Blender 技能生成草案，人工编辑后发布；生成失败不破坏已发布版本；历史版本可查看；冲突不会静默覆盖。

### P3 — 学习目标、路线与学习记录

交付：目标、阶段、路线节点、进度、任务、学习时段、笔记、资料、学习状态；首页继续学习。
验收：选择路线、开始学习、记录完成情况，退出再进入后正确恢复；路线编辑不删除历史记录；无 AI 配置仍可使用。

### P4 — AI 导师、练习与证据

交付：节点关联对话、SSE 流式输出、KaTeX、图片附件、分级提示、题目与作答、批改、证据记录、初版能力评估。
验收：数学证明题可以先独立提交，再选择提示级别；批改不确定时可复核；证据关联正确；API 失败可重试且不丢作答。

### P5 — 智能计划、复习与长期学习

交付：目标优先级、每日任务草案、用户确认、未完成任务处理、复习队列、基础间隔重复、跨技能时间预算。
验收：同时管理 11408、Java、Blender，生成不超预算的次日计划草案；未确认不覆盖正式任务；复习到期可追踪。

### P6 — 分析、导入导出与正式部署准备

交付：学习时长、完成率、独立正确率、能力等级与证据可视化；JSON 导入导出、备份恢复演练、监控日志、生产部署说明、移动端优化、E2E 回归。
验收：关键用户旅程全通过；导出后可在测试环境恢复；登录与附件访问安全；生产配置不含硬编码密钥；文档与实际部署一致。

### P7 — 可选增强（不得阻塞正式可用）

PWA 离线缓存、图谱高级编辑、学习资源智能检索、知识体系跨技能去重建议、可配置 AI 人格、作品集、代码练习沙箱、更多导入格式、复杂复习算法、多用户支持。逐项评估价值、成本、安全风险，不自动全部实现。

## 8. 测试与质量门禁

每个阶段至少执行：静态检查/格式检查、前端构建、后端编译与单元测试、数据库迁移测试、关键 API 集成测试。P2 起覆盖图谱草案发布回滚；P4 起覆盖 AI 失败与超时；P6 完成 E2E 测试。

关键测试用例：
1. 知识节点跨两个技能引用，修改某技能展示顺序不影响另一技能。
2. 先修关系形成 A→B→C→A 时拒绝发布。
3. 草案发布中途失败时正式图谱保持原状。
4. 同一草案两次并发修改触发冲突提示。
5. 重命名节点后历史学习证据仍关联原 UUID。
6. 路线更新后旧学习活动与评估仍可追溯。
7. 用户无权限读取其他用户的节点、附件、对话或任务。
8. AI 返回非法 JSON 时不污染正式数据。
9. AI API 超时后用户作答不丢失。
10. 无 AI Key 时手动流程完整可用。
11. 图片上传拒绝不支持类型和超限文件；下载必须鉴权。
12. 数学独立练习模式不自动给完整解答。
13. 计划草案未确认时不覆盖已有正式计划。
14. 导出—恢复后关键记录数量、关联和校验值一致。
15. 手机窄屏可以创建技能、查看节点、学习和记录任务。

安全：密码哈希、输入校验、CSRF/CORS 策略、鉴权、限流、附件校验、日志脱敏、密钥隔离、数据库备份、错误信息脱敏；不得在日志记录完整密钥或敏感学习附件。

## 9. Codex 自主推进与恢复协议

### 9.1 每次开始

1. 阅读 `AGENTS.md`、`docs/PROGRESS.md`、`docs/ROADMAP.md`、最近 Git 提交。
2. 检查仓库状态与未提交改动，不覆盖用户修改。
3. 找出当前最早未完成的可执行里程碑及验收标准。
4. 选择一个范围有限的任务，先明确实现和测试步骤。

### 9.2 每次结束

1. 运行适用测试并记录实际结果。
2. 更新相关文档和 `docs/PROGRESS.md`。
3. 汇报：本次改动、验证结果、剩余问题、下一步、是否需要用户确认。
4. 在允许的情况下提交 Git commit；避免混入无关改动。

### 9.3 遇到阻塞

- 缺少密钥：完成可离线开发的部分，提供 `.env.example` 和配置说明，不索要密钥粘贴到聊天。
- 缺少 Docker/数据库：报告可复现环境问题，优先完成独立代码与测试，不伪造成功。
- 产品歧义：采用本文默认值并记录；只有影响核心架构、成本、安全或数据不可逆的事项才提问。
- 失败测试：先定位并修复；不能修复时记录重现步骤和影响范围。
- 上下文耗尽：依靠仓库文档恢复，不要求用户重新叙述完整需求。

### 9.4 任务完成定义（Definition of Done）

一个功能只有同时满足以下条件才算完成：真实前后端可用；数据持久化；输入校验与鉴权正确；关键错误处理完善；有自动化测试；文档更新；人工可验收；没有把 mock 作为生产功能；已记录局限。

## 10. 首批真实使用数据（仅作为初始化建议，不伪造进度）

允许用户创建以下技能：
- 11408 考研：数学一、408、英语一、政治；当前优先数学一、408、英语一。
- Java 后端开发：Java SE、数据库、Spring Boot、Web API、工程化等。
- AI Agent 开发：编程基础、模型 API、检索、RAG、Agent、评测。
- VRChat 角色制作：Blender、Unity、材质、骨骼、Avatar 工作流。

不得自动把示例百分比当作用户真实掌握程度。初次使用时通过引导问答或手动录入确定现有基础；允许导入既有学习记录，标明来源和可信度。

## 11. 关键产品决策默认值

- 单用户私有部署起步，预留多用户隔离。
- 中文界面优先，保留未来国际化可能。
- AI 生成 + 人工编辑 + 审核发布；不允许 AI 静默修改正式知识体系。
- 先知识树，后高级依赖图；手机提供列表编辑。
- 先规则驱动的复习与能力评价，后算法优化。
- 初期只需一个真实 AI Provider 适配器，但抽象接口要允许更换。
- 学习数据优先保存在 PostgreSQL，附件独立存储。
- 先完成核心闭环，再做视觉美化与复杂功能。
- 所有重大技术权衡写入 ADR（`docs/DECISIONS.md`）。

## 12. 第一轮 Codex 执行清单

1. 检查现有仓库与工具链。
2. 创建/完善 `AGENTS.md` 和全部核心设计文档。
3. 明确版本兼容、数据库 ERD、模块边界和 API 合同。
4. 按 P0 建立可启动工程、数据库迁移和测试框架。
5. 运行真实构建与测试并记录结果。
6. 更新 `docs/PROGRESS.md`，列出 P1 可执行任务。
7. 若无阻塞且权限允许，按阶段继续实施 P1–P6；不可跳过验收门禁。
8. 需要用户授权时仅提出具体决策及默认建议，不要求用户重新解释整套产品。

**最终交付目标**：一个真正能创建技能、生成并编辑知识体系、按路线学习、记录证据、使用 AI 导师、生成计划、安排复习、分析成长、备份恢复的个人学习系统。任何未实现功能必须在进度文档中清楚标注，禁止声称“全部完成”而缺少可验证证据。
