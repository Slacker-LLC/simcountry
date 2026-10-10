package com.local.simcountry;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Settings screen for the SIM country the module reports to TikTok.
 *
 * Views are built in code so the module keeps a minimal resource footprint.
 * The visual idea is a SIM card: the current selection is laid out on the gold
 * contact pad of the card, and every row carries its MCC/MNC the way a carrier
 * lookup table does.
 */
public final class ConfigActivity extends Activity {

    private static final int INK = 0xFF101A2E;
    private static final int PAPER = 0xFFEEF1F7;
    private static final int SURFACE = 0xFFFFFFFF;
    private static final int NAVY = 0xFF1B2C52;
    private static final int GOLD = 0xFFC9962E;
    private static final int MUTED = 0xFF7A8598;
    private static final int LINE = 0xFFD8DEE9;

    private LinearLayout chipCard;
    private LinearLayout padsRow;
    private TextView carrierView;
    private LinearLayout rows;
    private String currentIso;
    private String pickedIso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentIso = ConfigStore.readIso(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));

        root.addView(header("SIM Country", 24, INK, Typeface.DEFAULT_BOLD));
        root.addView(body(getString(R.string.subtitle), 13, MUTED, 8, 22));

        chipCard = buildChipCard();
        root.addView(chipCard, wrap());

        root.addView(eyebrow(getString(R.string.eyebrow_pick)));
        root.addView(body("", 1, LINE, 0, 10));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button apply = new Button(this);
        apply.setText(R.string.apply);
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        apply.setAllCaps(false);
        apply.setTextColor(Color.WHITE);
        apply.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        apply.setMinHeight(dp(52));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(NAVY);
        btnBg.setCornerRadius(dp(14));
        apply.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        btnLp.topMargin = dp(16);
        root.addView(apply, btnLp);

        root.addView(body(getString(R.string.footer), 11, MUTED, 14, 0));

        buildRows();
        refreshChip();

        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onApply();
            }
        });

        setContentView(root);
        // The insets controller delegates to the decor view, so it only exists
        // once a content view has been installed.
        applyWindowChrome();
    }

    // --- window chrome -------------------------------------------------

    /**
     * Pin the system bars to the page background. Without this the bars take
     * their colour from the theme, which on an OEM skin is the vendor accent.
     */
    private void applyWindowChrome() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(PAPER);
        w.setNavigationBarColor(PAPER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light, light);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    // --- the SIM card ---------------------------------------------------

    private LinearLayout buildChipCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(NAVY);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));

        card.addView(body(getString(R.string.eyebrow_current), 11, GOLD, 0, 14));

        padsRow = new LinearLayout(this);
        padsRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(padsRow);

        carrierView = body("", 18, SURFACE, 14, 0);
        carrierView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(carrierView);
        return card;
    }

    /** One gold contact pad of the SIM chip, holding one value. */
    private TextView pad(String value) {
        TextView pad = new TextView(this);
        pad.setText(value);
        pad.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pad.setTextColor(NAVY);
        pad.setTypeface(Typeface.MONOSPACE);
        pad.setGravity(Gravity.CENTER);
        pad.setLetterSpacing(0.04f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GOLD);
        bg.setCornerRadius(dp(11));
        pad.setBackground(bg);
        pad.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = wrap();
        lp.rightMargin = dp(10);
        padsRow.addView(pad, lp);
        return pad;
    }

    private void refreshChip() {
        Countries.Entry e = Countries.find(currentIso);
        String iso = e == null ? currentIso : e.iso;
        String mcc = e == null ? "— —" : e.mcc;
        String mnc = e == null ? "— —" : e.mnc;
        carrierView.setText(e == null ? "" : e.carrier);

        padsRow.removeAllViews();
        pad(iso.toUpperCase(java.util.Locale.US));
        pad(mcc);
        pad(mnc);
    }

    // --- the country list ----------------------------------------------

    private void buildRows() {
        List<Countries.Entry> entries = Countries.distinct();
        pickedIso = currentIso;
        for (int i = 0; i < entries.size(); i++) {
            final Countries.Entry e = entries.get(i);
            final RadioButton rb = new RadioButton(this);
            rb.setClickable(false);
            rb.setButtonDrawable(indicator());
            rb.setText(e.label());
            rb.setTextColor(INK);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            rb.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            rb.setPadding(dp(16), dp(12), dp(16), dp(4));
            rb.setMinHeight(dp(52));
            rb.setId(View.generateViewId());
            rb.setTag(e.iso);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(pressFeedback());

            row.addView(rb);

            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            TextView carrier = body(e.carrier, 12, MUTED, 0, 0);
            carrier.setPadding(dp(52), 0, dp(12), dp(12));
            meta.addView(carrier, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            meta.addView(code(e.mcc + " " + e.mnc), wrap());
            row.addView(meta);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickedIso = (String) rb.getTag();
                    syncChecks();
                }
            });

            rows.addView(row, wrap());
        }
        syncChecks();
    }

    /** Single-selection done by hand: RadioGroup only manages direct children. */
    private void syncChecks() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) rows.getChildAt(i);
            RadioButton rb = (RadioButton) row.getChildAt(0);
            boolean on = rb.getTag().equals(pickedIso);
            rb.setChecked(on);
        }
    }

    private StateListDrawable pressFeedback() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, wash());
        sld.addState(new int[]{}, none());
        return sld;
    }

    private GradientDrawable wash() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x14000000);
        d.setCornerRadius(dp(16));
        return d;
    }

    private GradientDrawable none() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setCornerRadius(dp(16));
        return d;
    }

    /** Gold ring when checked, hairline ring when not. */
    private StateListDrawable indicator() {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_checked}, ring(GOLD, GOLD));
        sld.addState(new int[]{}, ring(LINE, Color.TRANSPARENT));
        return sld;
    }

    private GradientDrawable ring(int stroke, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(dp(2), stroke);
        d.setColor(fill);
        d.setSize(dp(22), dp(22));
        return d;
    }

    // --- small builders ------------------------------------------------

    private TextView code(String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(MUTED);
        v.setTypeface(Typeface.MONOSPACE);
        v.setLetterSpacing(0.10f);
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setPadding(0, 0, dp(16), dp(12));
        return v;
    }

    private TextView eyebrow(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setTextColor(GOLD);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setLetterSpacing(0.18f);
        v.setPadding(0, dp(10), 0, 0);
        return v;
    }

    private TextView header(String text, int sp, int color, Typeface tf) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(tf);
        v.setPadding(0, dp(4), 0, 0);
        return v;
    }

    private TextView body(String text, int sp, int color, int top, int bottom) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.25f);
        v.setPadding(0, dp(top), 0, dp(bottom));
        return v;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // --- action --------------------------------------------------------

    private void onApply() {
        final String iso = pickedIso;
        if (iso == null || iso.isEmpty()) {
            Toast.makeText(this, R.string.pick_one, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.applying, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int status = ConfigStore.apply(ConfigActivity.this, iso);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentIso = iso;
                        refreshChip();
                        int msg;
                        if (status == ConfigStore.APPLIED) {
                            msg = R.string.applied;
                        } else if (status == ConfigStore.SAVED) {
                            msg = R.string.saved_manual;
                        } else {
                            msg = R.string.save_failed;
                        }
                        Toast.makeText(ConfigActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
