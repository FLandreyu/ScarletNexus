# P2结构化Provider传输

生产代码包含真实HTTP适配器与默认关闭的浏览器生成入口；本地协议服务只存在于测试。手动草案不依赖Provider。实际验收状态见P2_ACCEPTANCE.md。

## 合同

`AIProvider.generateStructured(instructions,input,schema)`调用配置的`/chat/completions`，传递JSON Schema、固定单次输出上限8192 tokens，要求完整`stop`结果和JSON对象。返回候选文档和供应商用量；缺失用量为UNKNOWN，不填零或猜测价格。调用者仍须执行本地Schema和图规则验证，不能将结果直接写入正式图谱。

协议参考：[Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。默认`app.ai.protocol=json-schema`发送内置v1合同转换后的严格Schema；不支持时明确失败，不自动切换协议。

DeepSeek需显式配置`app.ai.protocol=deepseek-json`（环境变量`APP_AI_PROTOCOL`）：发送`response_format.type=json_object`、`max_tokens=8192`和`thinking.type=disabled`，Schema写入系统提示，服务端继续执行完整Schema与图校验。JSON模式不保证符合Schema，非法候选仍以失败结束，不能写正式图谱。估算与实际请求复用同一序列化，包括提示中的Schema。参考[DeepSeek JSON模式](https://api-docs.deepseek.com/guides/json_mode/)与[Chat接口](https://api-docs.deepseek.com/api/create-chat-completion/)。未知协议启动失败，不自动降级。V4.1 Flash的官方API模型ID为`deepseek-flash`，见[模型列表](https://api-docs.deepseek.com/api/list-models/)。

## 服务端配置

Spring配置项为`app.ai.enabled`（默认false）、`app.ai.base-url`（包含/v1路径）、`app.ai.model`、`app.ai.key`、`app.ai.timeout-seconds`（默认60，范围0.1–120秒的传输构造器；环境配置使用整数秒）、`app.ai.retries`（默认0，最多2）。Spring环境变量对应`APP_AI_ENABLED`等。无默认供应商/模型/密钥；不提供真实Key示例，不在前端保存凭据。未启用时调用立即返回AI_NOT_CONFIGURED。启用但配置不全会启动失败。

当前Compose不转发这些变量。生成HTTP入口默认关闭，需另配generation-enabled及服务端token/货币策略，见AI_GENERATION.md。不得仅配置Key就绕过预算和付费授权。

只允许HTTPS（测试/本地模型允许loopback HTTP），拒绝URL凭据、query、fragment和重定向。请求整体deadline包含重试等待；仅429/502/503可重试，支持秒数或HTTP日期Retry-After，超出剩余deadline直接返回可恢复失败。网络异常/超时不自动重发，避免不明确的重复计费。响应最多2MB；错误仅返回固定代码，不回显上游正文/Key/prompt。超时或错误不表示供应商未计费。

## 尚待实现

内部持久化任务、token/货币预留结算、价格快照、保守恢复和用户确认的有限结构修复已实现，见[AI_JOBS.md](AI_JOBS.md)、[AI_MONEY.md](AI_MONEY.md)。启用真实Provider必须配置货币策略且重试为0。内置v1 Schema转换、字节token估计及默认关闭的生成入口见AI_GENERATION.md；供应商账单对账仍待实现，真实模型验收见P2_ACCEPTANCE.md。chatStream/analyzeImage/estimateUsage仍为后续合同，不提供占位成功实现。
