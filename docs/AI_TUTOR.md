# P4导师协议、预算与恢复

服务端保留Java适配器，`AIProvider.chatStream`真实请求Chat Completions SSE，不将完整文本切片伪装流。默认`app.ai.tutor-enabled=false`，同时需要`app.ai.enabled`、token配额及P2货币策略才能启用；Compose不转发真实模型配置。本轮没有使用本地Key或调用外部模型。

协议依据：[DeepSeek Chat](https://api-docs.deepseek.com/api/create-chat-completion/)。`stream=true`、`stream_options.include_usage=true`，输出上限8192；DeepSeek显式JSON协议配置在聊天中仅用于选择`max_tokens`与关闭思考，不要求聊天输出JSON。默认OpenAI格式使用`max_completion_tokens`。HTTPS、无重定向、无重试，body整体deadline与2MB上限、单SSE行128KiB、总文本50000字符；须收到stop和[DONE]才成功。UTF-8按完整SSE行解码，不因网络分片破坏字符。

## 数据流

1. POST创建本人任务/节点关联对话，可绑定题目。练习对话必须引用已提交、同任务同题目的作答。通用节点讨论是另一上下文，不能以它的回答冒充独立作答。
2. POST stream需CSRF、Idempotency-Key及`confirmed=true`。事务中保存用户文本/上下文快照、价格与token/货币预留；随后事务外调用模型，增量通过SSE发给浏览器。
3. 成功保存建议文本和用量；超时/截断/断线保存已收到部分文本与固定错误码。模型回复不是学习事实，不直接更新任务、证据或评估。
4. 同键同请求只发snapshot，不再调用模型；同键不同请求409。浏览器不自动重试/重连；新请求须重新确认预算，GET记录不产生计费。
5. 每用户一个RUNNING请求，异步执行器2线程、16等待槽；超过5分钟的中断请求可确认恢复为FAILED/UNKNOWN，保守计入预留，迟到结果不覆盖终态。队列拒绝或进程中断可能留下RUNNING，需要这一恢复入口。

## 预算与上下文

复用P2的ai_daily_budgets、ai_money_days及用户行锁，避免图谱生成和导师各自绕过每日额度。Tutor费用快照放在tutor_turns，原图谱job/cost外键与API不改变。预留按同一序列化请求的UTF-8字节+4096开销+8192输出估计，先校验货币预留覆盖估计，再共同预留。已知用量按当时费率计算，失败未知用量按预留保守占用；不是供应商账单或硬支出上限。

上下文限定本人节点描述2000字、最多8个先修各300字、路线/目标、最近5条证据、最多3轮成功对话（用户1000/助手2000字截断）、当前作答与题目。长期事实来自PG，不依赖对话记忆；内容视为不可信学习数据，无工具执行、SQL或状态更改接口。

## 提示与图像边界

严格受限的一步提示来自题目作者准备的提示，完整解析需要确认；从不把未经审查的模型长回答直接标成“仅一步提示”。导师提供SOCRATIC、EXPLAIN、FULL_SOLUTION，练习请求记录提示/解析曝光，之后提交不再标记为未获帮助。只判文本一致与人工指出错误在练习复核中完成；没有自动准确定位首个错误的保证。

图片安全留档与作答关联已实现，当前`analyzeImage`明确不支持，`/tutor/status`返回`imageAnalysisSupported:false`。附件不会自动发送模型，用户可手动转录；不能声称模型已阅读图片。AI复核仅提供建议，用户确认后可引用同作答的成功turn保存REVIEWED_AI意见/证据，仍标SELF_REPORTED，不升为规则验证。

## 接口

前缀`/api/v1/tutor`：GET status；GET/POST tasks/{task}/conversations；GET conversations/{id}/turns（分页）；POST conversations/{id}/stream `{text,mode,attemptId?,confirmed}`；GET turns/{id}；POST turns/{id}/recover `{confirmed:true}`。SSE事件为meta（id）、delta（text）、done（终态turn）、snapshot（幂等重取）。失败可能在200流内报告FAILED，应读取事件状态；提问始终已落库。费用与状态显示在导师记录，不在图谱生成任务列表。

## 本地协议复现

`TutorIT`测试源集main可启动仅loopback的临时PG、Spring及确定性HTTP流服务，参数为忽略目录下metadata和全新stop文件路径；metadata仅含port/email/passwordFile/providerPort，不含密码/Key。Vite代理指向metadata端口，设置TUTOR_ACCEPTANCE=1、E2E_EMAIL/E2E_PASSWORD、E2E_TUTOR_FIXTURE_URL后运行Playwright，选择独立tutor-enabled.acceptance.ts。默认8项及原GENERATION_ACCEPTANCE套件保留。

该服务只存在测试源集，不打包入生产；模型名local-stream-fixture、报价为测试数值，不代表真实供应商/教学质量。结束时创建stop文件，运行时关闭服务、PG并删除临时密码/附件/metadata。真实模型验收需另有明确付费授权。
