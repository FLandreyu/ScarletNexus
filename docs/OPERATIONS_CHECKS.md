# P6运维检查与生产前门禁

本轮不生产部署、不开放新端口、不安装定时任务。Python≥3.11、Linux Docker宿主与命名的运行中Compose项目即可使用：

```sh
python3 scripts/operations.py monitor --project YOUR_PROJECT
python3 scripts/operations.py attachments --project YOUR_PROJECT --report-file /private/new-audit.json
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

monitor检查三个服务的状态及后端liveness/readiness、Nginx至数据库的实际HTTP路径；健康退出0，不可用/未就绪退出2。监控输出仅状态，失败不输出连接异常详情。可由维护者的调度器收集非零退出并告警；尚未连接外部告警服务或安装cron。检查是当前时刻探测，不代表后续持续可用。

attachments读取DB引用并以只读挂载扫描私有卷，核对大小和SHA-256，分别报告missing/corrupt/orphans；任一发现退出2，工具无删除功能。控制台仅数量，私有report-file包含标识/文件键，以独占创建保存。QUARANTINED仅保留引用、不按可下载图片检查。扫描不是一致性快照，上传时可能产生瞬时孤立项，需维护窗口再次复核，不能拿一次结果直接删除文件。穿越、链接等异常由备份模块安全检查拒绝，不执行文件内容。

Compose三个服务统一json-file日志，单文件10m、保留3份，由Docker按写入轮转；修改仅在重建容器后生效。网关访问日志只记录请求编号、方法、状态与耗时，不记录URI/查询/IP/Cookie/Authorization。网关重新生成并向后端传递X-Request-ID用于关联。PostgreSQL设置log_error_verbosity=terse、log_min_error_statement=panic，减少错误详情/SQL中的私有数据；排错信息减少是明确取舍。不能据此承诺第三方组件任意日志都已脱敏，禁止调高Web/Security/SQL绑定参数日志或公开完整日志。

## 实际验收（2026-11-16）

- 独立Compose真实配置与运行通过：三个容器日志配置均10m/3，PG两项日志参数实际SHOW匹配；带测试查询标记请求返回200，网关日志不含标记，响应X-Request-ID可在日志关联。
- 正常monitor退出0；主动停止该验收后端后退出2，再恢复服务。10个数据库附件/10个文件，missing/corrupt/orphans均0；私有清单在忽略目录。5项Python测试（含备份测试）通过。证据.runtime-p6-operations.log、.runtime-p6-logging-check.log。

## 尚未完成的生产前门禁

1. 选定域名与证书/TLS终止边界，只允许可信代理连接应用；HTTPS启用Secure Cookie并验证SSE、CSP与实际登录/附件。当前HTTP/loopback Compose不是生产方案，不将示例直接对公网开放。
2. 新空库的独立迁移/受限运行角色方案及权限/真实业务测试见[DATABASE_ROLES](DATABASE_ROLES.md)。既有库切换、独立迁移作业编排、备份专用角色和生产恢复权限仍未完成；默认本地DB初始化角色权限较高。迁移不得通过改旧Flyway文件实现。
3. 确认资源限制、磁盘容量/备份留存、镜像digest、Java/镜像依赖风险与修复方案。npm生产依赖审计不能代替全栈审查。
4. 保留原始真实客户端身份的可信代理限流设计与验证；当前统一代理来源、单实例内存登录限流，不能宣称分布式防护。
5. 备份异地与口令独立保管、定期恢复、告警接收、会话撤销与应急回滚流程。在具体配置完成前不把文档清单计作生产安全验收。生产部署、购买服务和不可逆操作另行确认。
