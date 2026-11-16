# 开发进度

更新：2026-11-16（Asia/Shanghai）。正式需求：根目录总纲 v1.0。

## 当前阶段

P6第三批推进监控、日志与只读附件审计，整体仍未验收。详见OPERATIONS_CHECKS。

- 新增operations.py：Docker状态与内部liveness/readiness/网关HTTP探测，健康0、异常2；只读扫描附件并分类缺失/损坏/孤立文件，私有清单独占保存，无删除动作，明确非一致性快照。
- Compose三服务json-file日志10m/3轮转；网关访问日志省略URI/查询/凭据并关联请求编号；PG减少错误详情和SQL语句输出。实际inspect/SHOW及查询标记未入日志的检查通过。真实10引用/10文件无异常，停止验收后端时监控正确返回2，随后恢复。
- 5项Python测试通过；Java31单元+50真实PG集成=81项通过，0失败/跳过（.runtime-p6-ops-backend.log）；前端格式/类型/14单元/构建通过。真实Nginx桌面6项25.5秒+手机6项31.3秒全部通过（.runtime-p6-ops-browser.log）。实际inspect/SHOW与查询标记检查见.runtime-p6-logging-check.log，正常和后端停止故障检查见.runtime-p6-operations.log，新增gateway探测实际UP。
- 收尾停止隔离Compose、保留数据库与附件卷，不安装服务、删除附件或推送。生产TLS、最小数据库角色、资源与全栈风险复核仍未完成；不把生产说明当作已部署/已安全验收。下一步优先准备最小数据库运行角色与独立迁移角色的可验收方案。
- GraphFlow工具未提供，使用定向检查，未context/plan/index；原.env.example改动保留，无付费调用、推送或生产部署。

P6第二批：加密备份与隔离恢复已验收，详见[BACKUP_RESTORE](BACKUP_RESTORE.md)，整体P6仍未完成。

- 本轮新增scripts/backup_restore.py：命名项目停写后配对pg_dump/附件、全表与文件清单、数据库附件引用校验、加密和密文认证；恢复只生成新目标，不覆盖源库/卷，无模型调用或新迁移。
- 最终Docker演练54张public表、10个附件全部匹配；新实例Flyway/readiness、原账户登录、学习快照、10张鉴权图片读取和checksum通过（.runtime-p6-backup-verified.log）。首轮内部网络无端口映射导致探测失败，改为Linux宿主直接访问内部桥地址后通过。
- 3项Python/OpenSSL格式安全测试通过；Windows Java21 mvn verify为31单元+50真实PG集成=81项通过，0失败/错误/跳过（.runtime-p6-backup-backend.log）。前端格式/类型/14单元/构建通过，既有图表体积告警保留。CI新增恢复演练，远端未运行。
- 密文/口令/样例仅在忽略目录，原.env.example修改保留。GraphFlow仍未提供，未context/plan/index。下一步监控、脱敏日志轮转、孤立附件只读审计与生产TLS/权限/资源/依赖风险复核，P6不提前标为完成。

P0–P5已按各阶段交付范围验收；P6开发中，尚未整体验收。P5每日规则计划/确认/调整/复习闭环通过，AI文字建议未接入。P4的一次真实导师样例不代表全面教学质量或严格单问保证。

## 本轮：P6第一批分析与JSON迁移（2026-11-16，进行中）

