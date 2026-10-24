package io.github.saiminhtet.glance;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Glance tile: one switch. On and Off say whether the key service filters keys at all. When the service is not
 * enabled (a force-stop takes it off the list), the row says so and OK puts it back, the only thing Glance writes
 * with WRITE_SECURE_SETTINGS.
 */
public final class GlanceActivity extends Activity {
    private SharedPreferences prefs;
    private TextView state, hint;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences(GlanceService.PREFS, Context.MODE_PRIVATE);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D0F14);
        page.setPadding((int) (96 * d), (int) (72 * d), (int) (96 * d), (int) (48 * d));

        page.addView(text(getString(R.string.title), 40, 0xFFFFFFFF));
        TextView intro = text(getString(R.string.intro), 18, 0xB3FFFFFF);
        intro.setPadding(0, (int) (12 * d), 0, (int) (32 * d));
        page.addView(intro);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setPadding((int) (32 * d), (int) (22 * d), (int) (32 * d), (int) (22 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(20 * d);
        bg.setStroke((int) (2 * d), 0x99FFFFFF);
        row.setBackground(bg);
        state = text("", 26, Color.WHITE);
        hint = text("", 16, 0xB3FFFFFF);
        hint.setPadding(0, (int) (6 * d), 0, 0);
        row.addView(state);
        row.addView(hint);
        row.setOnClickListener(v -> toggle());
        page.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView note = text(getString(R.string.volume_note), 15, 0x80FFFFFF);
        note.setPadding(0, (int) (28 * d), 0, 0);
        page.addView(note);
        setContentView(page);
        row.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        return t;
    }

    private String component() {
        return new ComponentName(this, GlanceService.class).flattenToShortString();
    }

    private boolean serviceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        String full = new ComponentName(this, GlanceService.class).flattenToString();
        for (String s : TextUtils.split(list, ":")) if (s.equals(component()) || s.equals(full)) return true;
        return false;
    }

    private void render() {
        if (!serviceEnabled()) {
            state.setText(R.string.stopped);
            hint.setText(R.string.hint_stopped);
        } else if (prefs.getBoolean(GlanceService.KEY_ON, true)) {
            state.setText(R.string.state_on);
            hint.setText(R.string.hint_on);
        } else {
            state.setText(R.string.state_off);
            hint.setText(R.string.hint_off);
        }
    }

    private void toggle() {
        if (!serviceEnabled()) {
            restoreService();
        } else {
            prefs.edit().putBoolean(GlanceService.KEY_ON, !prefs.getBoolean(GlanceService.KEY_ON, true)).apply();
        }
        render();
    }

    /** Appends Glance's own service to the enabled list, keeping every entry already there. */
    private void restoreService() {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            hint.setText(getString(R.string.no_permission, getPackageName()));
            return;
        }
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String next = TextUtils.isEmpty(list) || "null".equals(list) ? component() : list + ":" + component();
        try {
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            prefs.edit().putBoolean(GlanceService.KEY_ON, true).apply();
        } catch (SecurityException e) {
            hint.setText(getString(R.string.no_permission, getPackageName()));
        }
    }
}
