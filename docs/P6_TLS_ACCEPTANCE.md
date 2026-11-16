# P6 HTTPS 与资源约束验收

本批只实现可审查、可复现的独立 TLS overlay，并在本机隔离环境验收；不选择真实域名、不申请证书、不开放公网、不改变现有学习库角色。P6 整体仍待依赖/镜像风险复核等门禁，不提前标记完成。

## 验收标准（实现前定义）

1. 默认 HTTP Compose 保持兼容。显式 TLS overlay 替换而非追加入口端口，仅 loopback HTTPS；证书/私钥路径必填、只读挂载，禁止自动生成生产证书。
2. TLS 1.2/1.3 握手、证书链/主机名验证成功；不信任的证书失败。HTTPS 返回 HSTS/CSP/nosniff；外部不能走明文 HTTP 或读取 actuator。
3. 实际登录 Cookie 为 Secure/HttpOnly/SameSite=Lax；真实登录、刷新、CSRF 拒绝、退出、私有附件上传/读取及未登录拒绝均通过。前端桌面/手机完整旅程通过。
4. 网关不接受客户端 Forwarded/X-Forwarded-* 身份；按连接源 IP 限制登录请求，伪造转发头不能绕过。保留后端每连接来源 10 次/5 分钟限制，不冒称其已按真实客户端分别计数或支持分布式。
5. SSE 保持不缓冲与合理超时；用测试源协议流确认首段在终段前到达，不调用真实付费模型，不将此称为模型质量验证。
6. 三服务 CPU/内存/PID 限制在 Docker inspect 生效；后端只读根目录与临时目录/附件卷兼容实际业务；健康/附件只读运维检查可用。
7. 执行格式、前端类型/单测/构建、后端编译/单测与真实 PostgreSQL 迁移/API 回归，记录实际命令、结果与未验证项。保留用户 .env.example 修改，隔离服务收尾停止、卷保留。

## 实际结果

验收日期：2026-11-16，Asia/Shanghai。本批范围的本地验收通过，P6整体仍未通过。

| 命令/范围 | 实际结果 |
| --- | --- |
| `scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64` | Node24格式/类型/14单元/生产构建通过；Windows Java21编译/格式/31单元+51真实PG17.11集成=82通过，0失败/错误/跳过，后端3分05秒；`.runtime-p6-tls-verify.log` |
| TLS overlay `config` / `up --build -d --wait` | 使用原独立验收卷，V12、三服务就绪；入口只有127.0.0.1:8443→443。缺少TLS_CERT_FILE配置明确拒绝，文件挂载只读；`.runtime-p6-tls-compose.log`、`.runtime-p6-tls-runtime-final.log` |
| `scripts/verify_tls.py` | TLS1.2/1.3实际握手；CA/主机名验证通过，不受信任证书/错误主机名/明文请求拒绝；HSTS/CSP/nosniff、安全Cookie、真实登录/CSRF/退出、伪造头限流全部通过；`.runtime-p6-tls-security-final.log` |
| `scripts/Verify-Browser.ps1`（TLS文件组、tls Playwright配置） | 真实Nginx桌面6项43.9秒、手机6项44.0秒，共12通过；完整手动业务/图片上传读取/刷新/未登录图片拒绝/数据管理，无API mock、JS异常、横向溢出。已查看桌面/手机练习证据完整截图；`.runtime-p6-tls-browser.log` |
| `scripts/verify_tls_runtime.py` / monitor / attachments | 三服务实际CPU/内存/PID/no-new-privileges、后端cap/只读根/tmpfs、无DB/后端端口映射检查通过；monitor全部UP；最终16引用/16文件，无缺失/损坏/孤立；`.runtime-p6-tls-runtime-final.log` |
| `scripts/verify_tls_stream.py` | 独立内部Docker网络、不发布端口，使用测试源延迟SSE；首段请求后0.079秒、终段2.580秒，转发头清洗通过；仅网关协议，不是付费模型；`.runtime-p6-tls-stream-final.log` |
| 默认HTTP兼容 | 以原默认文件重建backend/web，HTTP状态与非Secure开发Cookie正确；桌面/手机system旅程2项5.4秒通过；只对默认入口做smoke，未重复默认全旅程12项；`.runtime-p6-tls-http-compat.log`、`.runtime-p6-tls-http-browser.log` |
| Python/配置 | Python编译、PowerShell语法、CI YAML解析通过；Linux Python3/OpenSSL 5项通过（`.runtime-p6-tls-python.log`）。Windows首次跑既有备份测试缺少openssl可执行文件导致2项环境错误，未当作通过；改在支持环境执行。最终SSE测试源另经Java21 spotless/test-compile通过 |
| 收尾 | Compose down后命名项目ps -a为空，postgres-data/attachments-data两卷保留；测试SSE容器/网络已清理，无OS信任修改/系统服务安装/付费调用/推送/生产部署；`.runtime-p6-tls-down.log` |

CI新增HTTP/TLS×桌面/手机矩阵、TLS协议/资源/运维检查；仅本地解析与等价运行，远端未执行。本批没有数据库/生产业务代码变化，不重复Linux Testcontainers全套；最新Windows全量与Linux Compose真实PG/浏览器结果如上，不挪用前轮全套证据。

GraphFlow工具仍未暴露，按项目规则定向检查；不能声称已context/plan/index。用户原.env.example修改完整保留，不纳入提交。证书/口令/账号样例/截图/日志均在忽略目录，不入Git。未验证实际生产CA/域名/续期、外部代理链、容量/故障负载及依赖/镜像风险；边界和下一步见[TLS_DEPLOYMENT](TLS_DEPLOYMENT.md)。
