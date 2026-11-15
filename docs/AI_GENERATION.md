# 受控知识草案生成入口

POST `/api/v1/skills/{id}/graph/generation`接收`{topic:"学习主题与目标"}`，要求登录、CSRF和Idempotency-Key；模型、费率、预算、输出上限均从服务器确定。返回任务状态及草案ID，不自动发布。GET `/api/v1/ai/generation`返回当前生成开关状态，仍需登录。

默认关闭：未同时设置`app.ai.generation-enabled=true`、`app.ai.enabled=true`和正数`app.ai.tokens.daily-limit`时返回503/AI_NOT_CONFIGURED，不创建任务。启用真实Provider还须已有货币策略、密钥及retries=0，见AI_MONEY.md。当前Compose不转发这些配置，本轮没有启用真实模型或发送外部调用。

## Schema合同

GraphGenerationContract只转换仓库内置v1合同：所有对象属性列为required，additionalProperties=false；父节点使用string/null联合；schemaVersion使用integer enum；枚举补明确类型。剥离元数据、default及供应商兼容性不一致的格式/长度/数值约束。原始本地合同文件保持不变。

依据[官方Structured Outputs规则](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat)。这只是该合同的供应商Schema子集，不是通用JSON Schema编译器，也不保证所有兼容供应商都接受。拒绝/截断/错误仍明确失败，不自动退回无Schema生成。

返回数据继续经过现有GraphValidator的严格解析、UUID、必填/长度/枚举、引用及环校验，合法才保存草案；跨技能全图和源变化检查仍在发布预览/事务内执行。发送Schema中放宽限制不等于放宽数据库业务规则。

## Token和金额预留

TokenReservationPolicy和实际HTTP传输共用StructuredRequest序列化，包含模型名、system/user消息、Schema、JSON转义和输出上限8192。按实际UTF-8请求字节数加4096开销及8192输出预留；是保守字节估计，不是模型精确分词，不承诺严格上界。

服务端`app.ai.tokens.daily-limit`默认0（禁用），范围0–1000000000；`app.ai.tokens.max-request-bytes`默认100000，允许1000–1000000。输入太大或单次估计超过日配额时，在创建任务/发请求前拒绝。货币单次预留必须覆盖该输入估计及输出上限按配置费率计算的金额，否则AI_RESERVE_TOO_SMALL。实际用量仍可能不同，按真实返回结算，未知时保守占用，不承诺供应商硬限额。

## 页面和测试边界

草案页显示AI候选区域：未启用给出明确提示，手动入口继续可用；启用后显示主题及费用确认，未保存草案时禁止生成。用户确认后调用真实API，不使用假生成返回。一次提交失败可在当前页面使用同一幂等键重试，任务返回后下一次明确提交使用新键。页面刷新会丢失未完成请求键，可先到任务页检查是否已有任务，避免重复提交。

本轮验证Schema转换、UTF-8/JSON字节估计、未配置零请求、真实loopback HTTP传输和PG任务事务；浏览器验证关闭状态及手动发布回归。未对启用状态页面执行实际模型生成，也未证明供应商Schema接受或产出质量。需要授权并安全配置Key后再进行供应商联调；本轮不宣称P2整阶段完成。
