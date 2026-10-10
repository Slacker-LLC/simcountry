# SimCountry

**A SIM-region spoofing module that only touches TikTok.** Make TikTok read your SIM as any country other than CN.

- Injects into `com.zhiliaoapp.musically` only; the system and every other app still see your real SIM
- Does not change timezone, language, device fingerprint or store region
- Ships a settings screen: pick a country, tap apply
- The module app itself needs **no root**

> [README（中文）](README.md)

---

## Why

TikTok decides your SIM region through APIs such as `TelephonyManager.getSimCountryIso()`. Those need **no runtime permission at all**, so there is no switch anywhere in system settings to turn them off, and the package does not declare `READ_PHONE_STATE` either. Once it reads CN, it stops serving you.

To make it stop reading the real region, the return values have to be changed inside its own process. That is exactly what this module does.

## How it works

The module hooks a set of telephony APIs inside the TikTok process and substitutes the configured country.

| Class | Substituted methods |
|---|---|
| `TelephonyManager` | `getSimCountryIso` / `getNetworkCountryIso` / `getSimOperator` / `getNetworkOperator` / `getSimOperatorName` / `getNetworkOperatorName` |
| `SubscriptionInfo` | `getCountryIso` / `getMcc` / `getMnc` / `getCarrierName` / `getDisplayName` |
| `ServiceState` | `getOperatorNumeric` / `getOperatorAlphaLong` / `getOperatorAlphaShort` |
| `SystemProperties` | `gsm.sim.operator.iso-country`, `gsm.operator.iso-country`, `gsm.(sim.)operator.numeric`, `gsm.(sim.)operator.alpha`, `ro.csc.countryiso_code` |

The country code and the MCC/MNC are substituted together. Changing only the country code while leaving a `460` operator prefix behind gives two signals that disagree, which looks more suspicious than either alone.

The settings screen and the hook are two different processes. The hook runs inside TikTok and cannot read this app's private storage, so the module declares `xposedsharedprefs`: the framework mirrors the preferences somewhere readable and the hook picks them up through `XSharedPreferences`. **Saving a choice therefore needs no root.**

## Requirements

- A rooted Android device
- An Xposed-compatible framework (LSPosed, the Zygisk build of Vector, ...). This project ships no framework.

The module app itself does not need root. The only convenience that tries to use `su` is the optional "restart TikTok for me" step, and when that fails it tells you to restart the app yourself.

## Build

No Gradle. You need JDK 17 and the Android SDK build-tools plus platform.

```bash
git clone <this repo>
cd simcountry

export ANDROID_HOME=$HOME/Android/Sdk
./build-android.sh
```

The output is `SimCountry.apk`.

Two toolchain pieces the script expects:

| Path | Source |
|---|---|
| `libs/api-82.jar` | Xposed API, vendored in this repo (compile-time only, never packed into the APK) |
| `$ANDROID_HOME/build-tools/34.0.0`, `$ANDROID_HOME/platforms/android-36/android.jar` | Official Android SDK |

> **Building on aarch64 Linux**: the official build-tools ship x86_64 binaries only. The script detects this and falls back to `qemu-x86_64-static` (install `qemu-user-static`, and point `QEMU_LD_PREFIX` at an x86_64 rootfs, or set `QEMU` to a full command line).

## Install and enable

```bash
adb install SimCountry.apk
```

Then enable the module in your Xposed manager and scope it to **TikTok** only (`com.zhiliaoapp.musically`). Restart TikTok to take effect.

With Vector, for example (the CLI is equivalent to the GUI):

```bash
sh /data/adb/lspd/cli modules enable com.local.simcountry
sh /data/adb/lspd/cli scope set com.local.simcountry com.zhiliaoapp.musically/0
am force-stop com.zhiliaoapp.musically
```

## Usage

Open **SIM Country** from the launcher, pick a country, tap "apply and restart TikTok".

The card at the top shows what is live right now. Every row in the list carries the MCC/MNC and carrier it would report, so you can see exactly what TikTok would be told.

The default is **United States** (`us` / MCC-MNC `310260` / `T-Mobile`).

Built-in table (switching country keeps the MCC/MNC and carrier name consistent):

| Value | Country | Carrier | Value | Country | Carrier |
|---|---|---|---|---|---|
| `us` | United States | T-Mobile | `tw` | Taiwan | Chunghwa Telecom |
| `ca` | Canada | TELUS | `my` | Malaysia | Maxis |
| `gb` / `uk` | United Kingdom | Vodafone UK | `th` | Thailand | AIS |
| `de` | Germany | Telekom.de | `vn` | Vietnam | Viettel |
| `fr` | France | Orange F | `ph` | Philippines | Globe |
| `jp` | Japan | NTT docomo | `id` | Indonesia | Telkomsel |
| `kr` | South Korea | SKTelecom | `ru` | Russia | MTS |
| `sg` | Singapore | SingTel | `br` | Brazil | Vivo |
| `au` | Australia | Telstra | `mx` | Mexico | Telcel |

Also available: `es` (Vodafone ES), `it` (TIM), `nl` (Vodafone NL), `tr` (Turkcell), `ae` (Etisalat), `sa` (STC).

> **Hong Kong and India are deliberately absent.** TikTok has withdrawn from Hong Kong, and India has banned it since 2020; spoofing the card to either region cannot help, so the table only lists regions TikTok actually serves.

### Command line

If you would rather skip the UI, or you are scripting this, a system property works too:

```bash
setprop persist.minis.simcountry jp
am force-stop com.zhiliaoapp.musically
```

The property is only consulted when the settings screen has never saved a choice, so a stale property can never override what a human picked.

Any other value is accepted too, but then only the country code changes and the MCC/MNC stay as they are. To pin them explicitly:

```bash
setprop persist.minis.simmcc 450
setprop persist.minis.simmnc 05
setprop persist.minis.simalpha "SKTelecom"
```

### Troubleshooting

Turn on debug logging, restart TikTok, and you will see which APIs it actually queries and what it gets back:

```bash
setprop persist.minis.simdebug 1
am force-stop com.zhiliaoapp.musically
logcat -s SimCountry:*
```

```
SimCountry: config: ca/302/220/TELUS from shared prefs
SimCountry: active in com.zhiliaoapp.musically, iso=ca, numeric=302220, alpha=TELUS, hooks=22
SimCountry: queried TelephonyManager.getSimCountryIso = ca
SimCountry: queried TelephonyManager.getSimOperator = 302220
```

The first line says whether the value came from `shared prefs` (the screen) or a `system property` (the command line).

### Disable and uninstall

```bash
sh /data/adb/lspd/cli modules disable com.local.simcountry
pm uninstall com.local.simcountry
```

## Known limitations

- **The SIM is only half of how TikTok decides region**; the other half is usually your exit IP. A US SIM behind a domestic direct connection will most likely still be blocked.
- If TikTok already wrote "CN" into its own data, clearing the app data once may be needed.
- The module loads when the process starts, so restarting TikTok is required after every config change.
- Only the Java telephony API surface is covered. A build that reads the country code through another channel needs extra hooks.

## References

The API list was assembled with the help of these two projects; none of their code is reused:

- [kjqg-cn/tiktok-region-hook](https://github.com/kjqg-cn/tiktok-region-hook)
- [NZM7973/TikTokSIMSpoof](https://github.com/NZM7973/TikTokSIMSpoof)

## License

Apache License 2.0, see [LICENSE](LICENSE). Xposed API attribution is in [NOTICE](NOTICE).
