# P6 Ryuk客户端SDK兼容性调查（未迁移）

2026-11-16，先定义门禁再实现。起点6d88582，用户原.env.example/.gitignore改动保留。GraphFlow未提供，定向检查，不声称context/plan/index。

## 标准

1. 保留上游0.14.0源码与摘要，迁移官方拆分客户端SDK，所有补丁/Go模块版本与校验和可审查并固定；不修改扫描ignore/VEX、禁用Ryuk或删除漏洞元数据求绿。
2. 上游无daemon的单元/协议测试通过，真实Docker资源清理验证不能删除其他会话资源；以真实Java25/Testcontainers及PostgreSQL验证兼容性，保持原应用业务不变。
3. 固定来源重建四工具，指纹随补丁/依赖锁改变，所有入口消费受测产物；保留Go构建信息。扫描必须识别新客户端版本，不接受旧docker/docker或识别缺失。
4. 重跑格式/前端类型/14单元/构建、后端31单元+52真实PG集成、TLS浏览器和运行探测；完整全栈扫描保留所有严重度与父层基线。门禁结果按实际报告记录，不提前标P6完成。
5. 更新进度/决策/复现与风险台账、本地回滚提交不推送。仅隔离资源，保留验收卷，无付费调用/生产或个人数据操作。

## 结果

官方稳定Ryuk0.14.0仍使用docker/docker28.3.3，main也仍使用28.5.2，公告对旧模块没有列出修复版。调查官方拆分client0.6.3（2026-11-13，提交2ca8f919108134b1d6e79ed95b1c55ffc32f9c2c）及api1.57.0（28396bf4817db0f6619d6656cded0877aae11293）。它们改变了List/Remove/Ping选项与返回类型，旧filters.Args不在新API中；上游测试还依赖旧build/jsonmessage路径。忽略目录候选的依赖解析出现旧root模块与新api子模块包歧义，未完成编译或任何清理兼容性验证，不能宣称新版本可用。

因此没有将试验源码/Go依赖锁/镜像接入项目，也不以改写关键清理逻辑的本地分支换取扫描零告警。现有受测Ryuk和所有来源指纹不变，保留2项高告警、原始报告和2026-11-23复查待办；优先等待上游可维护迁移或单独评审测试工具分支。以前的daemon/client符号证据没有转成ignore或VEX。临时Go下载容器已结束，候选只在.runtime中，不提交源码缓存。

证据`.runtime-ryuk-sdk-compile.log`，源码归档SHA仍核对8753af...77fbb，版本元数据从官方Go代理核对；不是成功编译/测试的证据。官方来源：[Ryuk依赖](https://github.com/testcontainers/moby-ryuk/blob/main/go.mod)、[客户端模块](https://github.com/moby/moby/tree/client/v0.6.3/client)、[API模块](https://github.com/moby/moby/tree/api/v1.57.0/api)、[archive公告](https://github.com/advisories/GHSA-x86f-5xw2-fm2r)、[cp公告](https://github.com/advisories/GHSA-rg2x-37c3-w2rh)。

本轮转而完成已规划的独立迁移作业，见P6_MIGRATION_JOB_ACCEPTANCE；Ryuk风险仍未关闭，P6不能标记完成。
