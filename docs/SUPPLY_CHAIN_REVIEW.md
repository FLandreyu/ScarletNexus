# P6 供应链复核与未关闭风险

复核日期：2026-11-16，Asia/Shanghai。**兼容补丁、不可变镜像与可复现扫描已实现；安全门禁未通过，P6不标记完成。** 扫描完成与安全通过是两个不同结果。组件识别受文件元数据/数据库覆盖限制，JVM与手工安装的服务端二进制仍须核对厂商发布公告。本轮未包含渗透测试、宿主/浏览器漏洞、GitHub Actions供应链、许可证合规或真实模型质量。

## 第七批：当前有效产物（2026-11-16）

工具镜像修补与验证见[P6_TOOLCHAIN_REMEDIATION](P6_TOOLCHAIN_REMEDIATION.md)。官方父镜像继续固定摘要，新增Go1.27.4；Maven/Node/PostgreSQL/Ryuk改为本地派生镜像，以源码/资产摘要及配方指纹核对，实际扫描与运行记录不可变image ID。这些本地标签不是已发布的registry digest，也不修改宿主全局工具。

最终报告`.runtime/security-tools-final`为v2格式，漏洞库/Java DB更新时间仍为当日01:11Z/00:57Z；`complete=true`，当前实际产物门禁**4项高/严重**，未通过。所有上游基线仍有完整报告，连同当前产物共45次目标发现；该合计包含重复基础层，不能代表生产暴露数量。第六批95次采用旧目标集合，不能直接与当前门禁4次比较；按当时有效产物范围排除已修复Nginx父层3次后，旧值为92次。没有新增ignore、VEX或接受风险例外。

| 当前门禁目标 | 严重 | 高 | 中 | 低 | 未知 |
| --- | --- | --- | --- | --- | --- |
| 派生Maven3.9.16/Java25 | 0 | 0 | 126 | 47 | 0 |
| 派生Node24/npm11.21.2 | 0 | 0 | 3 | 0 | 0 |
| 派生PG17.12/gosu1.20/Go1.27.4 | 0 | 0 | 0 | 0 | 1 |
| 重建Ryuk0.14.0/Go1.27.4 | 0 | 2 | 2 | 0 | 0 |
| Go1.27.4编译器镜像 | 0 | 0 | 1 | 0 | 0 |
| 实际后端 | 2 | 0 | 4 | 0 | 0 |
| 实际前端 | 0 | 0 | 1 | 0 | 0 |
| 实际Tomcat补充SBOM | 0 | 0 | 0 | 0 | 0 |

npm应用生产范围仍0项、全部依赖仍5项moderate，独立于工具npm自身的扫描。Maven升级精确apt/OpenSSL/brotli补丁和Guava33.7.2-jre；Node仅镜像内升级npm11.21.2，未使用强制主版本升级。gosu与Ryuk从固定源码用Go1.27.4重建，保留符号、无UPX；扫描强制识别两个二进制的stdlib版本，识别缺失会失败，不能当零漏洞。

| 剩余风险 | 当前判断 | 下一步/复查 |
| --- | --- | --- |
| Spring MVC CVE-2026-47884 / CVE-2026-47890 | 仍为2项严重，REST/SSE使用条件复核仅作补充，不替代补丁。 | [Boot4迁移计划](BOOT4_MIGRATION_PLAN.md)已准备；框架大版本按AGENTS先确认。2026-11-23复查。 |
| Ryuk CVE-2026-41567 / CVE-2026-42306 | 仍保留2项高。官方公告针对Docker daemon的archive/cp实现；实际Ryuk二进制有client符号、无daemon/builder/libcontainerd实现符号，受测Engine29.9.3高于公告修复版29.5.1。此范围证据限定本机受测组合，**未修改扫描门禁**，不笼统宣称SDK安全或其他daemon已修。 | 保留二进制SHA和引擎证据，复核上游SDK/数据库归属及函数可达性；2026-11-23。 |
| 中/低/未知及5项npm moderate | 全部报告保留；Maven工具仍有126中/47低，gosu依赖有1未知。 | 继续分类，2026-11-30；不把零高/严重称为零漏洞。 |

