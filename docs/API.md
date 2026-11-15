# API 合同

GET `/api/v1/ai/jobs/{id}/cost`：本人任务的币种、模型、价格版本/日期、费率、预留/结算金额和来源；无费用记录返回`{cost:null}`，不是费用0。未知或他人任务404。此接口只读，不接受浏览器设置价格。

P2任务记录：GET `/api/v1/ai/jobs?page=0&size=20`（owner隔离列表）、GET `/api/v1/ai/jobs/{id}`（其他用户/不存在404）、POST `/api/v1/ai/jobs/{id}/recover`（会话/CSRF及`{confirmed:true}`，五分钟内409，未确认422）。恢复将预留转为UNKNOWN消耗，保留草案，不重发模型请求。详见AI_JOBS.md；尚无创建生成任务的HTTP接口。

前缀 `/api/v1`，JSON UTF-8；时间为 ISO-8601 UTC，日期 YYYY-MM-DD；UUID 字符串。私有请求从会话识别用户，禁止相信客户端 userId。所有错误包含 `code`, `message`, `details`（数组）, `requestId`，并回显 X-Request-ID；内部异常不返回 SQL / 栈 / 连接信息。

## P0/P1 已实现

| 方法 / 路径 | 访问 | 响应 |
| --- | --- | --- |
| GET /api/v1/system/status | public | 200 `{service:"scarlet-nexus",status:"UP",database:"UP",schemaVersion:"6",phase:"P1",aiConfigured:false}`；phase为最近完整验收阶段，P2部分实现不冒充整阶段完成；通过MyBatis实查Flyway，DB不可用503 |
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

## P2手动草案与后续合同

### P2本轮已实现的手动接口

`POST/GET /skills/:id/graph/drafts`从正式图谱创建/列出草案；`GET /graph/drafts/:id`返回`{id,skillId,document:JSON字符串,revision,baseHash,status}`。`PATCH /graph/drafts/:id {revision,document:JSON对象}`返回新revision；旧revision或已发布草案409 DRAFT_CONFLICT。无效结构可保存（对象、最多100万字符），但不能发布。

`POST /graph/drafts/:id/validate {}`返回`{valid,issues,potentiallyAffectedSkills,sourceChanged,nodeCount,relationCount}`。v1合同见resources/schemas/graph-draft-v1.schema.json；应用通过严格类型解析、Bean Validation与图规则执行本合同的校验，不是通用JSON Schema执行引擎。校验分类/先修环、重复与引用，兼顾草案外全用户关系。影响列表为保守的所有引用技能，前端展示名称与节点变更摘要。

`POST /graph/drafts/:id/publish {revision,confirmed:true}`必须提供`Idempotency-Key`（1..128位安全字符），返回`{id,skillId,draftId,versionNumber,document}`。同键/同请求返回同一版本，不同请求409。发布前当前用户整个技能目录的SHA256指纹必须仍与草案创建时一致，任何正式目录变动都会保守拒绝覆盖，旧草案仍保留。事务修改节点/位置/内部关系、版本、草案状态、幂等记录及审计。移出技能的节点定义不会删除。

`GET /skills/:id/graph/versions`、`GET /graph/versions/:id`读取不可变发布快照。所有接口按认证用户隔离。发布后P1直接修改版本图谱或被其引用的节点/关系返回409 USE_GRAPH_DRAFT，需走草案。跨多个已发布技能的共享定义/关系变更暂拒绝，联合发布尚未实现；共享定义不变的引用仍可发布。缺少请求头/参数400，未确认或图不合法422。

AI草案生成端点、Provider与高级合并/拆分仍未实现。下方P2原始目标合同是剩余规划，不能当作已实现。

P2剩余规划：`POST /ai/knowledge-drafts`/`ai/path-drafts` → 202 `{jobId}`；`GET /ai/jobs/:id`轮询，未配置503 AI_NOT_CONFIGURED，失败保留草案。上述AI端点当前未实现。手动创建草案当前返回200，使用document字段；发布快照按UUID、标题首尾空白和缺省字段规范化，保存的原始草案及审计仍保留。

P3：`POST/GET /goals`、`POST/GET /skills/:id/paths`、`GET /paths/:id`、`POST /paths/:id/enroll`；`GET/POST/PATCH /tasks?date=`；`POST /sessions/start {taskId?,nodeId}` / `POST /sessions/:id/pause|resume|stop`；状态转换在服务端验证，重复 stop 幂等。笔记 / 资料操作均有归属检查。

P4：`POST /attempts {questionId,content,clientRequestId}` 首先持久化 → 201，再触发批改；`GET /knowledge/nodes/:id/evidence|mastery`；`POST /ai/conversations`；`POST /ai/conversations/:id/messages` 返回 text/event-stream，事件 `start {messageId}`, `delta {text}`, `usage {...}`, `done {messageId}`, `error {code,message,requestId,retryable}`。断线后消息 ID 查结果，避免重复收费，禁止自动重新发送变更操作。

P4 附件：multipart `POST /attachments` 单文件最大10MiB、PNG/JPEG/WebP；服务端检测 magic bytes、限制解码像素、独立 UUID 路径；201 `{id,mimeType,sizeBytes}`；下载 `GET /attachments/:id/content` 需认证，nosniff 与安全 Content-Disposition；不使用公开文件 URL。413 超限、415 类型不支持。

P5：`POST /ai/plans/draft {date,budgetMinutes}` → 草案，不触碰正式任务；`POST /plans/:id/confirm {revision,confirmed:true}` + 幂等键；`GET /reviews/due` / `POST /reviews/:id/complete {attemptId,outcome}`，记录原因和下一次复习。

P6：`GET /analytics/overview?from=&to=`；`GET /exports` 输出带 schemaVersion、记录、引用、附件校验清单的 JSON；`POST /imports/preview` → token + 影响摘要；`POST /imports/confirm {previewToken,confirmed:true}` → 事务结果。导入任务体积/有效期受限，错误不部分覆盖。

验证错误400（结构/参数）或422（领域约束）；未认证401、CSRF403、资源不存在/不属于用户404、并发/幂等冲突409、上游限流429、未配置/暂不可用503、上游超时504。具体 DTO/schema 在对应阶段加入机器可读 OpenAPI 和契约测试，不能将本文件当作完整实现。
