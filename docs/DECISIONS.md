# 架构决策记录

## ADR-001 — 保持指定栈，采用模块化单体（2026-11-15）

采用 Vue 3 / Java 21 / Spring Boot 3.5 / MyBatis / PostgreSQL；不引入微服务、Redis 或向量库。原因：单用户产品需要可靠数据关系和可备份性，单体足够且易维护。属于总纲明确默认值，不构成架构变更。

## ADR-002 — 会话认证与同源代理

采用服务端会话 + HttpOnly/SameSite Cookie + CSRF；P1 使用 PostgreSQL 持久会话。避免客户端长期 token 生命周期复杂度。P0 先拒绝所有私有入口，不生成默认账号。Nginx 本地入口、生产 TLS 留给 P6。

## ADR-003 — 迁移按阶段扩展

目标 ERD 全量设计，P0 只迁移 users 和 audit_events。后续业务表随真实约束、代码和测试一起迁移，避免在尚无功能时冻结未经验证的结构。P0 不插入学习样本、管理员或掌握度。审计追加由 PostgreSQL 触发器保护。

## ADR-004 — 无 Docker 时使用隔离真实 PostgreSQL 验证

本机缺少 Docker。使用测试范围内 Zonky Embedded PostgreSQL 启动真实 PG 17.11 进程，临时目录/随机端口，测试结束关闭。Compose 和 Testcontainers 同时保留；两者的测试结果分别记录，不能以本地 PG 证明容器构建成功。禁止 H2 代替。开发工具仅位于被忽略目录，不注册系统服务、不改系统 PATH。

## ADR-005 — 版本锁定和开发运行时

Java21 已存在 D:/Java21，仅在项目命令进程设置 JAVA_HOME；CI/Docker 统一21。Node 本机25可执行，CI/容器选择24 LTS；npm 锁定依赖。版本已核对官方兼容说明和制品元数据。依赖升级需复跑门禁，不用浮动 latest。

## ADR-006 — P0 可见功能限于真实系统状态

状态页请求真实 MyBatis / PostgreSQL 健康接口；失败可重试，显示工程阶段和 AI 未接入。技能、图谱、学习等入口在对应阶段实现，不添加假数据卡片。P0 报告区分代码完成、测试通过和环境验收待验证。

## ADR-007 — GraphFlow 环境限制

已读取 GraphFlow 技能并查询会话工具列表，但工具未暴露，无法调用 context/plan/index。采用范围明确的文件操作并保留后续重建索引任务；不得称已完成图谱更新。未自动安装或改动用户全局 MCP 设置。

## ADR-008 — 测试依赖告警不使用跨主版本强制覆盖

npm审计发现Vue Test Utils的js-beautify → glob/minimatch/editorconfig开发依赖链中等级告警；`npm audit --omit=dev`生产依赖为0。保持锁定且能通过测试的上游版本，不执行`audit fix --force`或对glob强制跨主版本override，以免通过破坏兼容性隐藏告警。暂不处理不可信项目/任意glob输入；发布前复查上游修复。详细计数及边界在PROGRESS记录，不宣称全依赖无漏洞。

## ADR-009 — 干净环境依赖解析与构建缓存

容器构建实测发现MyBatis上游POM引入Sonatype快照/Spring里程碑仓库，在当前网络超时。项目仅使用已发布Central依赖，因此在backend/.mvn配置项目级Central镜像；不改用户全局settings。Docker用BuildKit缓存.m2。真实镜像构建及Testcontainers验证通过后关闭P0门禁。

## ADR-010 — 现有WSL2上的隔离容器验收

现成Ubuntu发行版可运行，无需重装Docker Desktop或启用系统虚拟化。官方Docker静态二进制与iptables依赖包只提取到专用缓存；进程、Unix socket、数据根和Compose项目独立，不注册系统服务。Windows代理只监听loopback，使用标准输入输出转发到WSL的loopback，未开放未认证Docker TCP API。Docker运行产生正常的临时桥接网络规则；不改持久系统网络配置。

Testcontainers在Maven容器内运行时传递TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE为宿主机真实socket，供Ryuk清理资源；保持Ryuk启用。浏览器以PLAYWRIGHT_BASE_URL直接访问Nginx产物，保证包含构建输出和CSP验证。测试数据只在独立验收卷，不当作用户进度。

## ADR-011 — P1身份与管理员引导

