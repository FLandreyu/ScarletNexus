# P6 直接 TLS 终止与资源配置

`docker-compose.tls.yml` 是可选配置：合并默认 Compose 后，入口变为 `127.0.0.1:8443 → Nginx:443`，替换原 HTTP 映射，不额外开放公网。需要 Compose ≥2.24.4（使用 `!override`）；缺少证书文件配置或文件不存在时拒绝启动。默认 HTTP 配置保持原样。本轮仅隔离本机验收，未部署生产。

## 配置与启动

先按 README 配置独立数据库和管理员。由维护者在私有目录准备 PEM 完整证书链和匹配私钥；生产证书应覆盖选定域名、来自受信任 CA，并设置最小文件读取权限。不要把私钥写入仓库、镜像、聊天或日志。容器只读挂载两份文件，不自动签发/续期或安装证书。

在私有 Compose env 文件增加以下路径（示例只有路径，没有密码/密钥内容）：

```dotenv
TLS_CERT_FILE=/private/certificates/fullchain.pem
TLS_KEY_FILE=/private/certificates/privkey.pem
HTTPS_PORT=8443
```

```sh
docker compose --env-file /private/scarlet.env -f docker-compose.yml -f docker-compose.tls.yml config --quiet
docker compose --env-file /private/scarlet.env -f docker-compose.yml -f docker-compose.tls.yml up --build -d --wait
# 结束时保留数据卷；后续命令也必须带相同 env、文件和项目名。
docker compose --env-file /private/scarlet.env -f docker-compose.yml -f docker-compose.tls.yml down
```

Windows 辅助脚本支持 `-ComposeFiles @('docker-compose.yml','docker-compose.tls.yml')`。原生 Docker 与既有 WSL 分支均按顺序传文件；证书路径必须是实际 Docker 宿主可读取的路径，WSL 验收使用项目内相对路径。`Verify-Browser.ps1` 同样接收 ComposeFiles，避免浏览器项目之间重建时意外恢复 HTTP。

新空库可合并已验收的 roles overlay，见 [DATABASE_ROLES](DATABASE_ROLES.md)；本批没有对现有验收库更换角色，也没有执行生产权限迁移。

## TLS、安全 Cookie 与代理边界

Nginx 仅启用 TLS 1.2/1.3，TLS 1.2 限 ECDHE+AES-GCM；禁用会话票据。HTTPS 返回 HSTS `max-age=86400`，不包含 includeSubDomains/preload，避免自动影响其他子域；正式长期策略应在域名与续期方案确认后调整。CSP/nosniff/frame/referrer 规则与默认入口保持一致。无外部 HTTP 重定向端口，发往 HTTPS 端口的明文请求被拒绝。

overlay 强制 Secure Cookie，应用仍用 HttpOnly/SameSite=Lax 和 CSRF。浏览器至网关 TLS，网关至后端 HTTP 仅在本机 Docker 私有网络中；不是跨主机加密方案。后端不映射宿主端口，显式 `SERVER_FORWARD_HEADERS_STRATEGY=none`，不根据用户提供的转发头改写身份或协议。网关清除 Forwarded/转发 Host/Port，以实际连接源重写 X-Forwarded-For/X-Real-IP 与 HTTPS 协议；应用不消费这些头作为客户端身份。

登录精确路径增加按 `$binary_remote_addr` 的网关限流：10 请求/分钟、允许短时突发 10 次，超限 429；计数覆盖包括缺失 CSRF 的请求，伪造转发头不改变桶。后端原有 10 次/5 分钟/连接来源限制保留，因此同一网关下仍共享后端桶。这是单用户直接访问配置，不能称为多用户分别计数、分布式限流或代理链支持。若前面再放 CDN/代理，客户端会聚合为其连接 IP，必须先重新设计并验证可信代理边界，不得直接开启任意 XFF 信任。

代理使用 HTTP/1.1、关闭缓冲/缓存、130 秒读取间隔超时；应用仍执行自己的完整调用 deadline。测试源延迟 SSE 只验证网关传输，不证明真实模型质量。访问日志只保留请求号、方法、状态、耗时；TLS 网关错误日志提高至 crit，减少请求 URI/查询出现在请求级错误日志的风险，代价是排错详情减少。日志轮转沿用 10m/3。

容器内部 `127.0.0.1:80` 只允许读取系统状态供现有 monitor 使用，不发布 HTTP、不代理私有 API；外部 actuator 返回 404。

