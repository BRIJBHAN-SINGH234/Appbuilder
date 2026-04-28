package com.dotsquares.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loads runtime config:
 *   - Bundled snapshot from app/src/main/assets/config.json (always available)
 *   - Fresh from <store_url>/dsmobileapp/config (cached 5 min in SharedPreferences)
 */
public class ConfigLoader {

    private static final String TAG       = "DSConfigLoader";
    private static final String PREF_NAME = "dsmobileapp";
    private static final String PREF_KEY  = "runtime_config_json";
    private static final String PREF_TS   = "runtime_config_ts";
    private static final long   CACHE_MS  = TimeUnit.MINUTES.toMillis(5);

    public interface Callback {
        void onLoaded(JSONObject config);
    }

    public static JSONObject loadBundled(Context ctx) {
        try (InputStream is = ctx.getAssets().open("config.json")) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "Could not load bundled config: " + e.getMessage());
            return new JSONObject();
        }
    }

    public void fetchAsync(Context ctx, String storeUrl, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            JSONObject cached = readFromCache(ctx);
            if (cached != null) {
                cb.onLoaded(cached);
                return;
            }
            JSONObject fresh = fetchSync(storeUrl);
            if (fresh != null) {
                writeToCache(ctx, fresh);
                cb.onLoaded(fresh);
            }
        });
    }

    private JSONObject fetchSync(String storeUrl) {
        try {
            String urlStr = storeUrl.endsWith("/") ? storeUrl + "dsmobileapp/config" : storeUrl + "/dsmobileapp/config";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchSync failed: " + e.getMessage());
            return null;
        }
    }

    private JSONObject readFromCache(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long ts = sp.getLong(PREF_TS, 0);
        if (System.currentTimeMillis() - ts > CACHE_MS) return null;
        String json = sp.getString(PREF_KEY, null);
        if (json == null) return null;
        try { return new JSONObject(json); } catch (Exception e) { return null; }
    }

    private void writeToCache(Context ctx, JSONObject obj) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        e.putString(PREF_KEY, obj.toString());
        e.putLong(PREF_TS, System.currentTimeMillis());
        e.apply();
    }
}