- 已先定义P6_DESIGN完整分步门禁，本批实现成长分析、本人学习快照导出、知识目录预览/确认新增导入；完整数据库/附件恢复、监控与生产安全仍待后续，不把目录导入冒称全量恢复。
- 分析采用明确口径：已结束时段按结束日归集，任务按创建范围/当前状态、取消不进分母；每作答只取范围结束前最近RULE，独立明确对错才进独立正确率分母；自评与规则能力分别列最近结果，提供证据与原练习追踪。时区/日期范围/技能归属校验、REPEATABLE_READ一致读取。
- 新增V11目录导入预览表，源JSON/hash及确认结果受不可变触发器保护；确认新建UUID保留跨技能共享节点，不覆盖旧记录；事务、幂等、归属、严格字段/引用/环验证。V1–V10不修改。学习快照只读导出40个白名单业务表，附件仅元数据，不含身份凭据、文件路径或二进制。
- `/analytics`、`/data`与导航已接真实API；ECharts6.1.0精确锁定，用于真实时间趋势/技能投入，SVG与richText tooltip、不执行用户HTML，按需加载。最终Node24 format:check/typecheck/14单元/构建通过（`.runtime-p6-frontend-r2.log`），主JS290.11kB/CSS79.46kB，图表懒加载546.30kB/186.09kB gzip，保留体积提示；生产依赖audit0条发现（`.runtime-p6-audit.json`），开发链原5项moderate仍在。
- 第一轮真实PG暴露测试自评说明不足既有最小长度，以及Jackson默认将数字转字符串；前者修正验收输入，后者增加Textual显式拒绝数值/布尔与禁止浮点转整数。最终Windows Java21与Linux Java21/Testcontainers均31单元+50真实PG17.11集成=81，0失败/错误/跳过；日志`.runtime-p6-backend-final.log`（2分42秒）/`.runtime-p6-testcontainers.log`（4分41秒）。
- GraphFlow工具仍未提供，按规则定向检查，不声称context/plan/index；仓库原有.env.example改动保留，无真实模型调用、远端推送、生产部署或不可逆操作。
- Compose实构建升级既有独立验收卷至V11，三个服务Healthy；P6专门桌面/手机2项通过（`.runtime-p6-analytics-data-e2e-r2.log`）。ECharts自动ARIA覆盖固定图表名称的问题通过设置真实ARIA说明修正。已查看桌面/手机分析和导入确认4张完整截图。
- 默认全旅程新增后共12项、含12次UI登录（学习中断恢复有二次登录），首轮11通过/手机学习登录429，准确触发默认10次/5分钟限流。新增Verify-Browser.ps1按桌面/手机项目重建隔离backend/web并保留卷，CI改各自独立matrix实例，不修改生产限流。最终desktop6项29.9秒+mobile6项34.3秒全部通过，无API mock/JS异常/全页横向溢出（`.runtime-p6-browser-final.log`）；CI YAML解析通过，远端未运行。
- 第一批验收见P6_SLICE1_ACCEPTANCE，整体P6仍未完成。下一步数据库与附件完整备份、完整性/加密与隔离恢复/新实例读取演练，再完善监控日志/生产安全说明；目录导入和学习JSON快照不替代完整恢复。
- 收尾已执行本轮隔离Compose down，ps -a为空，postgres-data/attachments-data两卷确认保留；临时PG由测试运行时关闭，无额外预览服务、系统服务安装、生产部署或远端推送。验收截图/日志/下载JSON及样例不入Git。

## 本轮：P5预算计划与复习（2026-11-16）

