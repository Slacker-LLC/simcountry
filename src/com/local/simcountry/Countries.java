package com.local.simcountry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one source of truth for what a spoofed SIM looks like.
 * Shared by the hook (runs inside TikTok) and the settings UI (runs in its own
 * process), so it must stay dependency-free.
 *
 * Row format: iso:mcc:mnc:carrier:zhName:enName
 *
 * Only regions TikTok actually serves belong here. Hong Kong is left out
 * because TikTok withdrew from it, and India because it is banned there; a
 * reader of this table should never be offered a region that cannot help.
 */
public final class Countries {

    public static final String[] ROWS = {
            "us:310:260:T-Mobile:美国:United States",
            "ca:302:220:TELUS:加拿大:Canada",
            "gb:234:15:Vodafone UK:英国:United Kingdom",
            "uk:234:15:Vodafone UK:英国:United Kingdom",
            "de:262:01:Telekom.de:德国:Germany",
            "fr:208:01:Orange F:法国:France",
            "jp:440:10:NTT docomo:日本:Japan",
            "kr:450:05:SKTelecom:韩国:South Korea",
            "sg:525:01:SingTel:新加坡:Singapore",
            "au:505:01:Telstra:澳大利亚:Australia",
            "tw:466:92:Chunghwa Telecom:中国台湾:Taiwan",
            "my:502:12:Maxis:马来西亚:Malaysia",
            "th:520:01:AIS:泰国:Thailand",
            "vn:452:01:Viettel:越南:Vietnam",
            "ph:515:02:Globe:菲律宾:Philippines",
            "id:510:10:Telkomsel:印度尼西亚:Indonesia",
            "ru:250:01:MTS:俄罗斯:Russia",
            "br:724:06:Vivo:巴西:Brazil",
            "mx:334:020:Telcel:墨西哥:Mexico",
            "es:214:01:Vodafone ES:西班牙:Spain",
            "it:222:01:TIM:意大利:Italy",
            "nl:204:04:Vodafone NL:荷兰:Netherlands",
            "tr:286:01:Turkcell:土耳其:Turkey",
            "ae:424:02:Etisalat:阿联酋:United Arab Emirates",
            "sa:420:01:STC:沙特阿拉伯:Saudi Arabia",
    };

    public static final class Entry {
        public final String iso;
        public final String mcc;
        public final String mnc;
        public final String carrier;
        public final String nameZh;
        public final String nameEn;

        Entry(String iso, String mcc, String mnc,
              String carrier, String nameZh, String nameEn) {
            this.iso = iso;
            this.mcc = mcc;
            this.mnc = mnc;
            this.carrier = carrier;
            this.nameZh = nameZh;
            this.nameEn = nameEn;
        }

        /** Display label in the device language. */
        public String label() {
            return Locale.getDefault().getLanguage().equals("zh")
                    ? nameZh : nameEn;
        }
    }

    private Countries() {
    }

    /** Parsed table in table order. */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<Entry>(ROWS.length);
        for (String row : ROWS) {
            out.add(parse(row));
        }
        return out;
    }

    /** The single row for an ISO code, or null when the code is unknown. */
    public static Entry find(String iso) {
        if (iso == null) {
            return null;
        }
        for (String row : ROWS) {
            Entry e = parse(row);
            if (e.iso.equals(iso)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Distinct entries only. `uk` is an alias of `gb` and both carry the same
     * carrier, so the key is the carrier, not the ISO code.
     */
    public static List<Entry> distinct() {
        List<Entry> out = new ArrayList<Entry>();
        for (String row : ROWS) {
            Entry e = parse(row);
            if (find(out, e.carrier) == null) {
                out.add(e);
            }
        }
        return out;
    }

    private static Entry find(List<Entry> list, String carrier) {
        for (Entry e : list) {
            if (e.carrier.equals(carrier)) {
                return e;
            }
        }
        return null;
    }

    private static Entry parse(String row) {
        String[] f = row.split(":");
        return new Entry(f[0], f[1], f[2], f[3], f[4], f[5]);
    }
}
