# 开发进度

更新：2026-11-15（Asia/Shanghai）。正式需求：根目录总纲 v1.0。

## 当前阶段

P0/P1已验收；P2进行中。手动草案、校验、事务发布与版本历史已实现并通过本地验收；AI生成及高级编辑未完成，因此不将P2整阶段标记完成。P3–P6未开始。

## 初始环境检查

- 初始目录只有总纲（26466 bytes），不是 Git 仓库，无既有代码/用户数据。
- Windows11 amd64；Node25.2.1 / npm11.6.2；Maven3.9.16；系统Java25.0.2；发现 D:/Java21 的Java21.0.12，项目使用21。
- 无可用Docker / psql / 本地PostgreSQL服务；后续检查发现遗留com.docker.service处于Stopped，但其指向的C:/Program Files/Docker/Docker目录和可执行文件均不存在。未尝试启动残留服务或更改系统配置。
- 下载Node24.22.3便携版到.tools（官方SHA256校验），解决Vitest5不支持系统Node25的问题；下载独立Compose5.7.1并校验官方SHA256，仅用于配置解析，没有Docker引擎。
- Git已有身份配置；本地初始化与提交由总纲授权，不推送。
- GraphFlow MCP未提供，无法context/plan/index；已记录ADR-007。

## 已完成

- 阅读总纲；形成 PRD、架构、目标ERD/数据库约束、API合同、AI/安全、路线、测试与运维设计。
- 建立 AGENTS.md，记录默认决策和验证边界。
- 初始化本地Git（main），保留原始总纲；不创建远端、不推送。
- Vue3/TypeScript/Vite/Router/Pinia/Element Plus/Tailwind工程；状态页真实查询后端/PG，加载、故障、请求编号、显式重试、桌面/手机布局。
- Java21/Spring Boot3.5/MyBatis/Flyway/Bean Validation/Security/Actuator/OpenAPI工程；系统状态、存活/就绪、统一安全错误、请求ID；非公开接口默认拒绝，CSRF开启，无默认账号。
- V1迁移：users及唯一性/规范化/状态约束，audit_events及JSONB/外键/索引/追加触发器。无用户或学习数据种子。
- Compose/Nginx/Dockerfiles：同源入口仅loopback、DB持久卷、健康依赖、无默认密码；实际镜像标签远端manifest均存在。
- Windows验证和P0临时真实PG预览脚本；npm锁文件；CI定义覆盖前端质量、后端Testcontainers、Compose与浏览器烟测。
- P1身份：密码文件安全引导、BCrypt12、Spring Session JDBC、JSON登录/退出/当前用户、CSRF与会话ID轮换、统一失败及有界登录限流，无默认账号。
- P1技能/知识树：技能创建编辑归档恢复、分页搜索；稳定UUID节点创建编辑归档恢复、跨技能引用、独立父节点和顺序；依赖/关联增删改查、分类/先修环检测、所有权过滤/复合FK、图写入用户行锁、事务审计。
- P1界面：私有路由和会话恢复、技能列表/搜索/编辑、知识树列表编辑、位置调整、引用节点库、依赖编辑；桌面与手机均走真实API。移动端新建/编辑可定位并聚焦表单，避免在长页面寻找编辑器。新建节点引用失败保留定义并给出恢复提示。
- 本机启动辅助：Set-LocalAdmin安全提示输入密码、拒绝覆盖；Invoke-Compose优先原生Docker/回退现有WSL，支持config/ps/up/down等，不删卷、不改.env。

## 正在执行

- 已接入内置v1供应商Schema转换、实际请求字节token估计策略、默认关闭的生成API/草案页。系统AI配置状态读取实际生成配置，不再固定false。未启用任何外部模型，尚未验证启用页面的真实供应商生成，P2继续进行中。

- V6货币预算/价格快照已实现并接入任务创建、结算及保守恢复事务。金额计算使用BigDecimal，费用来源区分PRICED_USAGE/UNKNOWN/NOT_SENT；启用Provider必须配置货币策略且关闭重试。本人任务费用快照只读API已提供，无外部模型调用，P2仍未整体验收。

- 任务记录页`/ai/jobs`、owner隔离的列表/详情API和确认结束中断任务API已实现。超过五分钟的RUNNING可保守转FAILED/UNKNOWN，草案不变、预留转消耗；幂等恢复及迟到结果隔离已实现。生成创建入口仍未开放。

