package io.github.saiminhtet.backlight;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A slim slider at the bottom of the screen for the TV's own panel backlight, over whatever is playing. ◀ ▶ move
 * it, OK or Back closes it, and it closes by itself after a few idle seconds.
 *
 * The backlight is MediaTek's global setting picture_backlight (0 to 100), the value the TV's Picture menu
 * changes; the picture service applies it the moment it is written. TVs without that setting get a message and
 * no writes.
 */
public final class BacklightActivity extends Activity {
    static final String SETTING = "picture_backlight";
    private static final long IDLE_MS = 4000;
    private static final long MESSAGE_IDLE_MS = 10000;
    /** Holding ◀ ▶ repeats about every 50 ms; the picture service only needs to hear about it this often. */
    private static final long WRITE_EVERY_MS = 120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private final Runnable flush = this::write;

    private TextView value;
    private TextView hint;
    private ProgressBar bar;
    private int level;
    private int written;
    private long lastWrite;
    private boolean usable;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_backlight);
        value = findViewById(R.id.value);
        hint = findViewById(R.id.hint);
        bar = findViewById(R.id.bar);
        bar.setMax(Level.MAX);

        String stored = Settings.Global.getString(getContentResolver(), SETTING);
        if (stored == null) {
            show("—", getString(R.string.unsupported));
            return;
        }
        try {
            level = Integer.parseInt(stored.trim());
        } catch (NumberFormatException e) {
            show("—", getString(R.string.unsupported));
            return;
        }
        written = level;
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            show(String.valueOf(level), getString(R.string.no_permission, getPackageName()));
            bar.setProgress(level);
            return;
        }
        usable = true;
        update();
        idle(IDLE_MS);
    }

    private void show(String number, String message) {
        value.setText(number);
        hint.setText(message);
        idle(MESSAGE_IDLE_MS);
    }

    private void update() {
        value.setText(String.valueOf(level));
        bar.setProgress(level);
    }

    private void idle(long ms) {
        handler.removeCallbacks(close);
        handler.postDelayed(close, ms);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!usable) return true;
                level = Level.next(level, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                update();
                long wait = lastWrite + WRITE_EVERY_MS - SystemClock.uptimeMillis();
                handler.removeCallbacks(flush);
                if (wait <= 0) write();
                else handler.postDelayed(flush, wait);
                idle(IDLE_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                idle(usable ? IDLE_MS : MESSAGE_IDLE_MS);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            handler.removeCallbacks(flush);
            write();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void write() {
        if (!usable || level == written) return;
        ContentResolver cr = getContentResolver();
        try {
            Settings.Global.putInt(cr, SETTING, level);
            written = level;
            lastWrite = SystemClock.uptimeMillis();
        } catch (SecurityException e) {
            usable = false;
            show(String.valueOf(written), getString(R.string.no_permission, getPackageName()));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(flush);
        write();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Home or another app came up: a pop-up has nothing to come back to.
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
