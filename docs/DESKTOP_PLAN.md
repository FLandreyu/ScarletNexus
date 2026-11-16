# Windows桌面发行版

2026-11-17用户授权开始开发；保留Vue/Java25/Boot4/MyBatis/PostgreSQL业务栈。GraphFlow未提供，定向检查；不使用测试临时trust数据库作为正式用户存储。

## 分步验收

- D1：Electron独立窗口、单实例、安全导航、本机同源网关，复用真实Vue/API。Node权限关闭/contextIsolation/sandbox启用；网关仅绑定127.0.0.1，禁止任意文件读取和远程后端。先单测边界与真实窗口冒烟，再验证已有隔离验收服务，不修改个人数据。本批入口显式开发模式，尚非独立可安装产品。
- D2：内置Java25和PostgreSQL17运行时及校验/许可证，专用持久目录、数据库SCRAM随机口令、首次账号设置、版本迁移/升级前备份、进程所有权/异常关闭/恢复。不能删除用户目录或误停其他进程；所有运行时只监听loopback。
- D3：离线Windows x64安装包、桌面/开始菜单图标、启动/卸载不删数据，干净Windows无Java/Node/Docker验证，完整桌面业务/升级回归。
- D4：供应链/包校验和/发行说明/CI制品，用户授权发布后才创建GitHub Release；代码签名/付费证书另行确认。不自动更新数据库主版本或安装系统服务。

采用Electron45.3.3（npm官方注册表核对并锁版本）作为新增桌面壳，复用Chromium呈现现有Vue界面，避免另写业务UI。构建工具与运行时逐项锁定，Electron自身也必须纳入后续供应链扫描，P6扫描不能代表桌面产物安全。参考[Electron安全指南](https://www.electronjs.org/docs/latest/tutorial/security/)、[PostgreSQL官方Windows分发说明](https://www.postgresql.org/download/windows/)。

本批不发布Release、不推送、不生产部署、不改用户.env.example/.gitignore。阶段完成度以最新验收为准，具体结果见PROGRESS。

## 2026-11-17 当前状态

D1、D2 本地切片已实现并验证。D2 真实 Windows 内置运行时和 Electron 首次设置/重启、备份恢复等证据见 [DESKTOP_D2_ACCEPTANCE](DESKTOP_D2_ACCEPTANCE.md)，普通用户/干净系统/安装升级等保留边界同步记录；不把本机成功当作安装发行完成。下一步 D3，随后 D4；尚未构建或发布供普通用户下载的安装包。上方最初规划保留为阶段来源。