- P2内部GenerationJobs同步编排及V5任务/token账本，候选只进入草案。供应商证据对账、Schema兼容和生成页面仍待实现；没有创建生成任务的HTTP端点、没有外部付费调用。详情见AI_JOBS.md。

- P2手动草案发布闭环已实现：V4迁移、严格v1结构/图校验、草案revision、源数据冲突、全局DAG校验、幂等确认、事务回滚、不可变快照、审计及发布后编辑保护；前端支持新增节点、JSON编辑、撤销重做、差异和影响预览、确认发布、历史读取、未保存离开提示。未调用任何外部AI。
- 新增ai模块：AIProvider结构化合同、真实OpenAI-compatible HTTP适配器、默认禁用配置、有界响应/整体超时/有限重试/错误脱敏、用量缺失UNKNOWN。尚无生成API/任务/预算，不开放浏览器调用；配置见AI_PROVIDER.md。未接触真实Key或外部模型。

## 已执行验证

### P2 Schema与受控生成入口

- 最终Windows Java21 `mvn -B spotless:apply verify`：20单元+15真实PG集成=35项，无失败/跳过；日志`.runtime-p2-generation-verify-final.log`。新单测覆盖供应商Schema required/nullable/enum转换、原始合同不变、UTF-8和转义请求字节预留、超大输入、未配置零调用。原Provider loopback HTTP测试现发送实际转换后的Schema。
- HTTP故事验证关闭状态、受保护生成请求503且无任务记录；原PG故事继续验证生成候选/预算事务。供应商Schema转换和服务端图规则独立保留，未增加通用JSON Schema引擎，不声称支持任意Schema。
- Node24格式、前端8单元、类型和生产构建通过；Compose三个服务Healthy，仍为V6、无迁移修改；Nginx桌面/手机4项E2E通过（`.runtime-p2-generation-e2e.log`），新增未配置提示断言，再走手动草案发布旅程。桌面草案页截图已查看。最后系统AI状态配置读取改动由Windows完整回归验证，容器E2E为该状态小改前的同一UI/默认关闭配置。
- 未对启用状态页面执行实际模型生成，未证明供应商接受Schema或生成质量；本地HTTP/测试Provider不等于真实模型联调。未运行新一轮Linux Testcontainers，GraphFlow仍未暴露。无付费调用、无生产部署。
- 隔离验收容器和网络已停止，postgres-data卷确认保留；本轮无迁移修改，原总纲未改，不推送。

### P2货币预算切片

- Windows Java21 `mvn -B spotless:apply verify`，最终17单元+15真实PG集成=32项，无失败/跳过；日志`.runtime-p2-money-verify-final.log`。新增单测覆盖精确计算、8位向上舍入、负费率/精度/预算非法、未配货币策略不得启用Provider。
- PG故事覆盖价格快照、已知用量计算、未知费用保守占用、重复结算、超预算拒绝、跨用户费用不可见；完整编排测试确认货币不足时任务/草案/token预留一起回滚且Provider未调用。HTTP覆盖无费用记录显式null、未知任务404。价格均为测试数据，无供应商报价或付费调用。
- Node24前端8单元、类型和构建通过。Compose保留验收卷从V5迁移V6，三个服务Healthy；真实Nginx桌面/手机4项E2E通过（`.runtime-p2-money-e2e.log`），覆盖任务空页和原有草案旅程，不冒充费用页面或生成旅程验收。未重跑Linux Testcontainers整套。
- 当前预算基于服务器配置的单次预留；已知费用可超过预留并如实入账，未知用量也可能超过预留，不承诺供应商硬支出上限。多次调用的成本尚未实现，因此真实Provider启用时要求retries=0。仍需可信token策略、Schema兼容、供应商证据对账和生成入口。
- 隔离验收容器和网络已停止移除，确认postgres-data卷保留；原总纲及V1–V5迁移未改动，不推送、不生产部署。GraphFlow仍未提供，未声称已context/index。

### P2任务查询与保守恢复