- 先编写P5_DESIGN验收标准。V10增加复习安排/周期任务/不可变调度事件、规则草案、每日确认计划版本/当前指针、预算和任务调整历史；V1–V9未修改。所有写入按当前用户锁串行化，审计与业务同事务。
- 规则按到期复习、继续任务、目标优先级/截止日期、近期结果及先修自报完成排序，跨技能轮选整项；草案不创建任务、不覆盖当前计划。确认重新检查源指纹、revision、每日与技能预算，明确确认并用幂等键保存版本；故障注入回滚快照与周期任务。
- 延期/取消/保总分钟拆分保留原任务及笔记；计时未结束时拒绝调整。复习周期任务仅在计划确认时创建并跨日复用；复测需新作答和新确认的同节点证据，规则独立正确翻倍，其余保守1天，原因/日期/间隔追加历史。
- `/planner`、`/reviews`接入真实API，首页显示今日计划与到期数量，工作台提供任务调整/复习入口。规则来源显式标注，无新依赖、模型调用或生产部署。AI文字建议未接入，不冒充AI能力。
- 最终Windows Java21 `mvn -B verify`与Linux Java21/Maven `verify -Ptestcontainers`均为31单元+43真实PG17.11集成=74项，0失败/错误/跳过，格式/编译/打包通过；日志`.runtime-p5-backend-final.log`（1分55秒）、`.runtime-p5-testcontainers.log`（4分14秒）。新增3规则单元和8集成故事覆盖预算、先修、并发、幂等、所有权、不可变历史、故障回滚、计时/暂停、新证据与周期任务复用。
- Node24最终format:check/typecheck/13单元/生产构建均通过（`.runtime-p5-frontend-final.log`），主JS275.45kB/CSS78.38kB（未gzip）；无新依赖。前端新增明确确认/409保留草案、跨时区午夜日期测试。CI V10断言更新，YAML解析通过，远端CI未运行。
- Compose真实构建并复用既有验收卷升级V10，PG/backend/web均Healthy；真实Nginx默认桌面/手机10项通过，55.6秒（`.runtime-p5-e2e-final.log`）。P5专门旅程2项通过（`.runtime-p5-planning-e2e-r2.log`）：三技能各25分钟/总75、新草案不覆盖、延期/拆分、复测1→2天。无API mock/JS异常/横向溢出，已查看桌面/手机计划及复习历史4张完整截图。
- 首轮真实PG发现Java和PG UUID排序不同导致多技能指纹误冲突，已统一排序；回滚故障按既有合同返回503，测试修正预期后通过。首轮浏览器嵌套label精确定位超时，改用combobox角色后完整通过。预算列表限高滚动并默认由用户选择范围，不自动规划全部历史技能。
- GraphFlow工具未提供，使用定向本地检查，未执行context/plan/index。用户`.env.example`修改保持原样、不纳入提交；P2/P4一次调用授权已耗用，本轮不再调用。
- 验收细节见P5_ACCEPTANCE；规则启发式未个体校准，源指纹保守覆盖相关全用户上下文，预计预算不截断实际计时。总纲3.4的AI文字建议、每周/长期高级编排仍未实现。下一步P6：分析指标、JSON导入导出、隔离备份恢复、监控与生产说明；生产部署另行确认，不自动启动。
- 收尾已对本轮独立验收项目执行Compose down，ps -a确认无容器，postgres-data/attachments-data两卷保留；Windows临时PG由测试运行时关闭。没有启动额外开发服务、安装系统服务、推送或生产部署，日志/截图/样例仅保存在Git忽略目录。

## 本轮：P4真实导师验收（2026-11-15）

- 用户明确授权deepseek-flash一次、最多1 CNY；重新核对官方人民币价格，采用高峰缓存未命中输入2/输出8元每百万tokens保守计算。请求最多20000字节、8192输出，估计上限0.113728 CNY，预留0.2、每日限额1；无自动重试/追加修复。
- 在独立临时PG/Spring、本机随机端口59331，通过真实登录/CSRF/题目与作答接口保存独立思路，再发一次同题SOCRATIC导师请求。HTTP200、SUCCEEDED，收到SSE delta，供应商报告输入187/输出83 tokens；配置费率记账0.00103800 CNY，非供应商最终账单。作答保持原样，自动证据数量0。
- 样例使用平方非负证明题。模型引导检查展开式相等与平方符号，未写完整证明；但附带第二个思考点，不能据此承诺严格只问一问。受限一步提示仍走作者准备内容，开放式AI建议继续需要审核。6个实际数学片段用现有KaTeX安全选项离线渲染通过，无新增模型请求。
- 本轮运行命令为私有验收脚本编译及单次授权入口；真实请求1、自动重试0，单次哨兵已写入，禁止无授权重跑。证据`.runtime/real-tutor-evidence.json`，SHA-256 `825CB9E0C6F2DE55482E5C9667AAF29C7ADB4E7843E71C89AE39D1CC9E61593B`；原文/ID/Key只在忽略目录，不提交个人示例数据。首轮离线数学检查的PowerShell参数引号不兼容，改为本地mjs文件执行后通过，不涉及模型重发。
- 临时应用/PG已关闭，59331无监听，运行时删除临时引导密码和附件目录，未修改已有Compose卷、真实学习数据或默认模型开关。无生产部署/推送。GraphFlow工具仍未提供，未执行context/plan/index。本轮只更新验收/进度文档，不改业务代码，沿用前轮63项Windows后端、11项前端、8项Nginx及2项导师协议证据，不冒称重新运行这些套件。
- P4门禁按总纲的独立提交、分级帮助、可复核、证据关联和失败保留记录验收；图片分析仍明确降级、不支持自动读图。下一阶段P5：规则约束预算的每日计划草案、确认、延期及可解释复习；本次单次授权已用完，未来付费调用仍需新授权。

