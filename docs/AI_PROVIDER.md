# P2结构化Provider传输

本轮交付后端传输边界，尚未开放浏览器生成入口，也未发送外部模型请求。生产代码包含真实HTTP适配器；本地协议服务只存在于测试。手动草案不依赖Provider。

## 合同

`AIProvider.generateStructured(instructions,input,schema)`调用配置的`/chat/completions`，传递JSON Schema、固定单次输出上限8192 tokens，要求完整`stop`结果和JSON对象。返回候选文档和供应商用量；缺失用量为UNKNOWN，不填零或猜测价格。调用者仍须执行本地Schema和图规则验证，不能将结果直接写入正式图谱。

协议参考：[Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。兼容供应商必须支持所传Schema；不支持时明确失败，不自动退回无Schema请求。仓库的v1合同尚未转换成各供应商支持的Schema子集，因此本轮不声称图谱生成已可用。

## 服务端配置

Spring配置项为`app.ai.enabled`（默认false）、`app.ai.base-url`（包含/v1路径）、`app.ai.model`、`app.ai.key`、`app.ai.timeout-seconds`（默认60，范围0.1–120秒的传输构造器；环境配置使用整数秒）、`app.ai.retries`（默认0，最多2）。Spring环境变量对应`APP_AI_ENABLED`等。无默认供应商/模型/密钥；不提供真实Key示例，不在前端保存凭据。未启用时调用立即返回AI_NOT_CONFIGURED。启用但配置不全会启动失败。

当前Compose不转发这些变量。生成HTTP入口默认关闭，需另配generation-enabled及服务端token/货币策略，见AI_GENERATION.md。不得仅配置Key就绕过预算和付费授权。

只允许HTTPS（测试/本地模型允许loopback HTTP），拒绝URL凭据、query、fragment和重定向。请求整体deadline包含重试等待；仅429/502/503可重试，支持秒数或HTTP日期Retry-After，超出剩余deadline直接返回可恢复失败。网络异常/超时不自动重发，避免不明确的重复计费。响应最多2MB；错误仅返回固定代码，不回显上游正文/Key/prompt。超时或错误不表示供应商未计费。

## 尚待实现

内部持久化任务、token/货币预留结算、价格快照和保守恢复已实现，见[AI_JOBS.md](AI_JOBS.md)、[AI_MONEY.md](AI_MONEY.md)。启用真实Provider必须配置货币策略且重试为0。内置v1 Schema转换、字节token估计及默认关闭的生成入口见AI_GENERATION.md；尚待供应商对账、结构修复、真实供应商验收。chatStream/analyzeImage/estimateUsage仍为后续合同，不提供占位成功实现。
