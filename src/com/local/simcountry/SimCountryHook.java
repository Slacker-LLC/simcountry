package com.local.simcountry;

import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Reports a non-CN SIM / network country to the target app only.
 * Nothing outside the target process is touched.
 */
public final class SimCountryHook implements IXposedHookLoadPackage {

    private static final String TAG = "SimCountry";
    private static final String[] TARGETS = {
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.trill",
    };

    private static String ISO = "us";
    private static String MCC = "310";
    private static String MNC = "260";
    private static String ALPHA = "T-Mobile";
    private static boolean DEBUG = false;

    private static final String[] COUNTRIES = {
            "us:310:260:T-Mobile",
            "ca:302:220:TELUS",
            "gb:234:15:Vodafone UK",
            "uk:234:15:Vodafone UK",
            "de:262:01:Telekom.de",
            "fr:208:01:Orange F",
            "jp:440:10:NTT docomo",
            "kr:450:05:SKTelecom",
            "sg:525:01:SingTel",
            "au:505:01:Telstra",
            "hk:454:00:CSL",
            "tw:466:92:Chunghwa Telecom",
            "my:502:12:Maxis",
            "th:520:01:AIS",
            "vn:452:01:Viettel",
            "ph:515:02:Globe",
            "id:510:10:Telkomsel",
            "in:404:45:Airtel",
            "ru:250:01:MTS",
            "br:724:06:Vivo",
            "mx:334:020:Telcel",
            "es:214:01:Vodafone ES",
            "it:222:01:TIM",
            "nl:204:04:Vodafone NL",
            "tr:286:01:Turkcell",
            "ae:424:02:Etisalat",
            "sa:420:01:STC",
    };