- Windows Java21 `mvn -B spotless:apply verify`最终14单元+15真实PG集成=29项通过，无失败/跳过（`.runtime-p2-recovery-verify-final.log`）。扩展HTTP故事验证列表/详情、参数上限、未确认422、活跃409、过期恢复、重复结算幂等、未知ID404；应用故事验证恢复后迟到结果不写草案、不再次结算。
- Node24格式、8单元、类型和构建通过；Compose重建三个服务Healthy，无新迁移；真实Nginx桌面/手机4项E2E通过（`.runtime-p2-recovery-e2e.log`），增加任务页导航、真实空状态和刷新、无横向溢出，再走原有草案发布旅程。桌面/手机任务页截图已查看；恢复表单尚未通过浏览器实操，恢复语义由真实PG/API测试验证。最后的任务查询缓存修复由Windows完整回归验证，容器E2E为修复前同一UI代码。
- 迟到结果测试曾发现事务暂停期间MyBatis会话缓存返回旧RUNNING状态；对按键查任务显式flushCache后保留回归验证，不能仅以数据库已提交判断调用返回正确。
- 未调用任何外部模型，未添加生成任务写入口，未运行新一轮Linux Testcontainers。GraphFlow仍不可用，未声称context/index已执行。预算是token配额，恢复不释放资金或中止供应商请求；迟到用量对账留待货币策略实现。
- 收尾已停止隔离验收容器和网络，确认postgres-data卷保留；未推送或部署生产环境。

### P2任务账本切片

- Windows Java21 `mvn -B spotless:apply verify`最终14单元+15真实PG集成=29项通过，0失败/0跳过，日志`.runtime-p2-jobs-verify.log`。首轮测试账号哈希长度不符合既有约束，修正测试数据后复跑通过，未放宽数据库约束。
- 新集成故事验证任务幂等、无外层数据库事务的Provider调用、非法图失败、合法候选保存草案但不发布、已知用量结算、未配置释放、超时保守占用、超预算拒绝和两线程并发仅一项获准。Provider为测试方法内注入，不冒充真实模型联调。
- 前端8单测、类型/构建通过。Compose保留验收卷从V4迁移至V5，三个服务Healthy；本轮未重跑Linux Testcontainers整套。真实Nginx桌面/手机共4项E2E最终通过（`.runtime-p2-jobs-e2e.log`），覆盖既有登录、图谱、草案发布历史；首轮旧V4页面文本断言修正为V5后复跑成功。没有生成UI，本次浏览器测试不作为生成旅程验收。验收容器/网络已停止移除，数据卷保留。
- token预算只限制预留，不承诺实际模型支出的货币上限；实际用量超预留仍如实记账，未知用量保守占用。进程中断保留RUNNING/额度，恢复入口和货币策略完成前不开放浏览器生成。

### P2 Provider传输切片（2026-11-15后续）

- Windows Java21 `mvn -B spotless:apply verify`通过12单元+14真实PG集成=26项，0失败/0跳过，包含默认禁用Bean下应用启动、原有草案事务回滚；日志`.runtime-p2-provider-verify.log`。
- 随后仅新增超时/超大响应测试，`mvn -B spotless:apply test`最终14单元全部通过（其中8项Provider测试）；日志`.runtime-p2-provider-unit-final.log`。最终生产代码与上一轮verify相同，14项集成无需重复。
- Provider测试使用真实loopback HTTP传输，检查请求Schema/模型/授权头、返回用量、缺失用量UNKNOWN、429重试上限/401不重试、拒绝/截断/非法/尾随JSON、禁用零请求、HTTPS限制、超时不重发、2MB响应限制。无供应商联调、无付费请求，不把本地协议结果当作模型生成成功。
- 前端8单元、类型检查/生产构建通过；本轮无UI或数据库迁移变更，未重跑浏览器/Compose/Testcontainers，上一切片证据保留。GraphFlow仍未暴露，未声称索引。
- 首轮测试响应转义错误导致2项失败，改用Jackson生成测试响应后全套通过。Provider不持久化任何业务数据；生成任务/预算及完整P2验收仍待完成。

### P2手动草案切片验证

