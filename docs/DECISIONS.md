# 架构决策记录

## ADR-001 — 保持指定栈，采用模块化单体（2026-11-15）

采用 Vue 3 / Java 21 / Spring Boot 3.5 / MyBatis / PostgreSQL；不引入微服务、Redis 或向量库。原因：单用户产品需要可靠数据关系和可备份性，单体足够且易维护。属于总纲明确默认值，不构成架构变更。

## ADR-002 — 会话认证与同源代理

采用服务端会话 + HttpOnly/SameSite Cookie + CSRF；P1 使用 PostgreSQL 持久会话。避免客户端长期 token 生命周期复杂度。P0 先拒绝所有私有入口，不生成默认账号。Nginx 本地入口、生产 TLS 留给 P6。

## ADR-003 — 迁移按阶段扩展

目标 ERD 全量设计，P0 只迁移 users 和 audit_events。后续业务表随真实约束、代码和测试一起迁移，避免在尚无功能时冻结未经验证的结构。P0 不插入学习样本、管理员或掌握度。审计追加由 PostgreSQL 触发器保护。

## ADR-004 — 无 Docker 时使用隔离真实 PostgreSQL 验证

本机缺少 Docker。使用测试范围内 Zonky Embedded PostgreSQL 启动真实 PG 17.11 进程，临时目录/随机端口，测试结束关闭。Compose 和 Testcontainers 同时保留；两者的测试结果分别记录，不能以本地 PG 证明容器构建成功。禁止 H2 代替。开发工具仅位于被忽略目录，不注册系统服务、不改系统 PATH。

## ADR-005 — 版本锁定和开发运行时

Java21 已存在 D:/Java21，仅在项目命令进程设置 JAVA_HOME；CI/Docker 统一21。Node 本机25可执行，CI/容器选择24 LTS；npm 锁定依赖。版本已核对官方兼容说明和制品元数据。依赖升级需复跑门禁，不用浮动 latest。

## ADR-006 — P0 可见功能限于真实系统状态

状态页请求真实 MyBatis / PostgreSQL 健康接口；失败可重试，显示工程阶段和 AI 未接入。技能、图谱、学习等入口在对应阶段实现，不添加假数据卡片。P0 报告区分代码完成、测试通过和环境验收待验证。

## ADR-007 — GraphFlow 环境限制

已读取 GraphFlow 技能并查询会话工具列表，但工具未暴露，无法调用 context/plan/index。采用范围明确的文件操作并保留后续重建索引任务；不得称已完成图谱更新。未自动安装或改动用户全局 MCP 设置。

## ADR-008 — 测试依赖告警不使用跨主版本强制覆盖

npm审计发现Vue Test Utils的js-beautify → glob/minimatch/editorconfig开发依赖链中等级告警；`npm audit --omit=dev`生产依赖为0。保持锁定且能通过测试的上游版本，不执行`audit fix --force`或对glob强制跨主版本override，以免通过破坏兼容性隐藏告警。暂不处理不可信项目/任意glob输入；发布前复查上游修复。详细计数及边界在PROGRESS记录，不宣称全依赖无漏洞。

## ADR-009 — 干净环境依赖解析与构建缓存

容器构建实测发现MyBatis上游POM引入Sonatype快照/Spring里程碑仓库，在当前网络超时。项目仅使用已发布Central依赖，因此在backend/.mvn配置项目级Central镜像；不改用户全局settings。Docker用BuildKit缓存.m2。真实镜像构建及Testcontainers验证通过后关闭P0门禁。

## ADR-010 — 现有WSL2上的隔离容器验收

现成Ubuntu发行版可运行，无需重装Docker Desktop或启用系统虚拟化。官方Docker静态二进制与iptables依赖包只提取到专用缓存；进程、Unix socket、数据根和Compose项目独立，不注册系统服务。Windows代理只监听loopback，使用标准输入输出转发到WSL的loopback，未开放未认证Docker TCP API。Docker运行产生正常的临时桥接网络规则；不改持久系统网络配置。

Testcontainers在Maven容器内运行时传递TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE为宿主机真实socket，供Ryuk清理资源；保持Ryuk启用。浏览器以PLAYWRIGHT_BASE_URL直接访问Nginx产物，保证包含构建输出和CSP验证。测试数据只在独立验收卷，不当作用户进度。