## 本轮：P4练习、证据与SSE导师（2026-11-15）

- 先编写P4_DESIGN；V9新增11表与任务同节点FK，题目/曝光、不可变作答/复核/证据/评估、附件、对话及导师运行。V1–V8保持原样。参考答案不进入独立练习读取，先提交后提示；提示曝光影响以后提交的独立性标签。
- 证明/作品/纯图片规则返回UNSURE；人工复核追加，显式确认才形成证据，SELF_REVIEW/REVIEWED_AI均为自报。RULE仅基于不同题目规则比对，最高L2；SELF为LOW，历史与依据可查，不改任务/路线进度。
- 新增私有PNG/JPEG附件：5MiB/8000边长/1600万像素限制、magic/MIME/解码、PNG重编码、UUID键/SHA-256/鉴权下载/归档、默认每用户256MiB配额。Compose新增attachments-data卷，容器用户目录权限已处理。后端和网关重建后，认证会话与图片校验和保留，证据`.runtime/p4-attachment-persistence.json`。
- 新增KaTeX精确锁定0.19.1及锁文件，用于数学片段渲染；原文/解析错误纯文本，trust=false及扩展/尺寸上限。按需加载MathText，主JS252.75kB、数学chunk260.16kB、CSS76.96kB。格式、11前端单元、类型/构建通过，日志`.runtime-p4-final-front.log`；生产依赖audit为0，原有5项开发依赖中等级告警未用force升级掩盖。
- Java适配器新增真实Chat SSE传输，整体deadline与体积上限、UTF-8分片、stop/[DONE]终止校验，无重试。对话/提问先落库，再在事务外流式调用；共享P2日token/货币账本、费用快照、幂等snapshot、过期任务保守恢复。图片分析明确不支持，不自动发送附件；严格一步提示来自作者准备内容。接口/配置见AI_TUTOR。
- Windows最终全量28单元+35真实PG集成=63项通过（`.runtime-p4-release-native.log`，1分56秒）；Linux Java21/Maven/Testcontainers此前62项通过（`.runtime-p4-testcontainers.log`，3分45秒），该轮早于新增413用例。覆盖预算不足零调用、同键零重复计费、截断/超时/中断保留内容、终态保护、跨用户/FK及不可变历史；新增超限multipart 413/no-row也在最终Windows及真实Linux容器浏览器通过。
- 默认无AI临时PG/Vite桌面手机8项通过；导师启用独立HTTP协议桌面手机2项通过，刷新未追加调用。真实Nginx最终默认8项通过（`.runtime-p4-release-e2e-final.log`，31秒），截图已查看。上传图片通过非root容器写入私有卷，数学字体与CSP下显示正常；上述启用协议是测试数据，不代表真实模型质量。
- 复核修复：Vue多语句模板移入函数、题目切换取消时恢复原选项、资料选择清理归档引用、明确超限413。首次Docker构建读到尚未格式化测试文件而被门禁拒绝，格式化后重建；曾在容器未就绪时提前启动浏览器造成连接失败，等三服务Healthy后完整通过，未将环境失败冒充功能通过。
- 核心开发提交29b1228时，GraphFlow工具仍未提供，无法context/plan/index；无远端推送/远端CI/生产部署/新真实模型调用。`.env.example`用户改动保留不纳入提交。当时真实导师验收脚本仅编译未执行；后续授权执行结果见上方真实验收记录。
- 收尾停止临时PG/学习运行时/协议服务并删除临时附件/密码/metadata；停止Vite与验收Compose，保留postgres-data及attachments-data卷。截图、日志、验收脚本在忽略目录；真实Key未读入本轮应用、未调用真实服务。P4验收细节见P4_ACCEPTANCE。

