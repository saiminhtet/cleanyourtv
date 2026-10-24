package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * One short line, "24° Cloudy", for the Clean Your TV launcher's home screen (its status_provider key):
 * content://io.github.sumyabazar1.weather.status/status answers a "text" column, an optional "detail" line
 * (feels-like, humidity, wind; the launcher's clock screen shows it) and an "expires" time in epoch ms.
 * Enabled only with options.xml home_status. The launcher asks off its main thread while Home is on screen, so a
 * stale line (older than {@link Forecast#REFRESH_MS}) is fetched right here, at most once per
 * {@link Forecast#RETRY_MS}; nothing runs when nobody asks. The line names no place.
 */
public final class StatusProvider extends ContentProvider {
    private static final String PREFS = "status";
    /** The launcher hides a line this old: better nothing than the morning's weather at night. */
    private static final long KEEP_MS = 3 * 60 * 60_000L;
    private static final String[] COLUMNS = {"text", "detail", "expires"};
    private static final Object LOCK = new Object();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (context == null) return out;
        SharedPreferences place = context.getSharedPreferences(WeatherActivity.PREFS, Context.MODE_PRIVATE);
        if (!place.contains("name")) return out;
        SharedPreferences status = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (now - status.getLong("at", 0) > Forecast.REFRESH_MS && now - status.getLong("tried", 0) > Forecast.RETRY_MS) {
                status.edit().putLong("tried", now).apply();
                try {
                    save(context, Forecast.now(Double.longBitsToDouble(place.getLong("lat", 0)),
                            Double.longBitsToDouble(place.getLong("lon", 0))));
                } catch (Exception e) {
                    // Offline: keep the last line until it expires, and try again after RETRY_MS.
                }
            }
        }
        String text = status.getString("text", null);
        if (text != null) out.addRow(new Object[]{text, status.getString("detail", ""), status.getLong("at", 0) + KEEP_MS});
        return out;
    }

    /** Keeps the line for the launcher; WeatherActivity calls this too, so an open page saves a fetch. */
    static void save(Context context, Forecast.Report r) {
        String text = context.getString(R.string.status_line, r.tempC, context.getString(Forecast.sky(r.code, r.day)));
        String detail = context.getString(R.string.feels_like, r.feelsC)
                + " · " + context.getString(R.string.humidity, r.humidity)
                + " · " + context.getString(R.string.wind, r.windKmh);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("text", text)
                .putString("detail", detail)
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
