# P3验收清单

2026-11-15：P3阶段验收通过，P4尚未开始。实际证据基于Java21、PostgreSQL17.11、Node24及本地Docker环境；未推送或执行远端CI、生产部署、付费AI调用。

| 总纲交付与验收 | 实现与实证 |
| --- | --- |
| 目标 | 技能、成果、日期、优先级、状态，编辑/归档与revision；外来技能404 |
| 阶段与路线节点 | 阶段/节点排序、必修、目标等级、标题快照，真实关系表与FK；每版最多30阶段/300节点、节点不可重复 |
| 路线修订历史 | 新版本追加、旧版本不可改删、过期基版409；修订后旧任务仍指向v1，笔记/进度/时段保持 |
| 选择路线与进度 | 每用户一个ACTIVE选择；可恢复旧版，必修未完成不能结束；进度自报、独立于能力评估 |
| 任务与工作台 | 手动/路线任务，安排与状态保存，当前节点、原路线位置、先修与资料；无AI可运行 |
| 学习时段 | 开始/暂停/继续/结束，服务器毫秒累计；同键不重复记时，错误复用/旧revision409；并发两次开始仅一次成功 |
| 笔记与资料 | 纯文本笔记、revision防覆盖、未保存离开提示；http(s)链接校验、编辑/归档，无服务端抓取 |
| 退出与继续学习 | 浏览器刷新及退出登录后恢复同一暂停时段和笔记；首页定位未结束学习，继续任务不受最近已完成任务截断 |
| 隔离/事务 | 未登录401、外来任务/笔记/目标404、复合FK拒绝跨用户；审计故障503且笔记内容/revision一起回滚 |

## 实际运行

- Linux Java21/Maven Testcontainers：`wsl -d Ubuntu --cd /tmp -- sh /mnt/d/MyStudy/幽默项目/ScarletNexus/.runtime/testcontainers.sh`，执行仓库`scripts/verify-testcontainers-in-docker.sh`，25单元+23真实PG集成=48项，0失败/0跳过，BUILD SUCCESS，3分44秒。日志`.runtime-p3-testcontainers.log`。真实启动PG17.11容器与Ryuk，不是内存SQL替代。
- Windows Java21：新增功能先完整回归25单元+21PG集成通过；补入继续任务与审计回滚测试后，最终`mvn -B spotless:apply verify -Dit.test=LearningIT`运行25单元+7项P3真实PG集成，通过，日志`.runtime-p3-final-learning.log`。最终全量结果以上述Linux48项为准，不将Windows分轮重复结果相加。
- Node24：`npm run format:check`、9单元、`npm run build`（含vue-tsc）通过。前端JS约227.22kB、CSS46.86kB。P3交互通过真实浏览器验收，不添加只复述格式化函数的单测。
- 临时PG+Spring+Vite：默认6项桌面/手机Playwright全部通过，`.runtime-p3-e2e.log`。无真实Key/无Fake Provider，服务在loopback。
- 持久Compose验收项目`scarlet-nexus-p0-acceptance`：后端/前端镜像实际构建，保留卷V7→V8迁移，三个服务Healthy；真实Nginx入口默认6项桌面/手机Playwright全部通过，`.runtime-p3-nginx-e2e.log`。截图查看过桌面暂停工作台与手机完成工作台，无JS异常/横向溢出。
- 本轮修复：Vue内联多语句经格式化后解析失败（移入函数）；新版本断言由V7更新V8；审计故障测试最初错误预期500，核对既有DataAccessException合同后改为503，确认实际回滚，未修改生产错误合同迁就测试。相对URL不再触发空scheme异常；首页继续任务改独立筛选。

## 边界与下一步

进度为自报，不自动评估能力。目标等级仅为目标，不限制用户探索先修之后的节点。路线是手动阶段列表，不提供自动生成、自动进度迁移、复杂路线依赖编辑或拖拽画布。完成任务、已学路线节点、结束时段分别保存。计时离开页面后继续，不代表专注检测；客户端显示为估计，数据库累计为准。笔记须显式保存，资料仅链接，附件和AI导师属于P4。P5提供时间预算调度、计划草案与复习。

GraphFlow仍未提供，不声称context/plan/index执行；远端CI配置已更新V8但未实际运行。既有安全/依赖及生产限制继续适用。本轮无新依赖、不更改V1–V7、不使用真实Key。收尾与后续任务见PROGRESS.md。