    private static boolean installed = false;
    private static final Set<String> SEEN =
            Collections.synchronizedSet(new HashSet<String>());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!isTarget(lp.packageName)) {
            return;
        }
        try {
            install(lp.classLoader, lp.processName);
        } catch (Throwable t) {
            log("init failed: " + t);
        }
    }

    private static boolean isTarget(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String t : TARGETS) {
            if (t.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void install(ClassLoader cl, String process) {
        if (installed) {
            return;
        }
        installed = true;

        loadConfig();

        final String numeric = MCC + MNC;
        final String isoUpper = ISO.toUpperCase(Locale.US);
        final Integer mccInt = safeInt(MCC);
        final Integer mncInt = safeInt(MNC);

        XC_MethodHook isoHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                note(p, ISO);
                p.setResult(ISO);
            }
        };
        XC_MethodHook numericHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                note(p, numeric);
                p.setResult(numeric);
            }
        };
        XC_MethodHook alphaHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                note(p, ALPHA);
                p.setResult(ALPHA);
            }
        };
        XC_MethodHook mccHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (mccInt != null) {
                    note(p, String.valueOf(mccInt));
                    p.setResult(mccInt);
                }
            }
        };
        XC_MethodHook mncHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (mncInt != null) {
                    note(p, String.valueOf(mncInt));
                    p.setResult(mncInt);
                }
            }
        };

        int n = 0;
        Class<?> tm = TelephonyManager.class;
        n += hookAll(tm, "getSimCountryIso", isoHook);
        n += hookAll(tm, "getNetworkCountryIso", isoHook);
        n += hookAll(tm, "getSimOperator", numericHook);
        n += hookAll(tm, "getNetworkOperator", numericHook);
        n += hookAll(tm, "getSimOperatorName", alphaHook);
        n += hookAll(tm, "getNetworkOperatorName", alphaHook);

        Class<?> si = SubscriptionInfo.class;
        n += hookAll(si, "getCountryIso", isoHook);
        n += hookAll(si, "getMcc", mccHook);
        n += hookAll(si, "getMnc", mncHook);
        n += hookAll(si, "getCarrierName", alphaHook);
        n += hookAll(si, "getDisplayName", alphaHook);

        Class<?> ss = ServiceState.class;
        n += hookAll(ss, "getOperatorNumeric", numericHook);
        n += hookAll(ss, "getOperatorAlphaLong", alphaHook);
        n += hookAll(ss, "getOperatorAlphaShort", alphaHook);

        n += hookSystemProperties(cl, isoUpper, numeric);

        log("active in " + process + ", iso=" + ISO + ", numeric=" + numeric
                + ", alpha=" + ALPHA + ", hooks=" + n);
    }

    private static int hookSystemProperties(ClassLoader cl, final String isoUpper, final String numeric) {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("gsm.sim.operator.iso-country", ISO);
        map.put("gsm.operator.iso-country", ISO);
        map.put("gsm.sim.operator.numeric", numeric);
        map.put("gsm.operator.numeric", numeric);
        map.put("gsm.sim.operator.alpha", ALPHA);
        map.put("gsm.operator.alpha", ALPHA);
        map.put("ro.csc.countryiso_code", isoUpper);

        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args == null || p.args.length == 0) {
                            return;
                        }
                        Object key = p.args[0];
                        if (!(key instanceof String)) {
                            return;
                        }
                        String v = map.get(key);
                        if (v != null) {
                            note(p, String.valueOf(key));
                            p.setResult(v);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            return hookAll(sp, "get", hook);
        } catch (Throwable t) {
            log("SystemProperties hook failed: " + t);
            return 0;
        }
    }

    private static int hookAll(Class<?> clazz, String name, XC_MethodHook cb) {
        try {
            return XposedBridge.hookAllMethods(clazz, name, cb).size();
        } catch (Throwable t) {
            log("hook " + clazz.getSimpleName() + "." + name + " failed: " + t);
            return 0;
        }
    }

    /** Debug: record once per method the target actually consults. */
    private static void note(XC_MethodHook.MethodHookParam p, String value) {
        if (!DEBUG) {
            return;
        }
        try {
            String key = p.method.getDeclaringClass().getSimpleName()
                    + "." + p.method.getName() + " = " + value;
            if (SEEN.add(key)) {
                log("queried " + key);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Runtime config, so no rebuild is needed to change country:
     * persist.minis.simcountry / .simmcc / .simmnc / .simalpha / .simdebug
     */
    private static void loadConfig() {
        String iso = prop("persist.minis.simcountry");
        if (iso == null) {
            log("config: system properties unreadable, keeping built-in " + ISO);
            return;
        }
        if (!iso.isEmpty()) {
            applyCountry(iso.toLowerCase(Locale.US));
        }
        String m = prop("persist.minis.simmcc");
        if (m != null && !m.isEmpty()) {
            MCC = m;
        }
        String n = prop("persist.minis.simmnc");
        if (n != null && !n.isEmpty()) {
            MNC = n;
        }
        String a = prop("persist.minis.simalpha");
        if (a != null && !a.isEmpty()) {
            ALPHA = a;
        }
        String d = prop("persist.minis.simdebug");
        DEBUG = "1".equals(d) || "true".equalsIgnoreCase(d);
        log("config: " + ISO + "/" + MCC + "/" + MNC + "/" + ALPHA
                + (DEBUG ? " (debug)" : ""));
    }

    /** Keeps the country and the operator MCC/MNC consistent, like a real SIM. */
    private static void applyCountry(String iso) {
        if ("cn".equals(iso)) {
            log("refusing cn: nothing to spoof");
            return;
        }
        for (String row : COUNTRIES) {
            String[] f = row.split(":");
            if (f[0].equals(iso)) {
                ISO = f[0];
                MCC = f[1];
                MNC = f[2];
                ALPHA = f[3];
                return;
            }
        }
        ISO = iso;
    }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return String.valueOf(get.invoke(null, key, "")).trim();
        } catch (Throwable t) {
            log("config read failed: " + t);
            return null;
        }
    }

    private static Integer safeInt(String s) {
        try {
            return Integer.valueOf(Integer.parseInt(s.trim()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.i(TAG, msg);
    }
}
