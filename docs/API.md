# API 合同

前缀 `/api/v1`，JSON UTF-8；时间为 ISO-8601 UTC，日期 YYYY-MM-DD；UUID 字符串。私有请求从会话识别用户，禁止相信客户端 userId。所有错误包含 `code`, `message`, `details`（数组）, `requestId`，并回显 X-Request-ID；内部异常不返回 SQL / 栈 / 连接信息。

## P0/P1 已实现

| 方法 / 路径 | 访问 | 响应 |
| --- | --- | --- |
| GET /api/v1/system/status | public | 200 `{service:"scarlet-nexus",status:"UP",database:"UP",schemaVersion:"3",phase:"P1",aiConfigured:false}`；通过 MyBatis 实查 Flyway 历史，DB 不可用 503 |
| GET /actuator/health | public | 200 `{status:"UP",groups:["liveness","readiness"]}`，故障503；不公开组件细节 |
| GET /actuator/health/liveness | public | 进程存活，不依赖 DB |
| GET /actuator/health/readiness | public | 应用就绪且 DB 可查询 |
| GET /v3/api-docs | dev/测试 | 自动生成的实际 OpenAPI，仅包含已实现业务端点；Compose 默认关闭 |

未知 `/api/v1/**` 未登录先返回401，不意味着对应功能已存在；CSRF 缺失的变更请求返回403。AI 配置布尔值当前固定 false，因为 Provider 尚未实施，不读取或测试任何外部 Key。

## P1 身份与手动图谱合同

公开`GET /auth/csrf` → `{headerName:"X-CSRF-TOKEN",token}`及HttpOnly/SameSite=Lax会话Cookie。所有POST/PATCH/DELETE请求需同源Cookie和此header（包括登录/退出）。`POST /auth/login {email,password}` → **200 `{id,email}`**并旋转会话ID、清理旧CSRF；后续写操作重新取CSRF。密码最大72 UTF-8字节，不做静默截断。`POST /auth/logout` → 204并使数据库会话失效；`GET /me` → `{id,email}`，未认证401。Cookie名SCARLET_SESSION，默认30分钟空闲超时；部署HTTPS时SESSION_COOKIE_SECURE=true。登录失败统一401 INVALID_CREDENTIALS；每来源IP每5分钟最多10次尝试（单实例内存），超限429 LOGIN_RATE_LIMITED + Retry-After。

| 方法/路径（均以/api/v1开头） | 请求/响应 |
| --- | --- |
| GET /skills | `q`按名称字面子串搜索，`status=ACTIVE/ARCHIVED/ALL`，`page=0`，`size=20`（1..100）；`{items,page,size,total}` |
| POST /skills | `{name,description,category,status}`全字段必填，描述/分类可空字符串；201+Location及Skill |
| GET/PATCH /skills/:id | GET返回Skill；PATCH同创建全字段→200，status归档/恢复，无硬删除 |
| GET /knowledge/nodes | 同技能列表分页/状态/搜索；Node为`{id,title,nodeType,description,status}` |
| POST /knowledge/nodes | `{title,nodeType:CATEGORY/CONCEPT/PRACTICE,description,status}`→201+Location；节点定义独立于技能 |
| GET/PATCH /knowledge/nodes/:id | 获取/全字段修改定义或归档恢复；UUID不变，所有技能引用同步 |
| GET /skills/:id/graph | `{skill,nodes,relations}`，节点增加parentNodeId/displayOrder，关系只包含当前技能内两端节点 |
| POST /skills/:id/nodes | `{nodeId,parentNodeId:null或当前技能节点UUID,displayOrder:非负整数}`→201 Position，复用已有节点 |
| PATCH /skills/:id/nodes/:nodeId | `{parentNodeId,displayOrder}`→200；位置只影响当前技能，分类环422 CATEGORY_CYCLE |
| DELETE /skills/:id/nodes/:nodeId | 204，仅移除当前技能引用，节点定义保留；有子节点422 HAS_CHILDREN |
| GET /knowledge/relations | 当前用户所有关系`[{id,sourceNodeId,targetNodeId,relationType}]` |
| POST/PATCH /knowledge/relations[/:id] | `{sourceNodeId,targetNodeId,relationType:PREREQUISITE_OF/RELATED_TO}`→201/200；全用户先修图成环422 DEPENDENCY_CYCLE；自引用422 SELF_REFERENCE |
| DELETE /knowledge/relations/:id | 204，删除关系引用并保留变更审计 |

