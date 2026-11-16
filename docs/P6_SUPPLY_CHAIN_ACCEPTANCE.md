# P6 依赖、镜像与供应链复核

本批验收标准先于实现记录。范围：npm生产/开发依赖、实际后端JAR中的Java依赖、三个运行服务镜像及构建/测试基础镜像。保持原技术栈与已执行迁移，不修改用户.env.example，不生产部署、不推送。

## 验收标准

1. 记录扫描工具固定版本/校验值、漏洞库更新时间、扫描日期和目标摘要。扫描失败/数据库不可用不能算零发现；所有严重度保留，不通过ignore-unfixed或笼统忽略掩盖结果。
2. npm分别报告生产与全部依赖；Java从最终打包JAR/镜像提取并核对代表性依赖，避免只有POM文件而未覆盖真实传递依赖。镜像报告区分OS、Java、构建/测试工具与生产运行范围。
3. 对高/严重发现核对供应商或维护者公告、受影响范围、已修版本与实际使用条件；有适用兼容补丁则修复并回归。仍未解决的风险明示并保留门禁，不宣称全栈无漏洞。
4. 运行/构建镜像以官方仓库的tag@sha256固定，登记多架构索引/平台边界与来源；构建日志和实际镜像可追溯。digest固定只防漂移，不自动带来安全修复，更新须扫描、回归。
5. 提供可复现扫描/一致性检查命令与CI入口，输出报告放忽略目录，不扫描/上传私有学习数据、口令或Key。评审过的例外需范围、理由、复查日期；未评审新高风险须明确失败。
6. 修改后运行格式、前端类型/单测/构建、Java25编译/单测/真实PG迁移/API（按中断期间用户授权的ADR-032）；固定镜像重新构建并验证真实桌面/手机旅程，适用时TLS检查。清理本轮隔离容器、保留数据卷，记录局限和后续。

## 实际结果

实施与功能回归已完成，**漏洞门禁仍未通过**；不将独立迁移作业、生产配置或整个P6标记为完成。详细版本、来源、发现与处置台账见[SUPPLY_CHAIN_REVIEW](SUPPLY_CHAIN_REVIEW.md)。

| 范围/命令 | 实际结果 |
| --- | --- |
| `verify_image_lock.py --remote` | 6个官方多架构索引逐项核对，消费者/精确libxslt包约束一致；只实测linux/amd64。工具归档、官方checksum及二进制摘要均核对；已安装分支校验通过 |
| `security_scan.py --project scarlet-nexus-p6-security-acceptance --output .runtime/security-complete` | 扫描完整、当前锁摘要匹配；漏洞库/Java DB分别2026-11-16 01:11Z/00:57Z更新。全量发现保留，95次高/严重目标发现，退出2；`.runtime-p6-risk-scan-complete.log`。不将其说成安全通过 |
| 实际JAR/前端镜像 | Jackson2.21.8、JDBC42.7.14实物识别；Tomcat10.1.62三个实际嵌套JAR补充SBOM扫描0项。最终后端2严重/4中、最终前端1中；libxslt实物1.1.43-r4。首次Java DB未识别Tomcat覆盖失败已保留，不作为零漏洞证据 |
| Node24格式/类型/14单元/构建 | 全部通过，原图表chunk体积告警保留；`.runtime-p6-risk-frontend.log`。生产npm审计0项、全部依赖5 moderate，报告由容器Node24重新生成 |
| Java25/Linux Testcontainers全量 | 第一次因tag@digest兼容校验初始化失败；集中声明官方镜像兼容后，31单元通过，51真实PG17.12集成中50通过、1失败，仅SystemIT旧断言期待17.11（7分20秒，`.runtime-p6-risk-testcontainers-r2.log`）。无跳过，失败证据保留 |
| 最终修正的SystemIT定向回归 | 保留容器17.12/原生17.11各自严格断言；`mvn -B verify -Ptestcontainers -Dit.test=SystemIT`最终31单元+6系统集成全部通过，0失败/错误/跳过，3分03秒（`.runtime-p6-risk-system-final.log`）。其它45项沿用上一轮同生产代码的通过结果；不冒称最终又全量跑了一次82项 |
| 固定镜像构建/运行 | 全新独立项目/卷，V12、三服务就绪；实际JRE Temurin25.0.5+8、PG17.12；Maven构建/测试镜像JDK为25.0.4.1，明确不同于宿主与JRE补丁号。无Flyway变更、无原有学习卷升级；`.runtime-p6-risk-compose-r2.log`、`.runtime-p6-risk-runtime-final.log` |
| 真实TLS浏览器 | 最终桌面6项36.2秒、手机6项40.3秒，全部通过，无API mock/JS异常/横向溢出；`.runtime-p6-risk-browser-final.log`。旧首轮12项亦通过，最终结果使用补过libxslt的镜像 |
| TLS/运维/SSE | 证书正反、错误主机名/明文拒绝、安全Cookie/登录/CSRF/退出/伪造头限流通过；资源/只读根/monitor UP，4引用/4附件无异常。Java25源文件启动要求包路径与文件路径匹配，修正夹具挂载后延迟SSE首段0.048秒/终段2.548秒、头清洗通过；未发真实模型请求。日志`.runtime-p6-risk-tls.log`、`.runtime-p6-risk-runtime-final.log` |
| 默认HTTP兼容 | 同项目恢复默认文件后桌面/手机系统状态2项5.6秒通过；未重复HTTP完整12项，`.runtime-p6-risk-http-browser.log` |
| Python/静态 | Linux Python/OpenSSL 9项通过，包括4项扫描失败关闭/覆盖/数据库时效单测；Python编译、CI YAML解析、Git diff检查通过。新增手动安全workflow，远端未执行 |
| 收尾 | 命名隔离Compose down，ps -a为空，数据库/附件两卷保留；无SSE临时容器或运行测试容器。原用户.env.example/.gitignore改动保持，原Java25专项与stash备份保留；无推送、生产部署、公开服务、付费调用或全局环境修改 |

本轮恢复了Java25的真实容器/业务/浏览器验证路径；原生Windows PG令牌失败是前一专项的历史记录，本轮未重跑原生PG，不把Linux结果冒充Windows原生成功。GraphFlow仍未暴露，未执行context/plan/index。剩余高/严重、Framework生命周期及未分类风险必须继续处置，P6不能进入最终通过或P7。
