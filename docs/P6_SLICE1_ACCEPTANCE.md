# P6第一批验收：分析、学习快照与知识目录迁移

日期：2026-11-16，Asia/Shanghai。**本记录仅验收第一批功能，不代表整个P6完成。** 完整备份恢复、监控日志及生产安全/配置说明仍未完成，不进入P7，不生产部署。

## 实际实现

- `/analytics`：日期/时区/技能筛选，真实已结束时长、任务完成分母/取消项、独立且明确RULE比对正确率、按日/技能聚合、当前ACTIVE路线自报完成。服务端REPEATABLE_READ一致读取，最多366天、验证技能归属。
- 能力按节点分别列最近RULE/SELF评估，带可信度/时间/证据，能追踪证据的结果、来源、独立性和原练习。自评、UNSURE、未复核不混作独立正确；重复复核不增加作答分母。
- 按需加载ECharts6.1.0，真实时间趋势/技能投入，SVG+richText tooltip，固定可访问说明及表格替代；不计算伪精确“综合掌握度”。主JS290.11kB/CSS79.46kB，图表懒加载包546.30kB（186.09kB gzip），保留超过500kB的构建告警。
- `/data`：下载本人40个白名单学习业务表快照JSON，最多25000行/原始行JSON8MiB；不含身份凭据、会话、Key、审计事件和附件磁盘路径/二进制。含私有学习文本、题目答案，页面提醒保存边界，响应no-store。**学习快照不是数据库/附件备份，不能在目录入口导入。**
- 知识目录JSON独立格式，1..100技能、1000节点、5000位置、3000关系、2MiB。严格字段/类型/UUID、重复JSON键、引用/分类与先修环校验。服务器保存预览，刷新可恢复；明确确认后事务新建全部UUID并保持跨技能共享节点，不覆盖既有知识或学习记录、不自动发布版本。
- V11保存目录预览与确认映射，源JSON/hash和确认结果不可变；用户行锁、幂等键、revision/hash冲突、所有权过滤、事务审计。V1–V10未改，无新模型调用。

## 最终实际验证

| 命令/范围 | 结果与本地证据 |
| --- | --- |
| Windows Java21 `mvn -B spotless:apply verify` | 31单元+50真实PG17.11集成=81，0失败/错误/跳过，格式/编译/打包通过，2分42秒；`.runtime-p6-backend-final.log` |
| Linux Java21/Maven `verify -Ptestcontainers` | 同31+50=81，0失败/错误/跳过，真实PG容器/Ryuk，4分41秒；`.runtime-p6-testcontainers.log` |
| Node24 format:check/typecheck/test/build | 全通过，14单元；`.runtime-p6-frontend-r2.log`。图表体积告警未隐藏 |
| `npm audit --omit=dev --json` | 本轮生产依赖0条发现；开发链原5项moderate仍在，不代表全栈或生产安全验收；`.runtime-p6-audit.json` |
| Compose实构建/升级 | 前后端镜像构建，既有独立验收卷升级V11，PG/backend/web均Healthy；`.runtime-p6-compose.log` / `.runtime-p6-web-rebuild.log` |
| P6单独桌面/手机旅程 | 2项通过，10.1秒；真实图表/证据、实际下载两种JSON、预览刷新恢复、确认新增目录且原任务DONE保留；`.runtime-p6-analytics-data-e2e-r2.log` |
| `scripts/Verify-Browser.ps1`全站回归 | desktop6项29.9秒 + mobile6项34.3秒=12项通过，真实Nginx/PG，无API mock/JS异常/全页横向溢出；`.runtime-p6-browser-final.log`，截图`.runtime/p6-browser-final/` |
| 视觉检查 | 已逐张查看桌面/手机analytics-evidence及catalog-confirmed完整截图，指标来源区分、SVG、目录边界提示/确认状态/导入链接可读；`.runtime/p6-analytics-data-r2-results/` |
| CI/脚本 | CI V11断言、桌面/手机独立matrix，YAML解析与PowerShell实际执行通过；未推送、远端CI未运行 |

新增3项分析PG故事验证结束时刻归属/暂停排除/取消分母、独立比对去重与自评分离、日期时区和双用户隔离；4项目录PG故事验证共享定义新增/原数据保留/幂等、严格输入和篡改冲突、故障回滚、快照隐私/归属。前端新增空分母与时长格式测试，既有图谱/预算/学习/练习/附件安全回归未跳过。

首轮真实问题及处理：测试自评说明不足既有最小长度，修正测试输入；Jackson默认数值转字符串仍被接受，新增Textual显式拒绝数值/布尔及禁止浮点转整数；ECharts自动ARIA覆盖固定图表名，设置真实ARIA说明后通过。首轮默认全套11通过/手机学习登录429：新增旅程使同来源登录超过默认10次/5分钟，新增隔离验收脚本按浏览器项目重建backend/web并保留卷、CI采用独立matrix，不调高生产限流。以上失败均未计作最终通过。

## 剩余门禁与下一步

1. 完整PostgreSQL+私有附件备份、加密/完整性清单、隔离恢复、新实例读取与校验和演练；没有完成前不能声称完整数据可恢复。
2. 监控/脱敏轮转日志、健康告警、孤立附件审计（先列清单、不自动删除）、生产TLS/Secure Cookie/最小权限/资源限制/镜像与Java依赖风险复核。
3. 全阶段最终回归与生产说明。生产部署、公网暴露、付费服务和不可逆操作仍另行确认。P5的AI文字建议及P4自动读图局限继续保留。

GraphFlow工具未提供，定向本地检查，未context/plan/index。用户.env.example改动保留、不纳入提交；日志/截图/学习样例/附件只在忽略目录。收尾停止本轮隔离Compose并保留PostgreSQL与附件卷，不影响其他项目或安装系统服务。
