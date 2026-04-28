package com.dotsquares.mobileapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private JSONObject config;
    private String storeUrl;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swipe   = findViewById(R.id.swipe);
        webView = findViewById(R.id.webview);

        config   = ConfigLoader.loadBundled(this);
        storeUrl = config.optString("store_url", getString(R.string.store_url_default));

        // Try to fetch a fresh runtime config from /dsmobileapp/config (5-min cache)
        new ConfigLoader().fetchAsync(this, storeUrl, fresh -> {
            if (fresh != null) {
                config = fresh;
                String newUrl = fresh.optJSONObject("runtime") != null
                        ? fresh.optJSONObject("runtime").optString("store_url", storeUrl)
                        : storeUrl;
                if (!newUrl.equals(storeUrl)) {
                    storeUrl = newUrl;
                    runOnUiThread(() -> webView.loadUrl(storeUrl));
                }
            }
        });

        configureWebView();
        webView.loadUrl(storeUrl);

        swipe.setOnRefreshListener(() -> webView.reload());
        swipe.setEnabled(getRuntimeFlag("enable_pull_to_refresh", true));
    }

    private void configureWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(false);

        String suffix = readManifestMeta("ds_user_agent_suffix", "DSMobileApp/1.0");
        ws.setUserAgentString(ws.getUserAgentString() + " " + suffix);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                swipe.setRefreshing(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipe.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;

                boolean allowExternal = getRuntimeFlag("allow_external_urls", false);
                String host = uri.getHost();
                String storeHost = Uri.parse(storeUrl).getHost();

                if (host != null && storeHost != null && !host.equalsIgnoreCase(storeHost)) {
                    if (!allowExternal) {
                        // Open in device browser
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        } catch (Exception ignored) {}
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame() && !isOnline()) {
                    if (getRuntimeFlag("enable_offline_page", true)) {
                        view.loadUrl("file:///android_asset/offline.html");
                    }
                }
            }
        });

        webView.addJavascriptInterface(new DSMobileAppBridge(this, config), "DSMobileApp");
    }

    private boolean getRuntimeFlag(String key, boolean def) {
        JSONObject runtime = config.optJSONObject("runtime");
        if (runtime == null) return def;
        return runtime.optBoolean(key, def);
    }

    private String readManifestMeta(String key, String def) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(
                    getPackageName(),
                    android.content.pm.PackageManager.GET_META_DATA);
            String v = ai.metaData.getString(key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
