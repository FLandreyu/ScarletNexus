# P6 构建与启动工具修补

2026-11-16，验收标准先于实现定义。本批不迁移Spring大版本、不购买支持、不生产部署、不改已有Flyway/学习卷；保留.env.example/.gitignore的用户改动。GraphFlow未提供，使用定向检查，不能声称context/plan/index。

## 验收标准

1. 以现有95次高/严重目标发现为基线，保留上游报告；重建实际使用的构建/测试/数据库启动工具，重新扫描，不使用ignore或隐藏未修项。
2. Go编译器、gosu/Ryuk源代码、Guava、npm及OS补丁均固定版本与摘要；不因压缩/识别缺失而宣称零漏洞。记录实际包版本、Go构建信息和对应工具二进制校验值。
3. 构建、测试、Compose、SSE与安全扫描入口一致消费受测工具镜像；缺少构建产物或来源指纹不匹配时失败，不回退到旧工具。上游层和最终有效产物分开记录，只有真实补过的产物才能替代其上游门禁目标。
4. 真实PostgreSQL迁移/权限/业务、31后端单元、前端格式/类型/14单元/构建、TLS桌面/手机全旅程、SSE、资源与附件探测通过。数据库镜像若变更启动工具，补配对备份/隔离恢复演练，不覆盖源卷。
5. 扫描完整性、新鲜度、Java/Tomcat覆盖和所有严重度保持；高/严重未关闭则门禁仍失败，P6不完成。框架大版本需要先给出具体迁移计划并按AGENTS取得确认。
6. 更新进度、台账和复现命令，创建可回滚本地提交，不推送；停止本轮隔离容器并保留数据卷。

## 实际结果

本批工具修补与回归完成，**安全门禁仍未通过，P6保持开发中**。没有实施Boot4，具体方案见[BOOT4_MIGRATION_PLAN](BOOT4_MIGRATION_PLAN.md)。

| 命令/范围 | 真实结果 |
| --- | --- |
| `python3 scripts/security_images.py --build` | 四个目标构建及指纹核对通过；父层digest固定，资产SHA固定；Go1.27.4重建gosu1.20/Ryuk0.14.0；Maven精确OS包及Guava33.7.2-jre、Node内置npm11.21.2。日志`.runtime-p6-tools-build-r2.log` |
| `verify_image_lock.py --remote` | 六个官方索引摘要/消费者/源码资产约束通过，`.runtime-p6-tools-lock-remote.log`。第一次未传WSL代理的registry请求超时，传显式代理后通过；新增60秒请求超时，保留失败日志 |
| Java25 patched Maven `verify -Ptestcontainers` | 格式、编译、打包、31单元+51真实PG17.12集成全量通过，0失败/错误/跳过，4分48秒；Ryuk正常自动清理。`.runtime-p6-tools-backend.log`。不声称重新运行Windows原生PG |
| 受测Node容器前端检查 | 新临时容器中从lockfile安装，源目录只读；format:check/typecheck/14单元/build全部通过。既有ECharts chunk体积及whatwg-encoding弃用提示保留。`.runtime-p6-tools-frontend.log` |
| Compose/TLS浏览器 | 复用本轮独立验收两卷、无Flyway改动，V12/PG17.12/JRE25.0.5+8；真实TLS桌面6项29.5秒、手机6项32.8秒全通过，无API mock/JS异常/横向溢出。`.runtime-p6-tools-compose.log`、`.runtime-p6-tools-browser.log`。未重复默认HTTP12项 |
| TLS/资源/附件/SSE | Linux证书信任正反、主机名/明文拒绝、Secure Cookie/CSRF/退出/伪造头限流通过；三服务资源限制、monitor UP、4引用/4文件无缺失损坏孤立；延迟SSE首段0.038秒/终段2.538秒，代理头清洗通过。`.runtime-p6-tools-runtime-r2.log`。Windows探测未返回后终止仅本轮进程，未将其称为通过；Linux实测成功 |
| 配对加密备份/隔离恢复 | 54表/6附件全量一致、应用就绪、认证读取及6张私有图片可读；源卷未覆盖，隔离恢复资源自动删除。`.runtime-p6-tools-restore.log`。备份及密钥仅在忽略目录 |
| Linux Python/OpenSSL | 13项通过，包括有效产物不能移出门禁、缺失/过期/用途/平台错误镜像拒绝与无旧版回退。Go符号核查确认Ryuk含client而无daemon实现、Engine29.9.3；只记录证据，不抑制2项高告警 |
| 最终全量安全扫描 | v2报告`.runtime/security-tools-final`完整；当前产物4次高/严重（Spring2严重、Ryuk2高），全部含上游基线45次。Maven/Node/PG受测派生镜像零高/严重；扫描强制识别gosu与Ryuk的Go1.27.4。npm生产0、全依赖5 moderate。旧95和新4范围不同，详见SUPPLY_CHAIN_REVIEW |

配方SHA-256：`1d3458b40884099ed24d04a8aa42c30d4d0a216ae7149576c97020eaedf9f9ed`；父镜像锁SHA-256：`69e79c45f58f775e4b64d7405fe41760a161c87aad5fde8c6a8cb73446633a27`。实际ID、包清单、原始发现与各报告SHA在忽略报告目录，不把本地tag当registry digest。Ryuk二进制SHA-256为`793f00361a5b97892abd732a55512bfacc0680809d4f48b7286da2d9f08a0c56`。

收尾重新校验13项Python测试、编译/YAML及完整扫描，`.runtime/security-tools-confirmed`同样complete=true/门禁4项，明确捕获`scanner_exit=2`（.runtime-p6-tools-final-checks.log）。扫描目标身份与配方未改变；该复核确认失败门禁没有因摘要生成顺序而变绿。

## 复现与使用边界

```sh
python3 scripts/verify_image_lock.py --remote
python3 scripts/security_images.py --build
sh scripts/verify-testcontainers-in-docker.sh
# 准备私有.env/管理员后启动独立Compose，再扫描
python3 scripts/security_scan.py --project YOUR_ACCEPTANCE_PROJECT --output .runtime/new-security-report
python3 scripts/verify_ryuk_scope.py
```

直接Compose需要预构建上述本地镜像，数据库设置pull_policy=never；Invoke-Compose.ps1的up/build自动构建工具。配方变更导致标签指纹过期时重建；验证入口返回不可变image ID。CI的后端、Compose与安全任务增加构建步骤；前端宿主/浏览器任务仍按各自Node环境运行，不宣称修补了用户全局npm或所有GitHub runner工具。远端CI没有运行。

本地Go下载曾因默认构建网络超时；本轮显式设置`SCARLET_BUILD_NETWORK=host`并传入本机HTTP(S)_PROXY后成功。此选项仅作用于构建RUN下载，未公开业务端口、未保存代理凭据或安装系统服务；默认CI仍为default网络。需代理时自行显式选择，不自动切换。精确apt/npm/归档源消失则失败，应复核锁，不能回退旧版本。仅linux/amd64受测。

当前剩余2项Spring和2项Ryuk仍阻止门禁通过；中低/未知也未全部消除。无付费模型调用/生产部署/推送，用户.env.example/.gitignore原改动不入本提交。清理仅本轮隔离Compose与临时容器，保留数据库/附件卷和扫描证据。
