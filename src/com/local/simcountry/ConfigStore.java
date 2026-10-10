package com.local.simcountry;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How the settings screen reaches the hook.
 *
 * <p>The hook runs inside TikTok's process and cannot read this app's private
 * files. The module declares {@code xposedsharedprefs}, so the framework mirrors
 * these preferences into a world-readable location that the hook reaches through
 * {@code XSharedPreferences}. Saving therefore needs no root.
 *
 * <p>System properties are a second, command-line-friendly channel: the settings
 * screen mirrors the choice there when root happens to be available, and
 * {@code setprop} alone still works for anyone automating this.
 */
public final class ConfigStore {

    public static final String PREFS = "config";
    public static final String KEY_ISO = "iso";
    public static final String MODULE_PACKAGE = "com.local.simcountry";

    public static final String PROP_ISO = "persist.minis.simcountry";
    public static final String PROP_MCC = "persist.minis.simmcc";
    public static final String PROP_MNC = "persist.minis.simmnc";
    public static final String PROP_ALPHA = "persist.minis.simalpha";
    public static final String PROP_DEBUG = "persist.minis.simdebug";

    private ConfigStore() {
    }

    /** What the UI shows as current: the stored ISO, defaulting to us. */
    public static String readIso(Context context) {
        return prefs(context).getString(KEY_ISO, "us");
    }

    /** 2 = root applied it, 1 = saved only, 0 = failed. */
    public static final int APPLIED = 2;
    public static final int SAVED = 1;
    public static final int FAILED = 0;

    /**
     * Save the country where the hook will read it.
     *
     * <p>SharedPreferences is the channel the hook actually reads (the framework
     * exposes it to the target process), so saving needs no root. Root only buys
     * the system-property mirror and the automatic TikTok restart.
     *
     * @return APPLIED / SAVED / FAILED
     */
    public static int apply(Context context, String iso) {
        try {
            if (!prefs(context).edit().putString(KEY_ISO, iso).commit()) {
                return FAILED;
            }
        } catch (Throwable t) {
            return FAILED;
        }
        exposePrefs(context);

        Countries.Entry e = Countries.find(iso);
        String mcc = e == null ? "" : e.mcc;
        String mnc = e == null ? "" : e.mnc;
        String alpha = e == null ? "" : e.carrier;

        StringBuilder script = new StringBuilder();
        script.append("setprop ").append(PROP_ISO).append(' ').append(iso).append(" && ");
        script.append("setprop ").append(PROP_MCC).append(' ').append(mcc).append(" && ");
        script.append("setprop ").append(PROP_MNC).append(' ').append(mnc).append(" && ");
        script.append("setprop ").append(PROP_ALPHA).append(" '").append(alpha).append("' && ");
        script.append("am force-stop com.zhiliaoapp.musically");

        return sh(script.toString()) ? APPLIED : SAVED;
    }

    /** The exact command to run by hand when root is not available. */
    public static String manualCommand(String iso) {
        Countries.Entry e = Countries.find(iso);
        String mcc = e == null ? "" : e.mcc;
        String mnc = e == null ? "" : e.mnc;
        String alpha = e == null ? "" : e.carrier;
        return "setprop " + PROP_ISO + " " + iso + "\n"
                + "setprop " + PROP_MCC + " " + mcc + "\n"
                + "setprop " + PROP_MNC + " " + mnc + "\n"
                + "setprop " + PROP_ALPHA + " '" + alpha + "'\n"
                + "am force-stop com.zhiliaoapp.musically";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean sh(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    /**
     * Make the prefs file readable from TikTok's process, so the hook can fall
     * back to it when system properties are empty. Needs root, so it only helps
     * when root is already available; harmless otherwise.
     */
    public static void exposePrefs(Context context) {
        try {
            String dir = context.getApplicationInfo().dataDir + "/shared_prefs/";
            sh("chmod 755 " + dir + " && chmod 644 " + dir + PREFS + ".xml");
        } catch (Throwable ignored) {
        }
    }
}
