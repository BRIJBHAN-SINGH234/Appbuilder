package com.dotsquares.mobileapp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

/**
 * JS bridge exposed as window.DSMobileApp on the storefront.
 *
 * Usage from storefront JS:
 *   if (window.DSMobileApp) {
 *       const info = JSON.parse(window.DSMobileApp.getDeviceInfo());
 *       console.log(info.app_version, info.device_model);
 *   }
 */
public class DSMobileAppBridge {

    private final Context ctx;
    private final JSONObject config;

    public DSMobileAppBridge(Context ctx, JSONObject config) {
        this.ctx = ctx;
        this.config = config;
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            info.put("app_version", pi.versionName);
            info.put("app_version_code", pi.versionCode);
            info.put("package_id", ctx.getPackageName());
            info.put("device_model", Build.MODEL);
            info.put("device_brand", Build.BRAND);
            info.put("os", "Android");
            info.put("os_version", Build.VERSION.RELEASE);
            info.put("sdk_int", Build.VERSION.SDK_INT);
            info.put("locale", java.util.Locale.getDefault().toString());
            return info.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String getRuntimeConfig() {
        return config != null ? config.toString() : "{}";
    }

    @JavascriptInterface
    public boolean isMobileApp() {
        return true;
    }
}
