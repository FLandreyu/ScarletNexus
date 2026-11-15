# 安全方案

P4已实施：独立练习DTO不返回答案/隐藏提示；提示需已有本人作答、完整解析显式确认，曝光不能降低。作答/复核/证据/评估追加历史及同owner/node FK。模型建议只保存文本、不执行HTML/SQL/工具，不自动改状态；受限提示不直通模型长回答。

附件PNG/JPEG最大5MiB/1600万像素/边长8000，magic+MIME+解码验证，重新编码PNG丢弃原始元数据/尾随内容。文件随机UUID键、SHA-256、私有目录/独立卷、每用户默认256MiB配额；归档不物理删除。下载校验本人、状态、路径/符号链接/长度/checksum，固定image/png、no-store、nosniff。归档或隔离图片不可下载，不自动发给模型。重编码不是杀毒；崩溃孤立文件和备份恢复在P6补齐。

KaTeX固定0.19.1，trust=false、maxExpand100、maxSize20，单片段≤2000字符；原始文字/解析错误按文本展示，HTML不直通v-html。SSE整体deadline/体积上限/禁止重试，异步执行器有界；用户文本先落库、未知费用保守占用、过期RUNNING须明确恢复。真实质量与生产安全不以本地协议或这些措施冒充。

状态：P0/P1基线已实施，后续按阶段验收；本文件不代表完整生产安全审计。

## P1 已实现

密码仅从部署管理员指定的UTF-8单行文件引导，无默认账号/密码；BCrypt12，最少12字符且最多72 UTF-8字节。用户已存在则不重置、不读取新密码文件。初始化和审计在事务及数据库引导锁下执行。Set-LocalAdmin.ps1安全提示输入、创建新文件，不覆盖已有文件或.env。

JSON登录保持CSRF开启；先取/auth/csrf，同源Cookie+header提交。登录旋转会话ID并清理旧CSRF，退出使持久会话失效。Cookie SCARLET_SESSION为HttpOnly、SameSite=Lax，30分钟空闲过期，HTTPS部署需Secure。认证失败同消息，10次/来源IP/5分钟限流，429+Retry-After；限流为单实例内存，重启清零，代理下按连接源统一计数，不信任客户端X-Forwarded-For，生产需P6复核。

SessionUser仅暴露UUID/邮箱给API；Spring Security清除认证凭据后再持久化上下文。Login DTO的诊断表示强制脱敏，Web/Security/identity日志默认INFO，避免本机DEBUG=release引起敏感请求体日志。学习内容不作为HTML执行。

所有技能/节点/位置/关系操作由当前认证UUID限定；复合FK阻止跨用户引用，错误资源404；图写入与审计事务、用户行锁防并发环。节点/技能优先归档，引用移除不删除知识节点。已真实验证双用户API和直接SQL外键隔离。

## 保留的P0边界

Spring Security默认拒绝未认证私有请求，公开GET系统状态/健康/CSRF、POST JSON登录和显式开启的开发OpenAPI。禁用默认生成用户、表单登录和HTTP Basic；不启用跨域CORS，通过Vite/Nginx同源代理访问。会话Cookie在生产必须Secure；本地HTTP不能开启Secure。

所有错误使用安全消息和 requestId；不回传内部异常文本。requestId 只接受长度1..64的安全字母数字/点/横线/下划线，否则重新生成。健康细节关闭。Nginx 限请求体10MiB，设置 nosniff、frame 拒绝、referrer 策略、CSP；静态 HTML 不长期缓存。Compose 仅 loopback 暴露入口，不公开数据库/后端。配置项密码必须显式提供，不提供默认数据库密码。

## P2–P6 必须补齐

- 身份：P1上述措施已实现；P6复核TLS、限流部署、密码轮换/重置和会话管理。不要将cookie/CSRF/密码值写日志。
- 隔离：服务层使用当前用户限定全部私有查询，复合 FK 约束同 owner。跨用户资源统一404；安全测试使用两个账号。变更请求验证所有嵌套引用。不得信任前端隐藏入口。
- 数据：归档优先、审计追加；发布/导入事务与幂等；草案 revision；仅允许应用账号最小权限，生产 Flyway 使用独立迁移角色。
- 附件：magic bytes / 类型白名单 / 体积与像素限制、隔离目录、随机键、checksum、下载鉴权、禁止路径拼接/执行。后续 S3 私有桶、短有效签名不得绕过服务鉴权。
- AI：参见 AI_DESIGN，SSR​F 防护禁止未审查的 URL 抓取和任意 provider 目标；provider 地址仅管理员部署配置，限制出站目标；prompt 不可信，不执行输出。
- 运维：环境变量/secret store 注入，仓库不保存 `.env`；日志脱敏、备份加密、恢复演练；生产 TLS/HSTS、速率限制、超时、监控及漏洞检查，生产部署必须确认。

登录和手动图谱已做隔离数据库与真实浏览器验收，非渗透测试或完整生产安全审查。自动验收账号/Java技能只在独立测试卷，不当作用户真实记录。首次生产前完成依赖漏洞评估、镜像digest固定与访问控制复核。