官方依据：[gosu1.20源码](https://github.com/tianon/gosu/releases/tag/1.20)、[Ryuk0.14.0源码](https://github.com/testcontainers/moby-ryuk/tree/0.14.0)、[Go发布](https://go.dev/dl/)、[npm11.21.2](https://registry.npmjs.org/npm/11.21.2)、[Guava发布](https://github.com/google/guava/releases)、[Docker archive公告](https://github.com/advisories/GHSA-x86f-5xw2-fm2r)、[Docker cp公告](https://github.com/advisories/GHSA-rg2x-37c3-w2rh)。源码版本/提交/摘要见toolchain.lock；包修补版本见Dockerfile。

## 第六批历史处置

- 保留用户已授权的Java25升级（ADR-032），Boot3.5.16框架不跨大版本迁移。通过BOM属性固定Jackson2.21.8、Tomcat10.1.62、PostgreSQL JDBC42.7.14；不修改业务合同或已执行的Flyway迁移。
- 采用官方Nginx1.30.6-alpine3.24与PostgreSQL17.12-alpine3.24。实际前端运行镜像另精确安装libxslt1.1.43-r4，修复旧基础镜像尚包含的3项高风险；只改镜像内包，不改宿主。数据库使用全新命名验收卷，未升级原验收卷或个人学习库。
- 六份官方镜像索引以tag@sha256固定，覆盖Maven/Java/Node/Nginx/PostgreSQL/Ryuk；清单、用途与消费者见`security/images.lock.json`。静态一致性检查及registry逐项核对均通过。多架构索引保留，实际构建/运行只验证linux/amd64，不宣称其他平台已测。
- 原生测试的Zonky PG二进制BOM暂只有17.11.0；本轮容器与Compose实测17.12。文档区分二者，不把旧原生版本当作17.12验证。

兼容补丁依据：[Tomcat官方安全公告](https://tomcat.apache.org/security-10.html)、[pgJDBC安全修复](https://jdbc.postgresql.org/changelogs/2026-06-29-42.7.12-release/)、[PostgreSQL17.12发布说明](https://www.postgresql.org/docs/17/release-17-12.html)、[Nginx安全公告](https://nginx.org/en/security_advisories.html)。版本存在性另由Maven Central元数据和官方Docker registry核对。当前属性覆盖是Boot BOM的显式例外，更新Boot时必须重新复核，不能忘记旧覆盖。

## 扫描身份、覆盖与局限

Trivy0.76.2 Linux/amd64归档SHA-256为`bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf`；已核对官方release checksum文件与解包后二进制hash，固定于`security/tools.json`。这是HTTPS来源和摘要校验，不冒充独立签名/构建溯源验证。安装只写忽略的.tools，不注册系统服务或改全局PATH。

最终扫描使用漏洞库v2，更新于2026-11-16T01:11:02Z；Java识别库v1，更新于00:57:00Z。工具验证前者不超过48小时、后者不超过96小时，拒绝缺失、过期或未来时间。每个报告保留所有严重度、未修复项、包清单与SHA-256；显式空配置/空ignore文件，不使用ignore-unfixed，不加入漏洞忽略清单。没有扫描或上传.env、口令、附件卷、学习记录。

生产Java从实际构建镜像的`/app/app.jar`扫描，包含传递依赖。本次Trivy Java DB未识别刚发布的Tomcat10.1.62三个嵌套JAR；首轮覆盖门禁正确失败，随后增加从实际镜像提取文件名/版本/校验值的CycloneDX补充扫描，覆盖core/el/websocket三个包，结果0项发现。不能把缺少识别信息算成干净。代码保留Jackson、Spring MVC、JDBC代表性覆盖断言，Tomcat清单不完整则失败。

依据：[Trivy Java嵌套JAR识别机制](https://trivy.dev/docs/latest/coverage/language/java/)、[官方0.76.2发布](https://github.com/aquasecurity/trivy/releases/tag/v0.76.2)、[Docker镜像摘要固定](https://docs.docker.com/build/building/best-practices/#pin-base-image-versions)。固定digest防止标签漂移，不能自动修复漏洞；精确APK包也可能因仓库清理而无法重建，应失败后复核更新，禁止静默降级。

## 第六批历史发现快照

以下按“目标内包/CVE发现次数”统计，**不是独立CVE数，也不能将基础镜像和最终镜像相加称为生产暴露数量**。最终原始结果在忽略目录`.runtime/security-complete`，summary.json记录目标image ID、报告摘要、当前镜像锁摘要和数据库元数据。`.runtime/security-verified`是同结果的前一轮记录；最早的security-final覆盖不全，明确不作为通过证据。

| 目标 | 用途 | 严重 | 高 | 中 | 低 | 未知 |
| --- | --- | --- | --- | --- | --- | --- |
| 最终后端镜像 | 生产构建产物 | 2 | 0 | 4 | 0 | 0 |
| 最终前端镜像 | 生产构建产物 | 0 | 0 | 1 | 0 | 0 |
| Tomcat补充SBOM | 实际后端3个嵌套JAR | 0 | 0 | 0 | 0 | 0 |
| PostgreSQL17.12 | 数据库运行镜像 | 1 | 25 | 31 | 3 | 2 |
| Java25 JRE | 后端基础镜像 | 0 | 0 | 1 | 0 | 0 |
| Nginx1.30.6基础镜像 | 最终产物已补libxslt | 0 | 3 | 2 | 0 | 0 |
| Maven3.9.16/Java25 | 构建/测试工具 | 0 | 6 | 126 | 57 | 0 |
| Node24基础镜像 | 构建工具及其内置npm | 0 | 6 | 13 | 1 | 0 |
| Ryuk0.12 | 测试清理工具 | 3 | 49 | 72 | 56 | 1 |

npm生产依赖审计0项；全部依赖5项moderate、无high/critical，来自Vue Test Utils→js-beautify→editorconfig/glob/minimatch链。两份报告单独保留，不将Node镜像内置npm的告警混入应用package-lock计数。

与首轮比较：旧后端有7项高、7项严重，修补后剩2项严重；旧前端有73项高、3项严重，最终前端两类均0。仍有发现的运行/构建/测试目标没有被忽略。扫描器最终完整结束并返回2，保守门禁共计95次高/严重目标发现，**这是未通过的真实结果**，不是扫描执行错误或“全栈安全”。

## 第六批历史风险台账（当前状态以上节为准）

| 风险 | 当前判断与处置 | 后续/复查期限 |
| --- | --- | --- |
| Spring MVC CVE-2026-47884 / CVE-2026-47890 | 扫描器标严重，厂商分别评为MEDIUM/LOW。前者需要XsltView及特定视图映射；后者需要SSE视图片段和跨用户可控内容。当前仅REST JSON，TutorController以StreamingResponseBody写JSON事件，未配置XsltView/FragmentsRendering。此为使用条件复核，未将其加入ignore或声称已升级修复。 | Boot3.5/Framework6.2 OSS生命周期风险仍在。6.2.20官方列为商业支持；不得擅自购买或把7.x强塞入Boot3。生产前确认受支持框架迁移方案，2026-11-23复查。 |
| PostgreSQL镜像gosu的Go1.24.6 | 1严重/25高均位于usr/local/bin/gosu的Go标准库。它是启动降权工具，并非数据库网络处理器；扫描未证明这些路径可远程利用。也未做逐项可达性证明，故风险保持未关闭，不笼统标为误报。 | 复核上游gosu重建/官方镜像更新或单独验证启动工具替代；2026-11-23。 |
| Maven基础镜像 | 高项包含apt/libapt、brotli、OpenSSL和构建工具内Guava33.6.0-jre。它们不在最终后端镜像中，但构建机仍处理下载内容，不能因非运行时就删除告警。 | 评估上游镜像刷新/受测构建镜像补丁；2026-11-23。 |
| Node基础镜像内置npm | 高项位于npm自己的brace-expansion和undici；应用lockfile的生产依赖为另一范围。未给全局npm执行未经验证的强制升级。 | 验证含补丁的Node/npm构建组合后更新digest；2026-11-23。 |
| Ryuk | 仅测试源集启用，保持自动清理，不跳过/禁用。其Docker socket能力要求只运行可信测试；仍有OS/Go/docker库发现。 | 验证新Ryuk与当前Testcontainers/daemon兼容后更新；2026-11-23。 |
| 5项npm开发中等级 | 上游依赖范围仍受影响；目前不处理不可信项目/任意glob输入。不强制跨主版本override，不用audit fix --force隐藏。 | 上游兼容修复/专项升级，2026-11-30。 |
| 未知严重度/其他中低项 | 全量报告保留；没有“未知即安全”判断。 | 与下一轮风险处置一起分类，不以0个高危替代人工复核。 |

Spring条件与修复可用性依据：[CVE-2026-47884](https://spring.io/security/cve-2026-47884/)、[CVE-2026-47890](https://spring.io/security/cve-2026-47890/)。开发链依据：[glob公告](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)、[minimatch公告](https://github.com/advisories/GHSA-g3hv-75wg-m249)。所有复查日期都是待办，不是已接受的生产风险例外；本轮没有创建任何抑制条目。

## 复现与自动化

Linux/amd64 Docker宿主、已构建并运行的独立验收项目：

```sh
python3 scripts/verify_image_lock.py --remote
python3 scripts/security_images.py --build
python3 scripts/install_security_scanner.py
TRIVY_DISABLE_TELEMETRY=true python3 scripts/security_scan.py --project YOUR_ACCEPTANCE_PROJECT --output .runtime/new-security-report
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

输出目录必须是.runtime内的新目录；脚本只挂载npm package/lock两个只读文件，不挂载个人数据；扫描器也不执行镜像内业务服务。Tomcat补充清单通过无网络/只读/无cap容器读取现有JAR。退出0=完整扫描且未发现高/严重，2=完整扫描但有高/严重，1=扫描/覆盖/新鲜度校验失败；任何非0都不能算门禁通过。脚本不自动删除旧报告或更新锁。

`.github/workflows/security.yml`提供手动workflow_dispatch入口、固定扫描器和报告制品；有高风险就失败，报告仍上传。不擅自把已有未处置发现改成成功，也不声称远端已运行。默认质量CI仍独立负责构建/真实PG/浏览器；安全门禁未关闭前不进入P7或生产部署。
