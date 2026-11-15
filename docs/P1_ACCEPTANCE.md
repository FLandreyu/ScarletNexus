# P1 实施与验收清单

状态：**验收通过，2026-11-15**。前置P0已通过，提交2907fc9。不是全部产品完成，P2–P6未开始。

## 验收目标

1. 管理员通过部署配置的邮箱与密码文件初始化，无默认密码，重复启动不重置账号；密码不出现在日志或API。
2. 真实HTTP验证CSRF、登录/退出、统一认证失败、登录限流、HttpOnly/SameSite Cookie、会话ID轮换、PostgreSQL会话跨应用重启恢复。
3. 技能创建/读取/编辑/归档恢复，分页搜索；当前用户隔离，跨用户资源404。
4. 创建稳定UUID知识节点、跨技能引用、分类树移动与独立顺序、节点编辑与归档；错误引用、自引用、分类环拒绝。
5. 全用户先修DAG增删改查；A→B→C→A拒绝，错误归属引用拒绝；写事务与审计同步。
6. 桌面和手机创建Java技能及层级节点、建立依赖，刷新保留；未登录写入被拒绝。
7. 格式/类型/前端单测构建/后端单测与真实PG迁移和API集成/真实Compose浏览器验收；文档与实现同步。

## 顺序

身份与持久会话 → 技能API → 节点/树/依赖API → 页面 → 双用户隔离和图校验回归 → Compose和移动E2E → 记录实际结果并提交。

## 实际结果

- Windows真实PG与Linux Testcontainers均通过3单元+13集成，0失败/0跳过；Windows最终全套额外验证登录前旧CSRF在登录后被拒绝。
- 前端8项Vitest通过，格式/类型/构建通过，生产构建产物JS约170kB/CSS45kB，无新增前端依赖。
- Compose真实构建/启动通过：从P0持久卷升级到V3，管理员文件引导正常；空库V1–V3迁移由真实PG集成覆盖。
- Nginx直接提供前端产物，Playwright桌面/手机各2项（系统状态+完整手动技能树旅程），共4项通过，无API mock；截图已视觉检查。手机为390px Chromium模拟，不代表真机Safari验收；新建/编辑会定位并聚焦表单。
- 身份覆盖无默认密码、BCrypt12、引导不重置、Cookie HttpOnly/Lax、会话ID/CSRF轮换、退出及跨实例会话、统一失败和429限流。
- 图谱覆盖错误归属404、复合FK、分类环、全用户先修环、相反边并发仅一条提交、拒绝后无审计污染、节点共享而位置独立、稳定UUID重命名、引用移除保留节点、分页/搜索/归档恢复。

## 复现与证据

`scripts/Verify.ps1 -JavaHome D:/Java21 -NodeHome ./.tools/node-v24.22.3-win-x64`；Linux已有引擎时用`sh scripts/verify-testcontainers-in-docker.sh`运行同一后端容器测试。浏览器使用隔离账号E2E_EMAIL/E2E_PASSWORD与PLAYWRIGHT_BASE_URL，流程见README，不使用真实生产凭据。

本机日志`.runtime-p1-final-backend.log`、`.runtime-p1-testcontainers.log`、`.runtime-p1-compose.log`；JUnit XML位于backend/target。截图/报告在frontend/test-results及playwright-report（不入Git），包含登录、技能列表、知识树和系统诊断。测试账号和Java技能仅在独立验收卷；已停止验收容器，确认卷保留，不作为用户真实学习数据。

## 局限与下一阶段

P2草案/版本发布/AI、P3路线/任务/学习记录、P4证据、P5复习与P6导出备份尚未实现。单实例限流重启清零，节点库UI最多显示100项并提供搜索；密码重置/多用户注册未开放。节点定义+引用是两次请求，失败会保留定义并提示引用恢复。5项开发依赖moderate待兼容修复；无远端CI运行记录，GraphFlow工具仍不可用。生产部署另行确认。