| 命令/范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B verify` | 最终后端源代码6单元+14真实PG集成=20项通过，0失败/0跳过；日志`.runtime-p2-backend-final.log` |
| Linux Java21/Maven `verify -Ptestcontainers` | 6单元+14真实PG容器集成通过，0失败/0跳过；日志`.runtime-p2-testcontainers.log`。随后新增的规范UUID/标题快照归一化、Graph.versioned字段已在Windows全套验证，未重复执行容器全套 |
| Node24 `npm run format` / `npm test` / `npm run build` | 8项单元通过，类型检查和生产构建通过；JS179.95kB/CSS45.48kB（未gzip） |
| Compose `up --build -d --wait` | V4迁移及前后端镜像重建成功，DB/backend/web均Healthy；验收使用独立项目/卷与密码文件，无真实用户数据 |
| 真实Nginx `npm run test:e2e` | 最终4项通过，桌面/手机手动旅程覆盖草案新增、撤销重做、保存、预览、确认发布、历史读取、返回知识树只读保护、退出；无API mock/JS异常/横向溢出；日志`.runtime-p2-e2e-final.log` |
| 截图/清理 | 已查看桌面和手机draft-published完整截图，响应式排版可用；验收容器和网络已停止移除，独立postgres-data卷确认保留；未配置真实用户.env，未生产部署 |

新增集成故事覆盖草案与正式图谱隔离、revision过期409、确认发布、幂等重试、快照不可改、发布后旧编辑API拒绝、源变化409、发布中途故障注入后完整回滚、共享已发布节点变更拒绝。GraphValidator单测覆盖非法类型/字段/版本/UUID、标题归一化、重复引用和跨图先修环。浏览器回归发现只读提示写入为问号，修正后重建容器并全套复跑通过。

### P1本轮验证

| 命令/范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B spotless:apply verify` | 3项单元+13项真实PG集成=16项通过，无失败/跳过；包括登录后旧CSRF拒绝 |
| Linux Java21/Maven容器 `mvn -B verify -Ptestcontainers`（受测shell入口） | 同16项通过，无失败/跳过，真实PG17.11容器与Ryuk；最后追加的旧CSRF断言另在Windows全套验证通过 |
| `npm run format`/typecheck/build | 通过；最终构建JS约170kB、CSS约45kB（未gzip）；无新增前端依赖 |
| `npm test` | 8项通过：状态/错误/重试、写前CSRF、凭据失败不写入、保留请求ID、提交值快照 |
| Compose `up --build -d --wait` | 通过，V1保留原checksum，V2/V3从P0持久库迁移成功，管理员文件引导、三个服务就绪 |
| 真实Nginx `npm run test:e2e` | 4项通过：桌面/手机各系统状态+登录创建Java技能/层级节点/依赖/拒绝环/刷新恢复/重命名/退出；无API mock，HttpOnly/Lax cookie、无JS异常、无横向溢出 |
| 截图视觉检查 | 已查看桌面和手机的完整知识树页面，表单/导航/关系/层级可见、无裁切；状态页截图同步 |
| Set-LocalAdmin测试 | 创建测试密码文件通过；已有文件写入被CreateNew拒绝；临时脚本测试文件已删除，不覆盖真实账号/配置 |
| Invoke-Compose本机WSL分支 | config/ps/up/down通过；保留密码和独立项目名，原生Docker分支本机未单独运行 |
| 依赖审计与结束清理 | 本轮复查npm生产依赖0、开发链5项moderate；已停止验收容器/网络，确认独立PostgreSQL卷保留；秘密/构建/截图均未入Git |
| CI配置 | YAML解析通过；加入随机隔离管理员密码文件和E2E配置；远端CI未运行 |

后端8个P1集成故事覆盖：安全管理员引导/重复不重置、Cookie/CSRF/会话轮换/退出、会话跨新应用实例、双用户资源404和SQL复合FK拒绝、共享节点顺序独立/重命名UUID不变/分类环/叶节点引用移除、先修环及相反边并发仅一条成功/拒绝后不写审计、分页字面搜索和归档恢复、登录限流。保留原5项系统/迁移/宕机集成与2项请求ID单元，新增1项凭据日志脱敏单元。

### P0历史验证（保留阶段证据）