## 本轮：P3学习闭环（2026-11-15）

- 先编写P3_DESIGN验收标准。V8新增12张核心/关联表，复合所有权FK、路线不可变版本、用户唯一ACTIVE选择、唯一未结束时段；V1–V7未修改。目标/任务/资料归档或状态保留，笔记/进度revision防覆盖，写入与审计同事务。
- 路线通过阶段和节点表单保存新版本；旧版、选择、标题快照与任务引用保留，不自动迁移历史。状态与目标等级不冒充能力评估。工作台显示节点、先修、原路线位置，计时/任务完成/已学分别确认保存。
- `/learn`学习首页、`/paths`目标路线、`/study/:id`工作台接入真实API；笔记纯文本与未保存提醒，资料http(s)链接及归档，列表分页，首页独立查询继续任务。无需AI，无新依赖或付费调用。
- 最终Linux Java21 `mvn -B verify -Ptestcontainers`：25单元+23真实PG集成=48项，0失败/0跳过、BUILD SUCCESS（`.runtime-p3-testcontainers.log`，3分44秒）。Windows最终定向`mvn -B spotless:apply verify -Dit.test=LearningIT`：25单元+7项P3 PG集成通过（`.runtime-p3-final-learning.log`）；Windows此前全量46项通过，不将分轮重复计数相加。
- Node24格式、9单元、类型/构建通过，JS227.22kB/CSS46.86kB。临时PG+Vite默认桌面/手机6项通过（`.runtime-p3-e2e.log`）；持久Compose实构建，原验收卷从V7升V8、三个服务Healthy，真实Nginx默认6项通过（`.runtime-p3-nginx-e2e.log`）。截图已查看。
- 新增测试验证退出恢复、历史不丢、两请求并发只开始一个时段、相同命令不重复计时、旧revision409、非http(s)/相对/带凭据链接422、跨用户404/复合FK、审计故障503整笔回滚。首轮状态版本断言和错误码预期已按实际合同修正；原有生产错误处理未放宽。
- CI迁移断言更新到V8，未推送、未执行远端CI。GraphFlow工具仍未提供，未执行context/plan/index。无系统服务安装/公开暴露/生产部署，`.env.example`用户修改保留，不纳入本轮提交。详细证据见P3_ACCEPTANCE.md。
- 收尾：临时学习运行时及PG已关闭，临时引导密码/元数据删除；验收Compose执行down、保留postgres-data卷，Vite预览停止。不保留外部监听服务，私有截图/日志继续位于忽略目录。

## 本轮：DeepSeek真实验收（2026-11-15）

