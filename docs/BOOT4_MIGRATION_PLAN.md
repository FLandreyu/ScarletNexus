# Spring Boot 4 迁移提案（尚未实施）

核对：2026-11-16。此文件是可供确认的计划，不代表迁移完成或安全通过。工具镜像修补独立推进，当前业务框架仍为Boot3.5.16。

## 原因与目标

现有Framework6.2.19残留CVE-2026-47884/47890；官方为6.2列出的修复属于商业支持。当前使用条件复核不代替补丁与受支持生命周期。推荐免费OSS路线：Spring Boot4.1.2、MyBatis Starter4.0.1、springdoc3.2.0，保留Java25/Vue3/PostgreSQL模块化单体，不购买服务、不部署生产。以上版本已在Maven Central核对；springdoc官方矩阵支持Boot4.1对应3.1/3.2，MyBatis4对应Boot4。

## 具体范围

1. 单独本地可回滚提交迁移POM/starter模块与包名。以Boot4.1.2 BOM管理Framework7及Security7、Tomcat11；移除旧Boot3的Tomcat覆盖，复核Jackson/JDBC覆盖，不能将Jackson2版本属性误套到Jackson3 BOM。
2. 首选官方Jackson2过渡模块保留现有AI Provider、JSON Schema校验、目录导入的严格解析，明确HTTP JSON转换器选择；同时验证springdoc3/Jackson3元数据路径。不得放宽重复键、未知字段、数字/字符串转换等安全合同来求通过。官方模块已弃用，需在后续阶段迁移Jackson3，不能把过渡当永久方案。
3. 调整Boot4模块化后变更的Web应用上下文/测试客户端/异常等包名，保留认证会话、CSRF、Cookie、流式传输、错误合同及用户隔离。仅在必要时迁移配置属性，逐条记录。
4. 原则上不改数据库。若真实Session或框架集成证明确需schema变更，追加Flyway而不改V1–V12；先在全新隔离库验证，增加旧备份恢复/升级故事，不接触用户学习库。
5. 维持所有手动业务与无Key模式，不进行真实模型请求；不启用springdoc新版本附带的MCP或其他非需求能力。

## 验收与回滚

- Maven编译、31单元与51真实PG集成全量运行，禁止用定向通过代替迁移全套；格式/类型/前端14单元/构建、真实TLS桌面/手机12旅程、SSE、附件、CSRF与角色权限验证。
- 增补JSON合同/日期/数值和错误响应回归；actual JAR/SBOM确认Framework版本及CVE状态，保留原始扫描，不因识别失败称零漏洞。
- 重新执行配对备份/隔离恢复与旧V12库读取。未通过不标P6完成，不进入P7或生产部署。
- 开始前提交已验收工具修补作为回滚点，迁移另提交；失败可回到该代码与镜像，不自动重写历史或删除数据卷。若有新数据库迁移，回滚仅使用隔离备份恢复方案，不盲目降级现有库。

## 需要确认的边界

`AGENTS.md`要求“重大架构变更、付费服务、生产部署、公开暴露服务、不可逆操作必须先确认”。Boot3→4同时涉及Framework/Security/Jackson/Servlet代际变化，因此本提案将其视为需要确认的框架大版本变更。确认只授权上述本地迁移与隔离验证，不授权付费、推送、生产或个人数据变更。

官方依据：[Boot4迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)、[MyBatis兼容要求](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[springdoc兼容矩阵](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)、[Spring XSLT公告](https://spring.io/security/cve-2026-47884/)、[Spring SSE公告](https://spring.io/security/cve-2026-47890/)。