JSON登录通过AuthenticationManager校验，显式轮换会话ID、CSRF并保存SecurityContext；Cookie不进入localStorage。Spring Session JDBC使用Flyway管理的官方表结构，不让框架自行建表。管理员邮箱/密码文件成对配置，只在users为空时创建，已有账号不重置；本地脚本通过SecureString输入、CreateNew写文件，不覆盖.env或已有秘密。测试账号仅位于隔离测试库。

采用BCrypt12与72 UTF-8字节上限，避免截断；登录限流10次/IP/5分钟，内存容量1000来源，过期回收，超限返回Retry-After。初期单用户单实例足够；P6再复核代理与分布式限流。认证DTO诊断字符串脱敏，覆盖本机DEBUG环境误开日志的已知情形。

## ADR-012 — P1手动图谱与一致性

节点定义、技能位置、关系分表。parent_node_id使用稳定知识节点UUID，通过同owner/skill的复合FK保证父节点属于当前技能；同节点在不同技能的位置独立。每次图写入先锁当前用户行，事务内检查分类环/全用户先修DAG并审计，避免同时添加相反边绕过校验。单用户低并发优先正确性，后续确有性能瓶颈再细化锁粒度。

技能/节点采用ACTIVE/ARCHIVED，保留引用与关系，恢复不换UUID；移出技能仅删除关联且拒绝遗留子节点，不静默丢弃节点。PATCH使用完整表单，不隐式清空缺失字段。前端创建定义和技能引用为两个显式步骤；引用失败时保留定义并提示通过节点库重试。客户端等待CSRF前冻结提交值，不自动重试写入。P1的CatalogService集中处理小型手动目录事务，P2再按版本发布边界拆分knowledge模块。

## ADR-013 — P2手动发布的保守一致性边界

knowledge模块独立管理草案/校验/发布。用户行锁串行化发布，revision保护草案，整个用户目录的稳定SHA256保护正式源；不自动合并过时草案。发布事务保留基础知识节点，只替换当前技能位置和内部关系；不可变快照保留历史。已有发布版本后，P1写接口不能绕过草案。共享定义不变允许引用，若会改变其他已发布技能内容则拒绝单技能发布，联合发布留给后续明确设计。预览保守列出所有引用技能，允许过度提示而不隐藏影响。

幂等键按用户唯一，与草案/revision/版本绑定。使用已有审计表保存草案变更和发布事件，不建立重复事实源。v1 Schema作为合同文件，严格Jackson/Bean Validation和图规则执行校验；AI Provider接入时还需完善通用Schema执行/异常输出恢复，当前不声称AI已实现。E2E共用一个验收账号，设单worker避免两个旅程互相触发正确的源指纹冲突；后端并发测试仍保留。

## ADR-014 — Provider传输先于生成入口

沿用Java21 HttpClient和现有Jackson，不引入Node运行时或供应商SDK。首个适配器实现Chat Completions结构化输出；网络调用不持有业务事务、不写正式图谱。默认禁用且无生成API，预算/任务落库完成前不开启浏览器调用。重试默认0，仅明确429/502/503可在整体deadline内有限重试；超时/断连不自动重复收费不明确的请求。用量缺失记UNKNOWN，不伪造估算或成本。当前仅本地HTTP协议测试，无供应商付费调用。流式/图片能力按后续阶段真实实现，不增加占位返回。

## ADR-015 — 同步任务与保守token账本

先持久化任务及独立草案，再在事务外调用Provider，最后事务结算和保存候选。使用UTC日token账本和已有用户行锁，按所有者+幂等键阻止重复请求；未知计费保留预留额度，进程崩溃不自动释放或重发。仅明确NOT_CONFIGURED释放。实际用量超出预留仍记录实际值，不以预算数字掩盖支出。token配额不是货币预算；货币策略、恢复和Schema兼容完成前不公开生成API。本轮无新队列服务、后台调度或付费调用。

## ADR-016 — 明确确认的中断任务结束

不用进程内状态推断重启前请求是否计费。数据库创建时间超过五分钟后，用户可确认将RUNNING转为FAILED/UNKNOWN，预留转保守消耗，不自动释放或重发。用户行锁串行化恢复与结果落库；终态任务忽略迟到结果，不覆盖草案。已结束任务重复确认无新增结算。查询API仅返回本人任务的状态及用量，省略输入hash/幂等键；页面使用手动刷新，暂不增加轮询和后台服务。货币对账与生成入口另行实现。
