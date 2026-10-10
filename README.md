# SimCountry

**只对 TikTok 生效的 SIM 归属地伪装模块。** 让 TikTok 把你的卡读成任意国家，而不是 CN。

- 只注入 `com.zhiliaoapp.musically` 的进程，系统和其他应用看到的仍是真实 SIM
- 不改时区、不改语言、不改设备指纹、不改商店区域
- 国家可在运行时切换，不用重新编译

> [README (English)](README_EN.md)

---

## 为什么要做这个

TikTok 判断卡归属地用的是 `TelephonyManager.getSimCountryIso()` 这类接口。它们**不需要任何运行时权限**，所以在系统设置里根本找不到可以关的开关；实测这个包的权限清单里也没有 `READ_PHONE_STATE`。它读到 CN 就拒绝提供服务。

要做到「不让它读到真实归属地」，只有在它自己的进程里把返回值改掉。这就是本模块做的事。

## 工作原理

模块在 TikTok 进程内挂上一组 Telephony API，把这些接口的返回值替换成配置的国家。

| 类 | 被替换的方法 |
|---|---|
| `TelephonyManager` | `getSimCountryIso` / `getNetworkCountryIso` / `getSimOperator` / `getNetworkOperator` / `getSimOperatorName` / `getNetworkOperatorName` |
| `SubscriptionInfo` | `getCountryIso` / `getMcc` / `getMnc` / `getCarrierName` / `getDisplayName` |
| `ServiceState` | `getOperatorNumeric` / `getOperatorAlphaLong` / `getOperatorAlphaShort` |
| `SystemProperties` | `gsm.sim.operator.iso-country`、`gsm.operator.iso-country`、`gsm.(sim.)operator.numeric`、`gsm.(sim.)operator.alpha`、`ro.csc.countryiso_code` |

国家码和 MCC/MNC 一起替换。只换国家码而留着 `460` 的运营商前缀，两个信号对不上，反而更可疑。

## 环境要求

- 已 Root 的 Android 设备
- 一个 Xposed 兼容框架（LSPosed、Zygisk 版 Vector 等），本项目不带框架

## 构建

无需 Gradle，只需 JDK 17 和 Android SDK 的 build-tools + platform。

```bash
git clone <this repo>
cd simcountry

export ANDROID_HOME=$HOME/Android/Sdk
./build-android.sh
```

产物是 `SimCountry.apk`。

构建脚本需要的两个工具链文件：

| 路径 | 来源 |
|---|---|
| `libs/api-82.jar` | Xposed API，仓库已附带（编译期依赖，运行时由框架提供，不打进 APK） |
| `$ANDROID_HOME/build-tools/34.0.0`、`$ANDROID_HOME/platforms/android-36/android.jar` | 官方 Android SDK |

> **在 aarch64 Linux 上构建**：官方 build-tools 只有 x86_64 版本。脚本会自动检测，跑不起来就用 `qemu-x86_64-static` 兜底（需要 `apt install qemu-user-static`，并把 `QEMU_LD_PREFIX` 指向一个 x86_64 rootfs，或者把 `QEMU` 设成完整命令）。

## 安装与启用

```bash
adb install SimCountry.apk
```

然后在你的 Xposed 管理器里启用本模块，作用域只勾 **TikTok**（`com.zhiliaoapp.musically`）。重启 TikTok 生效。

以 Vector 为例（CLI 与图形界面等价）：

```bash
sh /data/adb/lspd/cli modules enable com.local.simcountry
sh /data/adb/lspd/cli scope set com.local.simcountry com.zhiliaoapp.musically/0
am force-stop com.zhiliaoapp.musically
```

## 使用

当前默认伪装为**美国**（`us` / MCC-MNC `310260` / `T-Mobile`）。

换国家不需要重编译，改一个系统属性，然后重启 TikTok：

```bash
setprop persist.minis.simcountry jp
am force-stop com.zhiliaoapp.musically
```

内置 27 国对照表，切国家时会连带把 MCC/MNC 和运营商名一起换掉：

| 值 | 国家 | 运营商 | 值 | 国家 | 运营商 |
|---|---|---|---|---|---|
| `us` | 美国 | T-Mobile | `tw` | 台湾 | Chunghwa Telecom |
| `ca` | 加拿大 | TELUS | `my` | 马来西亚 | Maxis |
| `gb` / `uk` | 英国 | Vodafone UK | `th` | 泰国 | AIS |
| `de` | 德国 | Telekom.de | `vn` | 越南 | Viettel |
| `fr` | 法国 | Orange F | `ph` | 菲律宾 | Globe |
| `jp` | 日本 | NTT docomo | `id` | 印尼 | Telkomsel |
| `kr` | 韩国 | SKTelecom | `in` | 印度 | Airtel |
| `sg` | 新加坡 | SingTel | `ru` | 俄罗斯 | MTS |
| `au` | 澳大利亚 | Telstra | `br` | 巴西 | Vivo |
| `hk` | 香港 | CSL | `mx` | 墨西哥 | Telcel |

其余可选值：`es`（Vodafone ES）、`it`（TIM）、`nl`（Vodafone NL）、`tr`（Turkcell）、`ae`（Etisalat）、`sa`（STC）。

表里没有的国家也能填，只是只改国家码，MCC/MNC 保持原样。要精确指定：

```bash
setprop persist.minis.simmcc 450
setprop persist.minis.simmnc 05
setprop persist.minis.simalpha "SKTelecom"
```

### 排错

打开调试日志，重启 TikTok，就能看到它实际查询了哪些接口、拿到什么：

```bash
setprop persist.minis.simdebug 1
am force-stop com.zhiliaoapp.musically
logcat -s SimCountry:*
```

```
SimCountry: config: us/310/260/T-Mobile (debug)
SimCountry: active in com.zhiliaoapp.musically, iso=us, numeric=310260, alpha=T-Mobile, hooks=22
SimCountry: queried TelephonyManager.getSimCountryIso = us
SimCountry: queried TelephonyManager.getSimOperator = 310260
```

### 关闭与卸载

```bash
sh /data/adb/lspd/cli modules disable com.local.simcountry
pm uninstall com.local.simcountry
```

## 已知限制

- **SIM 只是它判断地区的一半**，另一半通常是出口 IP。卡改成美国、人还在国内直连，大概率照样进不去。
- 如果 TikTok 之前已经把「CN」写进自己的数据里，可能需要清除一次应用数据。
- 模块在进程启动时加载，改配置后必须重启 TikTok。
- 只覆盖 Java 层的 Telephony API。如果某个版本改用别的通道取国家码，需要相应补 hook。

## 参考

接口清单的整理参考了这两个项目，没有复用它们的代码：

- [kjqg-cn/tiktok-region-hook](https://github.com/kjqg-cn/tiktok-region-hook)
- [NZM7973/TikTokSIMSpoof](https://github.com/NZM7973/TikTokSIMSpoof)

## 许可

Apache License 2.0，见 [LICENSE](LICENSE)。Xposed API 的归属见 [NOTICE](NOTICE)。
