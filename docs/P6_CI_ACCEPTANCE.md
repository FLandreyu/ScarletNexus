# P6 远端CI修复验收

2026-11-17收尾状态：P6核心Web范围已按总纲逐项验收通过，依据及保留局限见[P6_CLOSEOUT](P6_CLOSEOUT.md)。下文分批证据保留当时结论；实际生产投用和桌面发行版未完成，中低/未知风险继续跟踪。


2026-11-17，起点23f13ff。GraphFlow未提供，定向检查；保留用户.env.example/.gitignore修改。

验收标准：定位原始远端失败、保留固定摘要与所有Go/PG/浏览器断言；测试前加载真实镜像到Docker Engine，拉取失败则失败；验证本地脚本/整套Go，再核验推送后远端实际结果，不能把本地通过当CI通过。

## 首次推送失败原因

两次Quality gates均在新增TestUnselectedSessionResourcesSurvive失败：Engine报锁定Go父镜像不存在；前端成功，PG及浏览器因依赖失败未执行。日志133423010225、运行43902626793。BuildKit缓存父层并不保证Engine镜像仓库存在同一引用；上游后续容器测试才触发pull-on-missing，因此新增首个测试在干净runner失败。

verify_ryuk.py在启动测试前显式docker pull锁定linux/amd64的Go摘要，构建缓存不作为已加载证据。没有修改测试顺序、跳过保护测试或浮动标签。新增拉取先于测试与拉取失败不运行测试两项回归；配方、应用代码与Flyway不变。

## 实际结果

本地Linux Python/OpenSSL36项通过，全部上游Go及保护测试通过（22.116秒，.runtime-ci-ryuk-fixture.log）；缺少Engine夹具时先拉取的执行顺序及拉取失败关闭由两项新增单测覆盖，不声称本地曾删除共享镜像。Python runner无应用/依赖/配方变更，因此未重复本地84后端/前端/浏览器整套。

修复提交d2e67c9已推送main，[远端Quality gates](https://github.com/FLandreyu/ScarletNexus/actions/runs/43903457274)最终status=completed/conclusion=success，六个作业全部通过。前端格式/类型/14单元/构建，后端五镜像构建、整套Go验证及31单元+53真实PG集成通过，0失败/错误/跳过，Maven1分40秒（作业133425917648）。四组HTTP/TLS桌面/手机均通过六个浏览器故事，合计24；HTTP备份/隔离恢复与TLS安全/资源/SSE检查通过。TLS桌面18.9秒、手机26.0秒，延迟SSE首段0.036/0.035秒、终段2.536/2.535秒。

原失败运行及日志保留，不通过跳过或减少矩阵求绿；GitHub干净runner的失败已修复并完成远端整轮验收。此结论绑定d2e67c9源码，不冒称之后任意提交均通过。本地仅验收记录提交无需重跑应用测试；用户配置修改保留，未发布Release或部署生产。

P6不因单项CI修复自动完成，桌面发行版尚未实现；手动安全workflow未触发，不把Quality gates当安全扫描或生产验收。
