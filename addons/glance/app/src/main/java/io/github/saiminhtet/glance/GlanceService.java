package io.github.saiminhtet.glance;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Watches remote presses (never blocking one) and which app is in front. Every press draws the weather top left and
 * the time and date top right, or keeps them up, until {@link #SHOW_MS} after the last press, as an accessibility
 * overlay. Not over Home. With Glance switched off on its tile it stops filtering keys and draws nothing.
 */
public final class GlanceService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS = "glance";
    static final String KEY_ON = "on";

    private static final long SHOW_MS = 4000;
    private static final Uri WEATHER = Uri.parse("content://io.github.sumyabazar1.weather.status/status");
    /** Never shown over these: Home (and its clock screen) already shows the weather and the time. */
    private static final String[] EXCLUDED = {"com.gathin.tvhome", "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx"};
    /** Window events from these say nothing about what is being watched. */
    private static final String[] IGNORED = {"com.android.systemui", "android"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable fadeOut = this::fadeOut;
    private SharedPreferences prefs;
    private WindowManager windows;
    private String foreground = "";
    /** The last weather line and when it stops being true (epoch ms; 0: never). Main thread only. */
    private String weather = "";
    private long weatherExpires;
    private View shown;
    private TextView weatherView;

    @Override
    protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        applyOnOff();
        refreshWeather();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (KEY_ON.equals(key)) applyOnOff();
    }

    private boolean on() {
        return prefs == null || prefs.getBoolean(KEY_ON, true);
    }

    /** Off: no key filtering at all, so the remote behaves as if Glance were not installed. */
    private void applyOnOff() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if (on()) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        if (!on()) hide();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || contains(IGNORED, pkg)) return;
        foreground = pkg;
        if (contains(EXCLUDED, pkg)) hide();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Held keys count too (their repeats arrive as downs), so scrolling keeps it up.
        if (event.getAction() == KeyEvent.ACTION_DOWN && on() && showsOver(foreground)) {
            main.post(this::show);
        }
        return false;   // every key goes on to the app in front
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        hide();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        io.shutdownNow();
        super.onDestroy();
    }

    // ---- the weather, from the Weather add-on's provider ---------------------------------------------

    /** Asks the Weather add-on off the main thread; a line arriving while the glance is up fills it in. */
    private void refreshWeather() {
        io.execute(() -> {
            String text = "";
            long expires = 0;
            try (Cursor c = getContentResolver().query(WEATHER, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int t = c.getColumnIndex("text");
                    int e = c.getColumnIndex("expires");
                    if (t >= 0 && c.getString(t) != null) text = c.getString(t).trim();
                    if (e >= 0) expires = c.getLong(e);
                }
            } catch (RuntimeException e) {
                // No Weather add-on, or it failed: the glance shows the time only.
            }
            String line = text;
            long until = expires;
            main.post(() -> {
                weather = line;
                weatherExpires = until;
                if (weatherView != null && currentWeather() != null) {
                    weatherView.setText(currentWeather());
                    weatherView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    /** The weather line while it is still true, or null. */
    private String currentWeather() {
        if (weather.isEmpty()) return null;
        if (weatherExpires > 0 && System.currentTimeMillis() >= weatherExpires) return null;
        return weather;
    }

    // ---- the overlay --------------------------------------------------------------------------------

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView line(String text, Typeface font) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setTypeface(font);
        t.setTextColor(Color.WHITE);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void show() {
        if (windows == null || !on()) return;
        if (shown != null) {   // already up: another press keeps it the full time from now
            main.removeCallbacks(fadeOut);
            shown.animate().cancel();
            shown.setAlpha(1f);
            main.postDelayed(fadeOut, SHOW_MS);
            return;
        }
        refreshWeather();
        Typeface font = getResources().getFont(R.font.outfit_regular);
        Date now = new Date();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String time = DateFormat.getTimeFormat(this).format(now) + "  ·  "
                + DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), now);

        FrameLayout root = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (int) dp(140)));

        int side = (int) dp(48), top = (int) dp(36);
        weatherView = line(currentWeather() == null ? "" : currentWeather(), font);
        weatherView.setVisibility(currentWeather() == null ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams left = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        left.setMargins(side, top, side, 0);
        root.addView(weatherView, left);
        FrameLayout.LayoutParams right = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        right.setMargins(side, top, side, 0);
        root.addView(line(time, font), right);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        root.setAlpha(0f);
        try {
            windows.addView(root, p);
        } catch (RuntimeException e) {
            weatherView = null;
            return;
        }
        shown = root;
        root.animate().alpha(1f).setDuration(250).start();
        main.postDelayed(fadeOut, SHOW_MS);
    }

    private void fadeOut() {
        View v = shown;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            if (shown == v) hide();
        }).start();
    }

    /** Nothing stays on screen between glances: the window exists only while it shows. */
    private void hide() {
        main.removeCallbacks(fadeOut);
        if (shown != null) {
            shown.animate().cancel();
            try {
                windows.removeView(shown);
            } catch (RuntimeException ignored) {
            }
            shown = null;
            weatherView = null;
        }
    }

    /**
     * Not over Home, and not before the first window change tells which app is in front: after a reboot or an
     * update the service may bind with an app already on screen, and Home is the likeliest one.
     */
    private static boolean showsOver(String pkg) {
        return !pkg.isEmpty() && !contains(EXCLUDED, pkg);
    }

    private static boolean contains(String[] list, String s) {
        for (String x : list) if (x.equals(s)) return true;
        return false;
    }
}
