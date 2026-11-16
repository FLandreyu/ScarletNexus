# P6 Boot4迁移验收

2026-11-16，用户了解迁移理由后确认继续；验收标准先见BOOT4_MIGRATION_PLAN。起点9dcc816，仅本地迁移/隔离验证，无付费模型调用、生产部署、推送或个人学习卷操作。GraphFlow未提供，使用定向检查，未执行context/plan/index。

## 实现

- Boot4.1.2、MyBatis Starter4.0.1、springdoc3.2.0；保留Java25，Web MVC/Session JDBC/Flyway及测试使用专用模块。Testcontainers按BOM切换2.0.5模块名；JUnit6.0.3，已有容器辅助类保留兼容API，弃用提示保留。
- Framework7.0.10/Security7.1.2/Tomcat11.0.27/Jackson3.1.7由Boot BOM管理，移除Tomcat10覆盖；显式保留Jackson2.21.8与JDBC42.7.14，Jackson2的BOM属性是jackson-2-bom.version。实际JAR扫描识别这些版本，不只依据POM。
- 官方spring-boot-jackson2弃用模块作为过渡，HTTP显式选择jackson2、UTC配置移至spring.jackson2；AI Provider/JSON Schema/目录导入仍用原严格解析。新增SystemIT验证真实HTTP转换器、UUID/Instant/LocalDate/高精度小数与401错误/requestId合同；已有严格转换、重复键、未知字段与隔离回归不放宽。Jackson3框架/OpenAPI路径与Jackson2都强制扫描。后续仍需独立迁移Jackson3，不将兼容层当永久方案。
- 调整Boot4应用上下文、安全自动配置包名与TestRestTemplate显式自动配置。没有修改业务数据库模型、V1–V12、认证范围或引入springdoc MCP模块。
- 备份drill可显式指定已有本地后端sha256内容ID，用于旧备份/新应用演练；默认仍用原后端。只创建新命名卷/网络，不修改认证备份或源库；同时输出原/新后端ID。浮动标签在创建资源前拒绝。

## 实际结果

| 检查 | 结果与证据 |
| --- | --- |
| Java25/Linux patched Maven `verify -Ptestcontainers` | 格式/编译/打包与**31单元+52真实PG17.12集成=83项通过**，0失败/错误/跳过，7分10秒，`.runtime-boot4-integration-r2.log`。含角色隔离、手动业务、AI测试协议与新增JSON合同；Ryuk启用并正常清理 |
| 初轮编译/格式 | Boot包名、LocalServerPort注解位置及一次UTF-8 BOM/导入排序问题导致失败，修正后上述全量通过。日志`.runtime-boot4-compile*.log`、`.runtime-boot4-integration-r1.log`保留；未跳过格式或测试求绿 |
| 前端受测Node容器 | format:check/typecheck/14单元/build全部通过，`.runtime-boot4-frontend.log`；既有ECharts chunk与弃用提示保留，无新增前端依赖 |
| Compose旧V12卷 | 复用隔离验收卷启动新后端，三服务Healthy，PG17.12/JRE25.0.5+8，`.runtime-boot4-compose.log`。无用户学习卷变更；不声称已验证旧浏览器会话跨Security代际保持 |
| TLS桌面/手机 | 桌面6项30.6秒、手机6项32.4秒全通过，无API mock/JS异常/横向溢出，`.runtime-boot4-browser.log`。未重复默认HTTP完整12项 |
| TLS/资源/监控/附件/SSE | CA/主机名正反、明文拒绝、Secure Cookie/CSRF/退出/伪造头限流通过；三服务资源、monitor UP、6引用/6文件无缺失损坏孤立；延迟SSE首段0.041秒/终段2.541秒及代理头清洗通过，`.runtime-boot4-runtime.log`。仅本地协议夹具，未调用真实模型 |
| 旧备份升级演练 | Boot3备份54表/6附件在**Boot4实际后端**上恢复成功、全表/文件一致、认证读取和6图成功，`.runtime-boot4-upgrade-drill.log`。报告明确原后端519a194...与受测新后端0556407...不同，没有把旧应用恢复成功当升级证据 |
| 新配对备份/恢复 | Boot4备份54表/8附件认证通过，隔离恢复逐项匹配，应用就绪、认证读取和8图成功，`.runtime-boot4-paired-restore.log`。原备份/密钥/样例只在忽略目录 |
| Python/静态 | 最终17项Python/OpenSSL测试通过，新增双Jackson覆盖、Tomcat11完整/缺包/混合版本检查、升级镜像标签拒绝；Python编译、六父层/消费者静态锁检查、CI YAML解析和Git diff通过，`.runtime-boot4-static.log`。远端CI未运行 |
| 最终漏洞扫描 | `.runtime/security-boot4-final`完整，捕获scanner_exit=2；当前产物**2项高/0严重，均Ryuk**，仍未通过。实际后端2中、前端1中，Tomcat11实际三包补充SBOM无发现；npm生产0/全部5 moderate。原Spring2严重已消失，无ignore/VEX或未修项过滤 |

首次Boot4扫描因旧规则只接受Tomcat10.1而失败，`.runtime/security-boot4`不完整，不作为安全通过证据。修正支持10.1/11.0，仍要求恰好core/el/websocket三包且版本一致，提取实际版本/SHA后扫描；Jackson2/3任一缺少识别都失败。完整计数与中低/未知项见SUPPLY_CHAIN_REVIEW。

## 边界与下一步

本批完成框架迁移与本地回归，**P6整体仍未完成**。没有重复Windows原生PG验收、渗透测试、生产证书/域名/权限切换或真实模型质量测试。后端中等级zlib/OGNL、Ryuk2高和工具中低/未知仍保留。下一步复核Ryuk兼容修复/公告归属，并完成独立迁移作业、备份权限与最终P6门禁。

收尾仅停止本轮Compose，ps -a无该项目容器、无Testcontainers残留，postgres-data/attachments-data两卷保留；升级/配对恢复临时资源由工具清理。用户.env.example/.gitignore原改动保留且不纳入提交，未修改全局工具或服务。回滚代码起点9dcc816，无新增数据库迁移，不自动回滚或删除卷。