- 增加显式`app.ai.protocol=deepseek-json`，使用JSON对象模式、关闭思考、8192输出上限；默认严格Schema模式保留。预留估计和实际HTTP请求共用序列化，服务器Schema/图检查不变。无新依赖、迁移或前端行为改动。
- 用户将API信息写入模板；真实Key已移至Git忽略的本地配置，模板只清空Key，其他用户编辑保留且不纳入本次提交。官方V4.1 Flash API ID为`deepseek-flash`。
- Java21 `mvn -B spotless:apply verify`：25单元+16真实PG集成=41项，0失败/0跳过，日志`.runtime-deepseek-compat-verify.log`。Node24 `npm run format:check`、9项单测、`npm run build`（含类型检查）通过。Linux/Testcontainers和浏览器本轮未重跑；前轮Linux40项及浏览器6项证据继续保留，不混称本轮结果。
- 一次真实生成请求经本机Spring登录/CSRF/生成API发往DeepSeek，无重试、无额外修复。请求尺寸上限20000字节，输入保守预留加8192输出按官方高峰未命中费率估计最多0.113728 CNY，单任务预留0.2、每日限额1 CNY。模型返回SUCCEEDED，供应商总用量4630 tokens；按输入2/输出8元每百万保守记账0.03455000 CNY，非最终账单。
- 生成17节点/12关系，覆盖界面、建模、材质、灯光、相机、渲染及综合实践。原输出通过Schema/引用/无环检查；确认前版本数0；通过API编辑说明、预览有效、明确确认发布后版本数1/正式位置17，并读取历史。模型先修关系偏线性、UUID呈规律排列，内容仍需人工审核，不承诺随机性、教学完整性或多样本质量。
- 独立临时真实PG执行V1–V7；本机随机端口63301及数据库已关闭，临时引导密码删除，未修改保留的开发数据卷。真实结果/单次哨兵/验收脚本留在`.runtime`，不提交学习示例或Key；首次验收脚本将创建技能201误判失败，修正后重跑，首次在发送模型请求之前退出，真实调用总计仍为1。
- 证据摘要见P2_ACCEPTANCE.md；原始私有证据`.runtime/real-model-evidence.json`。GraphFlow本轮仍未提供，未执行context/plan/index。下一阶段为P3，按目标→路线→任务→学习时段→笔记/资源推进，先定义验收标准及迁移设计；不追加付费调用或生产部署。

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

- P2核心功能已实现并完成本地回归，正在整理最终证据和关闭隔离验收资源。
- V1生成、V2合并拆分/来源快照、节点/关系表单、重复提示、联合发布和历史查看均可用；发布始终明确确认，事务保留旧定义和版本。
- 生成默认关闭，token/货币策略和价格快照在服务端；V7保留私有主题用于最多两次显式修复，未知费用失败不自动重发。
- 最后门禁为用户授权后的真实供应商/模型质量验收。当前没有真实Key，不调用付费服务。P3–P6未开始。

## 已执行验证

### P2核心功能最终本地验收（2026-11-15）

| 范围 | 实际结果 |
| --- | --- |
| Windows Java21 `mvn -B spotless:apply verify` | 24单元+16真实PG集成=40项，0失败/0跳过；`.runtime-p2-final-native.log` |
| Linux Java21/Maven `verify -Ptestcontainers` | 同40项全部通过，真实PG17.11容器/Ryuk，V1–V7迁移；`.runtime-p2-final-testcontainers.log` |
| Node24格式/类型/构建/单测 | 9项单元通过；生产JS196.97kB、CSS46.29kB（未gzip），无前端新依赖 |
| Compose重建/V7升级 | 复用隔离验收卷，V1–V6校验不变、V7新增请求关联表，DB/backend/web均Healthy；`.runtime-p2-final-compose-guard.log` |
| 默认关闭配置Nginx浏览器 | 桌面/手机4项通过：任务页、手动图谱/草案/版本、状态V7；`.runtime-p2-final-default-guard.log` |
| 启用配置本地协议浏览器 | 桌面/手机2项通过：Blender主题生成→拆分→关系编辑→合并→撤销重做→重命名→预览发布→非法结果→确认修复→新任务成功；`.runtime-p2-final-enabled-guard.log` |

新增后端故事验证V2映射持久化、revision409、旧节点/版本保留、重复名称识别、真实Schema执行、来源/时间字段、共享节点默认保护、联合发布第二技能故障注入全部回滚及重试幂等、有限修复成功/重复不重发/最多两次/原发布版本不变。修复测试使用真实适配器向loopback测试HTTP发送，不替换生产Provider。