| 命令 / 范围 | 实际结果 |
| --- | --- |
| Java21 `mvn -B spotless:apply verify` | 后端编译/打包/格式通过；2项JUnit单元+5项真实PG17.11集成全部通过，无跳过 |
| `scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64` | 最终端到端脚本退出0：npm ci、格式、类型、4项前端单测、构建和Maven verify全部通过 |
| `npm run typecheck` / `npm run build`（隔离Node24） | 通过；产物JS约147.74kB、CSS约41.72kB（未压缩） |
| `npm test` | 4项通过：真实合同解析、503后重试、网络失败、非法响应；单元环境允许stub fetch |
| `npm run test:e2e`（真实P0Preview后端） | 桌面+390px手机共2项通过，无API拦截；后端与PG均实际连接，刷新、无水平溢出、无页面JS异常；两张截图已逐张查看 |
| 独立Compose `config --quiet`（临时仅验证密码） | 通过；缺少密码被明确拒绝；未保存.env |
| Docker Hub manifest HEAD | Maven / Temurin / Node / Nginx / PostgreSQL所用5个标签均返回200；这不代表镜像构建/运行通过 |
| `npm audit --omit=dev` | 生产依赖0漏洞 |
| `npm audit` | 5项moderate（开发依赖链Vue Test Utils/js-beautify/glob/minimatch/editorconfig），0 high/critical；ADR-008记录，未强制跨版本覆盖 |
| Docker构建 / Compose空卷启动 | 本轮通过：WSL2 Ubuntu隔离Docker29.9.3、Compose5.7.1，前后端镜像构建成功、三个服务Healthy |
| PostgreSQL容器重建 / 后端重启 | 本轮通过：新建验收审计记录后重建DB容器并复用卷，记录保留；Flyway历史仍1条；后端重启后再次就绪 |
| `mvn -B verify -Ptestcontainers`（Java21/Maven容器） | 本轮通过：2单元+5真实PG容器集成，0失败/0跳过，Ryuk正常启用 |
| `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8088 npm run test:e2e` | 本轮通过：2项桌面/手机直接访问Nginx生产构建，未启动Vite、未拦截API；API与HTML均200并含CSP |
| 远端CI | 未运行：未配置远端仓库；本轮在本地执行实际容器和测试，不冒充远端结果 |

集成测试真实覆盖：空库V1迁移、validate与重复migrate幂等、规范化email唯一约束、非法状态、审计JSON约束/不可改删/外键保护、未登录401/CSRF403/Basic无效、requestId、应用实例关闭后重新启动保留记录、实际OpenAPI只含已实现端点、停PG后status/readiness503且liveness200、不泄漏连接信息。

首轮问题均已定位并修复：SpringApplication默认属性优先级不足导致新实例连错库（改用显式启动参数）；健康响应包含groups（改为检查无敏感components/details）；Element Plus样式从页面移至入口解决测试CSS加载；使用延迟响应验证Vue加载状态；系统Node25不受Vitest支持（隔离Node24）；Windows临时文件锁导致一次构建失败后复跑通过；PowerShell5.1将npm stderr警告视为异常（检查真实退出码，保留告警输出）。

## 阻塞与下一步

- P0无剩余验收阻塞。本轮发现现成WSL2 Ubuntu；提取Docker静态二进制和iptables运行依赖到专用缓存，没有安装系统包/服务、改全局PATH或修复残留Docker Desktop。发行版首次检查为Stopped，本轮仅启动Ubuntu，未触碰docker-desktop发行版。运行时详情见OPERATIONS。
- 本机可按README使用临时真实PG预览；它不保留用户数据，不能作为长期使用部署。截图和日志在忽略目录，正式证据摘要进仓库。
- 开发依赖5项中等级告警待上游兼容修复；生产依赖审计无发现不代表全栈安全审计完成。生产前重新评估Java依赖和容器漏洞。
- GraphFlow恢复可用后执行context/plan/index，当前不能声称已索引。
- **下一步P2**：用本地协议服务验证启用状态的API→预算→HTTP→候选草案完整链路及生成表单，完善非法输出恢复/供应商证据对账；Fake仅用于测试。随后完善流式/图片/估算合同、专用合并/拆分及来源映射、跨技能联合发布设计，最后完成总纲要求的Blender草案完整验收。真实供应商/付费调用或生产部署前单独确认，本地协议验收不算真实模型验收。
- 现有边界：源指纹保守覆盖整个用户目录，其他技能变化也可能要求新草案，不自动合并；改变其他已发布技能的共享定义/关系会被拒绝。当前新节点表单+高级JSON不是完整可视化图编辑器。登录限流仍为单实例内存；节点库选择最多100项且提供搜索；P1完整表单PATCH而非部分合并；无密码重置/多用户注册。部署仍是私有本地开发配置，不是生产安全验收。
- 暂不调用付费AI，不生产部署，不自动安装系统服务，不执行不可逆数据操作。
