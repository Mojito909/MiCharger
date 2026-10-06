# MiCharger 充电管家

针对小米9（cepheus / Android 13）的充电控制工具，MIUI 风格（[Miuix](https://github.com/compose-miuix-ui/miuix)）。

## 功能
- 实时电池状态：电量 / 电流 / 电压 / 功率 / 温度 / 剩余容量
- 充电控制（需 Root / Magisk）：暂停充电、恢复充电、充电限流
- 充电守护：达到目标电量自动暂停，回落阈值自动恢复；支持开机自启
- 充电历史：24 小时电量曲线（需开启守护服务）
- 耗电排行：Root 下按 batterystats 精确统计，无 Root 按前台时长估算
- 信息页：设备信息、电池健康度、循环次数

## 安装
从 [Releases](../../releases) 下载 APK 安装（release 构建当前使用 debug 签名）。

## 权限说明
| 权限 | 用途 |
|---|---|
| Root（Magisk） | 写 sysfs 节点控制充电：`/sys/class/power_supply/battery/` 下 `input_suspend`、`battery_charging_enabled`、`constant_charge_current_max` |
| 使用情况访问 | 无 Root 时的应用前台时长统计（可选授权） |
| 通知 | 充电守护前台服务通知 |

## 构建要求
- JDK 17+
- Android SDK（compileSdk 36）
- `./gradlew assembleDebug` 或直接推送触发 GitHub Actions 自动打包

## 免责声明
修改充电策略存在电池风险，请自行评估。作者不对任何设备损坏负责。

## License
MIT
