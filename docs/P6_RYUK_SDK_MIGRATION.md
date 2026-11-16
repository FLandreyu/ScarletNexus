# P6 Ryuk拆分SDK适配

2026-11-16，起点e75a6a5。GraphFlow未提供，定向检查；保持用户.env.example/.gitignore及所有旧扫描记录，不生产部署/推送/付费调用/操作学习卷。

## 实现前门禁

1. 固定Ryuk0.14.0原始归档SHA和官方拆分client0.6.3/api1.57.0；以可审查的本地补丁适配API，不冒充上游新版本。保留原标签筛选/ACK协议、重试/超时、清理顺序和Force语义；不禁用Ryuk或用ignore/VEX求绿。
2. 保留并运行上游mock/协议及四类真实Docker资源测试；补未选中资源不删除检查。所有测试独立随机标签，不清理用户资源，不删除已有镜像/卷。
3. 锁Go依赖和补丁摘要，构建元数据识别新SDK与Go，禁止旧root模块重新进入；变更进入配方指纹和受测本地镜像标签。扫描保留全部包/严重度，缺识别不能算零。
4. 全量真实Java25/Testcontainers/PG回归、前端检查、TLS全旅程、运维/SSE及完整扫描/离线证据复核；代码门禁失败不替换已受测工具，不提前宣称P6完成。
5. 更新风险台账/进度/复现，创建本地可回滚提交；本地fork维护成本和不支持平台明确保留。
6. 补查新增测试构建目标发现仅存在于测试二进制的archive/compress两高，扩展为正式第五工具/第16扫描目标。修补并识别这两项精确版本和patch包、双二进制SDK覆盖；不允许仅运行时镜像为零就跳过新测试工具。

## 设计与复现

本地补丁基于上游0.14.0归档8753af202acbfbd9c4f14b2fb85e2171c4cf053b836ccab8d78faa1a45d77fbb，提交b3726afd6cc2c36628abcc08e9cabac43f587384。官方拆分SDK为client v0.6.3（2ca8f919108134b1d6e79ed95b1c55ffc32f9c2c）、api v1.57.0（28396bf4817db0f6619d6656cded0877aae11293），不是上游新Ryuk发行版。修改只涉及类型/返回结果、Filters编码和协商Ping；原ACK、过滤组合、重试/超时、清理次序及删除选项保留。测试辅助改用新archive/jsonstream模块，保留上游全部测试，不再通过旧root取测试工具。

security/ryuk包含可审查的sdk.patch、go.mod/sum、MIT LICENSE、输出源码校验和及lock.json。构建阶段固定patch=2.8.1-r0用于应用补丁，不进入最终scratch运行镜像。所有资产字节与补丁后源码校验，且纳入工具recipe；下载模块后再次校验，构建-mod=readonly，保留Go元数据和符号。新本地标签0.14.0-sdk0.6.3-go1.27.4-r2明确标识工具分支，旧标签/历史日志保留。

测试目标由同一Dockerfile/归档/补丁重建，运行全部上游Go测试及新增的四类不匹配标签资源保留检查；只删除测试自己创建的ID。上游容器测试原本只create而不start，固定已有Go父镜像以避免alpine:latest漂移；Java集成另外验证实际运行中的PG容器和独立Ryuk二进制。锁定SDK client.go的MinAPIVersion为1.40，本轮只实测Docker29.9.3/API1.56、linux/amd64，不扩大平台承诺。构建/测试需要可信本地Unix socket，不能运行不可信代码。

```sh
python3 scripts/verify_image_lock.py
python3 scripts/security_images.py --build
python3 scripts/verify_ryuk.py
python3 scripts/verify_ryuk_scope.py
sh scripts/verify-testcontainers-in-docker.sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
# 在隔离Compose健康且迁移完成后扫描实际产物；输出须为新的.runtime目录
python3 scripts/security_scan.py --project YOUR_ACCEPTANCE_PROJECT --output .runtime/NEW_SCAN
python3 scripts/review_security_report.py --report .runtime/NEW_SCAN --output .runtime/NEW_REVIEW.json
```

仅在本机网络需要时显式设置SCARLET_BUILD_NETWORK=host并传入代理；默认default，不修改系统服务/网络或全局环境。扫描保留全部严重度/父层，不使用ignore/VEX；bin/ryuk必须识别两个SDK和Go精确版本且不得含旧根模块，缺识别直接失败。

