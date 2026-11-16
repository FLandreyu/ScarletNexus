# P6 远端CI修复验收

2026-11-17，起点23f13ff。GraphFlow未提供，定向检查；保留用户.env.example/.gitignore修改。

验收标准：定位原始远端失败、保留固定摘要与所有Go/PG/浏览器断言；测试前加载真实镜像到Docker Engine，拉取失败则失败；验证本地脚本/整套Go，再核验推送后远端实际结果，不能把本地通过当CI通过。

## 首次推送失败原因

两次Quality gates均在新增TestUnselectedSessionResourcesSurvive失败：Engine报锁定Go父镜像不存在；前端成功，PG及浏览器因依赖失败未执行。日志133423010225、运行43902626793。BuildKit缓存父层并不保证Engine镜像仓库存在同一引用；上游后续容器测试才触发pull-on-missing，因此新增首个测试在干净runner失败。

verify_ryuk.py在启动测试前显式docker pull锁定linux/amd64的Go摘要，构建缓存不作为已加载证据。没有修改测试顺序、跳过保护测试或浮动标签。新增拉取先于测试与拉取失败不运行测试两项回归；配方、应用代码与Flyway不变。

实际结果将在本批进度记录；远端未成功前P6不标完成，桌面发行版尚未实现。
