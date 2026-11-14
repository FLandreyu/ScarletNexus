# API 合同

前缀 `/api/v1`，JSON UTF-8；时间为 ISO-8601 UTC，日期 YYYY-MM-DD；UUID 字符串。私有请求从会话识别用户，禁止相信客户端 userId。所有错误包含 `code`, `message`, `details`（数组）, `requestId`，并回显 X-Request-ID；内部异常不返回 SQL / 栈 / 连接信息。

## P0 已实现

| 方法 / 路径 | 访问 | 响应 |
| --- | --- | --- |
| GET /api/v1/system/status | public | 200 `{service:"scarlet-nexus",status:"UP",database:"UP",schemaVersion:"1",phase:"P0",aiConfigured:false}`；通过 MyBatis 实查 Flyway 历史，DB 不可用 503 |
| GET /actuator/health | public | 200 `{status:"UP",groups:["liveness","readiness"]}`，故障503；不公开组件细节 |
| GET /actuator/health/liveness | public | 进程存活，不依赖 DB |
| GET /actuator/health/readiness | public | 应用就绪且 DB 可查询 |
| GET /v3/api-docs | dev/测试 | 自动生成的实际 OpenAPI，仅包含已实现业务端点；Compose 默认关闭 |

未知 `/api/v1/**` 未登录先返回401，不意味着对应功能已存在；CSRF 缺失的变更请求返回403。P0 没有登录 / 技能 API。AI 配置布尔值当前固定 false，因为 Provider 尚未实施，不读取或测试任何外部 Key。

## 后续合同基线（设计，不是已实现接口）

P1：`GET /auth/csrf` → token，`POST /auth/login {email,password}` → 204 + HttpOnly session cookie（旋转 ID），`POST /auth/logout` → 204、会话失效，`GET /me` → `{id,email}`。401 认证失败、403 CSRF、429 限流；用户不存在和密码错误相同消息。P1 启用数据库持久会话。

P1：skills GET/POST/GET:id/PATCH:id，创建 `{name,description?,category?}` → 201 + Location；列表 `?page=0&size=20&q=&status=ACTIVE` → `{items,page,size,total}`（size 1..100）；PATCH 支持归档。knowledge/nodes、skills/:id/nodes、knowledge/relations CRUD；节点 UUID 稳定，跨技能引用关系同 owner；非法引用422/404，成环422。所有私有查询越权统一404。

P2：`POST /skills/:id/graph/drafts {baseVersionId?}` → 201；`GET/PATCH /graph/drafts/:id`，PATCH 必须 `{revision,draft}`，冲突409；`POST .../validate` → `{valid,issues:[{code,path,message}]}`；`POST .../publish {revision,confirmed:true}` + `Idempotency-Key` → 发布版本；`GET /skills/:id/graph/versions` 和版本详情只读。`POST /ai/knowledge-drafts`/`ai/path-drafts` → 202 `{jobId}`；`GET /ai/jobs/:id` 轮询，未配置503 `AI_NOT_CONFIGURED`，失败保留草案。

P3：`POST/GET /goals`、`POST/GET /skills/:id/paths`、`GET /paths/:id`、`POST /paths/:id/enroll`；`GET/POST/PATCH /tasks?date=`；`POST /sessions/start {taskId?,nodeId}` / `POST /sessions/:id/pause|resume|stop`；状态转换在服务端验证，重复 stop 幂等。笔记 / 资料操作均有归属检查。

P4：`POST /attempts {questionId,content,clientRequestId}` 首先持久化 → 201，再触发批改；`GET /knowledge/nodes/:id/evidence|mastery`；`POST /ai/conversations`；`POST /ai/conversations/:id/messages` 返回 text/event-stream，事件 `start {messageId}`, `delta {text}`, `usage {...}`, `done {messageId}`, `error {code,message,requestId,retryable}`。断线后消息 ID 查结果，避免重复收费，禁止自动重新发送变更操作。

P4 附件：multipart `POST /attachments` 单文件最大10MiB、PNG/JPEG/WebP；服务端检测 magic bytes、限制解码像素、独立 UUID 路径；201 `{id,mimeType,sizeBytes}`；下载 `GET /attachments/:id/content` 需认证，nosniff 与安全 Content-Disposition；不使用公开文件 URL。413 超限、415 类型不支持。

P5：`POST /ai/plans/draft {date,budgetMinutes}` → 草案，不触碰正式任务；`POST /plans/:id/confirm {revision,confirmed:true}` + 幂等键；`GET /reviews/due` / `POST /reviews/:id/complete {attemptId,outcome}`，记录原因和下一次复习。

P6：`GET /analytics/overview?from=&to=`；`GET /exports` 输出带 schemaVersion、记录、引用、附件校验清单的 JSON；`POST /imports/preview` → token + 影响摘要；`POST /imports/confirm {previewToken,confirmed:true}` → 事务结果。导入任务体积/有效期受限，错误不部分覆盖。

验证错误400（结构/参数）或422（领域约束）；未认证401、CSRF403、资源不存在/不属于用户404、并发/幂等冲突409、上游限流429、未配置/暂不可用503、上游超时504。具体 DTO/schema 在对应阶段加入机器可读 OpenAPI 和契约测试，不能将本文件当作完整实现。
