# Scarlet Nexus Desktop — D1开发入口

这是桌面窗口/本机网关的开发切片，尚未包含Java/PostgreSQL运行时和安装包；不能作为可独立发行的成品。阶段标准见[DESKTOP_PLAN](../docs/DESKTOP_PLAN.md)。

前置Node24、已构建frontend/dist，以及显式启动的隔离Web后端；不能使用个人数据库做冒烟测试。

```powershell
npm ci --prefix desktop
npm test --prefix desktop
npm start --prefix desktop -- --dev-backend=http://127.0.0.1:8089
```

可选真实窗口验证：设置DESKTOP_TEST_BACKEND为上述隔离服务、E2E_EMAIL为example.test测试账号、E2E_PASSWORD_FILE为本机测试密码文件，运行`npm run test:smoke --prefix desktop`。该脚本创建独立.runtime浏览器配置，确认Node隔离、真实数据库状态、登录/分析、外部导航阻断、单实例及退出网关关闭；截图在.runtime，不提交账号或数据。代码显式移除测试子进程的ELECTRON_RUN_AS_NODE，避免VS Code宿主环境将Electron误作为Node运行。CI本批只新增Windows网关单测/语法检查，尚未运行远端真实窗口验收。

网关只监听随机loopback端口；页面无Node访问权限，沙箱/上下文隔离打开；API同源转发并保留SSE，拒绝外部Host/Origin及内部运维路径。关闭窗口只停止本次网关，不关闭显式配置的外部开发后端。正常发行版不会要求用户提供该参数，D2将以拥有的子进程启动内置运行环境。当前packaged模式明确拒绝启动，不伪装为已装配运行时。

Electron安装首次可能下载二进制，依赖锁与安装包内置checksums验证官方资产；本机代理需要时可在当前进程设置ELECTRON_GET_USE_PROXY=1，不关闭TLS验证、不修改系统代理。实际离线安装包由D3交付。