来源：[Ryuk 0.14.0](https://github.com/testcontainers/moby-ryuk/tree/0.14.0)、[官方client](https://github.com/moby/moby/tree/client/v0.6.3/client)、[官方API](https://github.com/moby/moby/tree/api/v1.57.0/api)。第九批失败调查仍见P6_RYUK_ACCEPTANCE，不修改其历史结论。本地补丁需持续维护，优先评估后续上游兼容迁移。

## 实际结果

- 最终五个工具从锁定配方构建，全部上游Go及新增保护测试通过（10个顶层测试，20.506秒，.runtime-ryuk-sdk2-build-final.log）；没有跳过。实际Ryuk SHA256 47c57509e09526fa02971049a3dd7e8f57aafb8f7fc4ccd858acfe72545e0573，镜像d4052021...127883；SDK/Go版本、符号和无旧根模块核对通过。配方8dc0d562654d7ac96aacb1249d0e39ec35e7a3f119ba5c4f2e6e8985fe5b0a46。
- 首轮缺patch构建失败，补固定工具后成功；测试构建补查发现compress/archive两高，升级后compression常量API编译失败，适配后整套测试通过。失败日志.runtime-ryuk-sdk2-build.log、.runtime-ryuk-sdk2-upgrade-tests.log、.runtime-ryuk-sdk2-supplement.log保留；初期15目标零高不代表新测试构建目标通过，也不能在最终锁上复用旧报告。
- 测试依赖最终固定go-archive0.3.0/compress1.18.7，必要间接依赖在go.mod/sum；测试构建目标作为正式第五工具纳入扫描/复核第16目标。强制out/ryuk、out/ryuk.test两者SDK/Go及修补依赖/patch包识别，新增缺目标/错版本/补丁篡改等失败关闭测试。
- 最终Java25/Maven格式/编译/打包、31单元+53真实PG17.12集成=84通过，0失败/错误/跳过，2分38秒（.runtime-ryuk-sdk2-backend-final.log）。前端最终受测构建阶段格式/类型/14单元/构建通过（.runtime-ryuk-sdk2-frontend-final.log）；原chunk/弃用告警保留，Windows原生PG未重跑。
- 最终复用隔离卷的Compose独立迁移退出0、三服务Healthy；TLS桌面6项23.9秒/手机6项27.8秒通过（.runtime-ryuk-sdk2-browser-final.log），无API mock/JS异常/横向溢出。TLS信任正反/主机名/明文/Secure Cookie/CSRF/退出/限流、资源、monitor、只读附件8引用/8文件无异常、SSE首段0.106秒/终段2.607秒及头清洗通过（.runtime-ryuk-sdk2-runtime-final.log）；附件审计不是事务一致快照，无真实模型调用。
- Python/OpenSSL最终34项通过，Python编译/CI YAML/静态锁检查通过，远端CI未运行。CI先运行相同源码的Go验证，再执行Java/PG；安全workflow自动包含新正式工具目标。默认HTTP12及v2/旧v1恢复沿用前批相同应用代码实证，本批无业务代码或Flyway变更。

最终扫描与清理结果见下节。


## 最终扫描与收尾

最终.runtime/security-ryuk-sdk-complete完整16目标，扫描/复核均退出0，有效产物0高/严重；含全部父层41次仍保留。结束2026-11-16T15:43:09.437041+00:00，summary SHA256 c06fa64fb7e1dade30269de0af59a1fc6138b8125e2ab3fc4b250b1a76d598d6；复核输出.runtime/p6-ryuk-sdk-complete-review.json。Ryuk1中、测试构建3中2未知、后端2中/前端1中、npm生产0/全依赖5 moderate，全部保留，不用ignore/VEX或改目标分类求绿。补查的测试两高也已实际消除；中低/未知与上游兼容迁移继续于2026-11-23复核。

扫描通过仅指既定范围，不是无漏洞、第三方审计或P6整体成立。用户.env.example/.gitignore不入提交，无推送/生产/付费调用/个人库权限切换；默认HTTP与备份恢复沿用证据已明确。GraphFlow未提供，未声称context/plan/index。收尾仅停止本验收项目，保留PG/附件卷与全部原始证据。
