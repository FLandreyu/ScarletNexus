# D3 本机空测试机搭建记录

2026-11-17；用户授权本机搭建后明确“还不确定，先把空测试机搭好”。本批不下载/安装Windows，不创建客体账户，不购买许可。

## 已交付

- 复用 `D:/VMware` Workstation 17.6.4 build-24832109；vmware.exe、vmrun.exe、vmware-vmx.exe签名均Valid，VMAuthdService/VMnetDHCP/NAT服务原已运行。此前仅查常见位置遗漏了非默认安装，本批不再安装VirtualBox驱动。
- VM：`D:/VMs/ScarletNexus-D3-Clean/ScarletNexus-D3-Clean.vmx`。
- 4 vCPU（1插槽4核）、8192MiB内存、100GiB单文件动态VMDK，EFI/Secure Boot配置、关闭3D。初始文件总占用约13MiB；后续磁盘写入/快照消耗D盘空间，至少保留30GiB。
- NAT网卡默认断开；共享文件夹、剪贴板、拖放、USB关闭，不安装Guest Tools。既有软件测试ISO配置为光驱来源但默认不连接，它不是Windows系统镜像。
- 桌面快捷方式 `ScarletNexus D3 VM` 指向现有VMware和此VMX。双击打开VMware中的测试机，再选择启动；空盘没有Windows桌面属于正常状态。
- 检查后已关闭VM释放内存；未修改宿主WSL/Hyper-V/VBS或原有虚拟机，未重启宿主。

## 实际验证

1. 原生 `vmcli VM Create -c windows11-64` 创建VMX/动态盘。命令直接写入指定目录、不自动建同名子目录；发现后仅将本批新建的两个文件移入核对后的新VM目录，保留相对盘名，未移动既有VM。
2. `vmware-vdiskmanager -x 100GB` 成功；读取稀疏盘头容量为107374182400字节；`-e` 返回 `Disk chain is consistent.`。
3. `vmcli Chipset query` 确认4 vCPU/8192MiB/每插槽4核；`Disk query`确认系统盘与默认断开的测试ISO。逐项检查隔离设置通过。
4. `vmrun -T ws start <VMX> nogui`返回0，`vmrun list`包含该VM，`Power query`为on。vmware.log记录ULM/WHP、EFI ROM和 `Status upon boot failure: No Media`，证明空VM在原宿主配置下启动到无介质阶段。
5. 仅对此无OS/数据的空VM执行 `vmrun stop <VMX> hard`，返回0，运行VM为0；这不是Windows正常关机或D3异常断电验收证据。
6. Computer Use native pipe不可用；MKS截图未生成文件，vmrun截图要求客体登录，未绕过权限。没有视觉确认，不把日志启动验证称作已看到安装界面。

私有证据：`.runtime/desktop-empty-vm/verification.json`、VM目录的 `vmware.log`。VM、磁盘、日志和快捷方式不入Git。报告记录配置SHA，后续改配置时该SHA仅代表本批快照。

## 装Windows前的下一步

2026-11-17镜像准备：已创建宿主目录 `D:/VMs/ISO`。官方下载链接接口返回 `ErrorSettings.SentinelReject`，随后命令启动浏览器被自动审批拒绝（blocked by policy），未下载ISO。请用户手动打开 [微软官方下载页](https://www.microsoft.com/zh-cn/software-download/windows11)，选择“下载适用于x64设备的Windows11磁盘映像(ISO)”中的多版本ISO，再选简体中文和64位下载，保存到上述目录。仅下载，不在宿主运行安装助手或ISO中的setup；后续先核对微软公布的SHA256，再配置虚拟机光驱。许可、vTPM和Windows安装状态不变。

1. 用户确定官方Windows11 x64系统ISO与适用许可，或自行处理官方企业评估注册和条款；不下载改装系统或绕过许可。
2. 在VMware受支持的流程中启用所需加密并添加正式vTPM2.0。**本批尚未配置TPM，不能称为Windows11安装就绪**；不使用实验性autoAddVTPM或绕过TPM检查。
3. 加载系统ISO并安装到虚拟磁盘，按官方流程临时连接NAT完成系统准备。
4. 创建实际中文/空格路径的标准账户，断网，换回软件测试ISO，记录基线并保存S0，然后执行客体验收表。

D3客体业务、升级/恢复、磁盘满和异常断电仍NOT_RUN；D4未开始。GraphFlow工具未暴露，未执行context/plan/index。业务代码未变，未重复前后端测试；桌面控制不可用仍限制后续安装向导自动操作。