最后补充模型来源防伪：模型请求V1，返回即使结构有效的V2人工映射也必须失败。新增协议用例覆盖，Windows/Linux仍各40项通过，之后重新构建并复跑全部6项浏览器测试。没有把模型声称的人工来源当作可信记录。

容器构建曾发现联合确认的Vue内联多语句被格式化后无法解析，改为独立事件函数；状态页旧AI文案已改为读取真实配置，并新增启用状态单测。修正后生产构建、9单测和Nginx4项复跑通过。

真实供应商、模型质量、供应商账单核对与远端CI未验证。已确认项目无.env且无APP_AI_KEY；不得拿本地测试协议响应冒充真实模型。不生产部署、不进入P3。GraphFlow未提供，未声称context/plan/index已执行。完整设计/门禁见P2_DESIGN/P2_ACCEPTANCE。

收尾已关闭临时Spring/协议服务/PG/Vite，确认临时密码及元数据删除；停止Compose验收容器/网络，postgres-data卷保留。生产jar确认包含Schema引擎、不包含GenerationTestRuntime。发布/生成写入过程中冻结JSON编辑、联合范围和切换草案，避免响应覆盖用户正在输入的内容；冻结修正后重建并再次通过9单测/构建和全部6项浏览器回归。

### P2启用生成完整链路验收

- 新增GenerationChainIT及测试源集GenerationTestRuntime：真实Spring Boot、PG、登录/CSRF、完整服务器配置和真实loopback HTTP传输，协议响应是确定性测试数据，未调用模型。生产源代码/迁移/实际配置未改，不打包测试协议服务。
- Java21 `mvn -B spotless:apply verify`最终20单元+16真实PG集成=36项，无失败/跳过，日志`.runtime-p2-enabled-verify-final.log`。新故事验证生成成功候选落库、同键仅调用一次、非法结构/截断失败、费用快照、预算结算；未确认前无发布版本/正式节点位置。
- 前端8单元、类型/构建通过；`GENERATION_ACCEPTANCE=1`选择独立启用套件，Vite15173→真实后端18080→PG/本地HTTP服务，桌面和手机共2项通过（`.runtime-p2-enabled-browser-final.log`）。验证费用确认、生成→编辑器→预览→明确发布、非法结果提示/任务列表、无JS异常/横向溢出；两张截图已查看。默认套件通过`--list`确认仍4项，本轮未重跑关闭配置的Nginx4项。
- 首轮浏览器断言误用文本节点读取textarea值，改为toHaveValue后用全新隔离库复跑成功；协议响应补齐required的可空parent字段。没有修改生产逻辑来迎合测试。
- 本地协议验收不代表真实供应商Schema接受、模型质量或生产部署成功；未重跑Linux Testcontainers/远端CI。GraphFlow仍不可用，未声称context/index已执行。复现与资源清理步骤见GENERATION_ACCEPTANCE.md。
- 收尾确认18080/15173端口无监听，Spring/协议服务/Vite/临时PG均关闭，临时密码及元数据已删除；未触碰保留的Compose数据卷或实际配置。

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
- **当前P6后续**：在第一批分析/学习快照/知识目录迁移基础上完成数据库与附件备份恢复演练、完整性与新实例读取验证、监控日志/轮转、生产配置说明和安全复核。整阶段门禁仍未通过；不得以目录JSON导入代替完整学习历史恢复。P5的AI文字建议、P4图片自动识别和供应商账单对账局限继续保留。
- 现有边界：源指纹保守覆盖整个用户目录，其他技能变化也可能要求新草案，不自动冲突合并。改变其他已发布技能必须明确联合范围、重新预览及确认。表单/JSON编辑器没有拖拽画布；来源映射保留节点与快照，不自动迁移能力证据。登录限流仍为单实例内存，节点库最多100项并提供搜索；无密码重置/多用户注册。部署是私有本地开发配置，不是生产安全验收。
- P2与本轮P4各一次付费调用授权均已使用；不追加调用、不生产部署、不自动安装系统服务、不执行不可逆数据操作。