## 资源基线

| 服务 | CPU 上限 | 内存上限 | PID 上限 |
| --- | --- | --- | --- |
| PostgreSQL | 1 | 512 MiB | 128 |
| Spring Boot | 2 | 1024 MiB | 256 |
| Nginx | 0.5 | 128 MiB | 64 |

三服务 no-new-privileges；后端 cap_drop=ALL、只读根目录，64 MiB noexec/nosuid 临时目录和独立附件可写卷。JVM 最大堆按容器内存 60%、活动处理器数 2 设置，留出堆外/线程空间；Nginx 按实际 CPU 配额调整 worker。以上是本机功能验收的起点，不是压力测试结论或容量承诺。尚需为宿主、磁盘、附件配额、备份留存、swap/IO 和故障恢复预留资源；OOM/PID 达限可能中断请求，不会自动扩容。

## 可复现的隔离验证

仅对隔离验收账号/数据库运行。这些脚本不会配置生产域名或信任存储。CA_FILE 是只用于本轮的自签名 localhost 测试证书，KEY_FILE 是其私钥；不要使用个人学习库运行浏览器写入旅程。

```sh
python3 scripts/verify_tls.py --base-url https://localhost:8443 --ca-file "$CA_FILE" --email "$E2E_EMAIL" --password-file "$E2E_PASSWORD_FILE"
python3 scripts/verify_tls_runtime.py --project YOUR_ACCEPTANCE_PROJECT
python3 scripts/operations.py monitor --project YOUR_ACCEPTANCE_PROJECT
python3 scripts/operations.py attachments --project YOUR_ACCEPTANCE_PROJECT
# Linux Docker 宿主，使用已有本项目前端构建镜像；测试网络不发布端口。
python3 scripts/verify_tls_stream.py --ca-file "$CA_FILE" --key-file "$KEY_FILE" --web-image YOUR_BUILT_WEB_IMAGE
```

`verify_tls.py` 校验证书、主机名与 TLS 版本，验证不信任/错误主机名/明文被拒绝，执行真实登录/CSRF/退出和伪造转发头限流。测试证书须未加入全局信任库，否则负向信任断言会失败。不要以关闭证书验证替代它。

浏览器使用 `playwright.tls.config.ts`：仅接受 loopback HTTPS、显式 `E2E_TLS_SPKI`（测试证书公钥 SHA-256 的 Base64）和 `NODE_EXTRA_CA_CERTS` 文件。Chromium 只放行该测试密钥，API 客户端通过额外 CA 验证；不设置全局 ignoreHTTPSErrors、不改 OS 信任库。它是受限测试配置，不是生产浏览器信任方案。

```sh
export PLAYWRIGHT_BASE_URL=https://localhost:8443
export NODE_EXTRA_CA_CERTS=/absolute/private/test-cert.pem
export E2E_TLS_SPKI="$(openssl x509 -in "$NODE_EXTRA_CA_CERTS" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)"
cd frontend
npm run test:e2e -- --config=playwright.tls.config.ts --project=desktop
```

手机项目需重建同一隔离项目 backend/web 后运行，保留默认登录限额；Windows 可用 Verify-Browser 的 `-BaseUrl https://localhost:8443 -PlaywrightConfig playwright.tls.config.ts -ComposeFiles ...` 一次运行两项目。CI 增加 HTTP/TLS × 桌面/手机矩阵，每项使用独立应用；远端未执行前不声称 CI 已通过。

## 仍需完成

镜像digest固定、兼容补丁和全量扫描已实施，但仍有未关闭高/严重发现，见[SUPPLY_CHAIN_REVIEW](SUPPLY_CHAIN_REVIEW.md)。仍需实际部署域名、证书及续期/撤销、可信网络与防火墙、现有库迁移/独立迁移作业、备份专用权限、负载与容量、异地备份/告警接收、会话撤销与应急流程。生产部署、公网暴露、购买服务及现有库权限修改均须另行确认。本批本地 TLS 与资源验收不等于生产安全审计或 P6 完成。

配置依据：[Nginx HTTPS](https://nginx.org/en/docs/http/configuring_https_servers.html)、[代理模块](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)、[请求限流](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)、[Compose 合并与 override](https://docs.docker.com/reference/compose-file/merge/)、[服务资源限制](https://docs.docker.com/reference/compose-file/services/)。核对日期：2026-11-16。
