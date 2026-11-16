# 启用状态的本地协议验收

验收使用测试源集GenerationTestRuntime：真实Spring Boot、PostgreSQL和OpenAiCompatibleProvider，通过loopback HTTP调用测试协议服务；供应商响应是确定性的测试数据，不是模型推理，不证明真实供应商兼容性或质量。测试运行时不会打包进生产制品，不改变实际配置。

## 自动集成

`mvn -B verify`自动运行GenerationChainIT，继承既有数据库策略；Windows默认真实PostgreSQL17.11临时实例，`-Ptestcontainers`使用PG容器。测试启用完整配置、token/货币策略、真实登录/CSRF/生成HTTP API，验证成功候选、同键幂等只请求一次、非法Schema候选和截断失败、草案持久化与成本快照。未经确认不得出现发布版本或正式节点位置。

## 浏览器验收运行方式

从backend目录使用Java25（原Java21验收证据仍保留）：

```powershell
mvn -B test-compile exec:java '-Dexec.mainClass=dev.scarletnexus.GenerationTestRuntime' '-Dexec.classpathScope=test' '-Dexec.args=../.runtime/generation-acceptance.json ../.runtime/generation-stop-UNIQUE'
```

先保证`.runtime`存在，使用全新的stop文件名。运行时绑定loopback18080，元数据仅包含测试邮箱、端口和临时密码文件路径；密码不输出日志。不要输入个人学习数据或真实Key。

另一个终端从frontend目录启动Vite，设置`API_PROXY_TARGET=http://127.0.0.1:18080`，使用独立loopback端口15173。测试终端设置：

```powershell
$metadata=Get-Content -Encoding utf8 ../.runtime/generation-acceptance.json | ConvertFrom-Json
$env:GENERATION_ACCEPTANCE='1'
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:15173'
$env:E2E_EMAIL=$metadata.email
$env:E2E_PASSWORD=[IO.File]::ReadAllText($metadata.passwordFile)
npm run test:e2e
```

要求Node24及已安装Playwright Chromium。GENERATION_ACCEPTANCE=1仅选择generation-enabled.acceptance.ts，默认套件仍选*.spec.ts，不使用跳过的测试证明通过。

创建所指定的stop文件后，运行时主动关闭Spring、协议服务和临时PG，删除临时密码及元数据；Vite单独停止。已有stop文件将被拒绝，不自动覆盖或删除。测试截图/trace/日志保留在忽略目录，不入Git。

浏览器用真实API创建隔离技能，验证启用表单、费用确认、HTTP生成→候选编辑器→校验预览→明确发布，再验证非法生成错误和任务列表。仍不代表真实模型联调、生产Nginx部署或完整P2验收。