Skill.name<=120、category<=80、description<=4000；Node.title<=160、description<=8000；空白名称400 VALIDATION_ERROR，未知状态/类型400，重复引用/数据库约束409 REFERENCE_CONFLICT（应用层位置冲突422 POSITION_CONFLICT）。所有私有读取/嵌套引用均校验当前用户，越权404。写入在事务内锁当前用户行，避免并发绕过环检测；审计与业务写入一起提交或回滚。PATCH采用完整编辑表单合同，不是JSON Merge Patch。

前端新建节点和附加技能引用是两个请求；若附加失败，已保存定义保留在节点库并明确提示，可选择引用重试，不冒充整组事务成功。客户端在取得CSRF前冻结提交值，不自动重试写操作。

## 后续合同基线（设计，不是已实现接口）

P2：`POST /skills/:id/graph/drafts {baseVersionId?}` → 201；`GET/PATCH /graph/drafts/:id`，PATCH 必须 `{revision,draft}`，冲突409；`POST .../validate` → `{valid,issues:[{code,path,message}]}`；`POST .../publish {revision,confirmed:true}` + `Idempotency-Key` → 发布版本；`GET /skills/:id/graph/versions` 和版本详情只读。`POST /ai/knowledge-drafts`/`ai/path-drafts` → 202 `{jobId}`；`GET /ai/jobs/:id` 轮询，未配置503 `AI_NOT_CONFIGURED`，失败保留草案。

P3：`POST/GET /goals`、`POST/GET /skills/:id/paths`、`GET /paths/:id`、`POST /paths/:id/enroll`；`GET/POST/PATCH /tasks?date=`；`POST /sessions/start {taskId?,nodeId}` / `POST /sessions/:id/pause|resume|stop`；状态转换在服务端验证，重复 stop 幂等。笔记 / 资料操作均有归属检查。

P4：`POST /attempts {questionId,content,clientRequestId}` 首先持久化 → 201，再触发批改；`GET /knowledge/nodes/:id/evidence|mastery`；`POST /ai/conversations`；`POST /ai/conversations/:id/messages` 返回 text/event-stream，事件 `start {messageId}`, `delta {text}`, `usage {...}`, `done {messageId}`, `error {code,message,requestId,retryable}`。断线后消息 ID 查结果，避免重复收费，禁止自动重新发送变更操作。

P4 附件：multipart `POST /attachments` 单文件最大10MiB、PNG/JPEG/WebP；服务端检测 magic bytes、限制解码像素、独立 UUID 路径；201 `{id,mimeType,sizeBytes}`；下载 `GET /attachments/:id/content` 需认证，nosniff 与安全 Content-Disposition；不使用公开文件 URL。413 超限、415 类型不支持。

P5：`POST /ai/plans/draft {date,budgetMinutes}` → 草案，不触碰正式任务；`POST /plans/:id/confirm {revision,confirmed:true}` + 幂等键；`GET /reviews/due` / `POST /reviews/:id/complete {attemptId,outcome}`，记录原因和下一次复习。

P6：`GET /analytics/overview?from=&to=`；`GET /exports` 输出带 schemaVersion、记录、引用、附件校验清单的 JSON；`POST /imports/preview` → token + 影响摘要；`POST /imports/confirm {previewToken,confirmed:true}` → 事务结果。导入任务体积/有效期受限，错误不部分覆盖。

验证错误400（结构/参数）或422（领域约束）；未认证401、CSRF403、资源不存在/不属于用户404、并发/幂等冲突409、上游限流429、未配置/暂不可用503、上游超时504。具体 DTO/schema 在对应阶段加入机器可读 OpenAPI 和契约测试，不能将本文件当作完整实现。
