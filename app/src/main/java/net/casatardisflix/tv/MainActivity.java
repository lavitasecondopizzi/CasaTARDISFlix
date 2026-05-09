package net.casatardisflix.tv;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String JELLYFIN_URL = "http://casatardisflix.ddns.net:8096";
    private static final String GITHUB_LATEST_RELEASE =
            "https://api.github.com/repos/lavitasecondopizzi/CasaTARDISFlix/releases/latest";

    private WebView webView;
    private View progress;
    private View customSplash;

    private long splashStartTime;
    private static final long MIN_SPLASH_TIME = 3000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        splashStartTime = System.currentTimeMillis();

        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        customSplash = findViewById(R.id.customSplash);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " CasaTardisFlix");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {

                long elapsed = System.currentTimeMillis() - splashStartTime;
                long remaining = MIN_SPLASH_TIME - elapsed;

                if (remaining > 0) {

                    webView.postDelayed(() -> {
                        customSplash.setVisibility(View.GONE);
                    }, remaining);

                } else {
                    customSplash.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            return false;
        });

        webView.loadUrl(JELLYFIN_URL);

        checkForUpdate();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void checkForUpdate() {
        executor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(GITHUB_LATEST_RELEASE).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "CasaTardisFlix");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                connection.disconnect();

                JSONObject obj = new JSONObject(json.toString());

                String tag = obj.optString("tag_name", "");
                String remoteVersion = normalizeVersion(tag);

                String localVersion =
                        getPackageManager()
                                .getPackageInfo(getPackageName(), 0)
                                .versionName;

                String apkUrl = null;
                JSONArray assets = obj.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                if (apkUrl != null && isNewerVersion(remoteVersion, localVersion)) {
                    String finalApkUrl = apkUrl;
                    String finalTag = tag;

                    mainHandler.post(() -> showUpdateDialog(finalTag, finalApkUrl));
                }
            } catch (Exception ignored) {
                // Silenzioso se il controllo fallisce
            }
        });
    }

    private String normalizeVersion(String version) {
        if (version == null) return "0.0.0";
        version = version.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        return version;
    }

    private boolean isNewerVersion(String remote, String local) {
        String[] r = remote.split("\\.");
        String[] l = local.split("\\.");

        int max = Math.max(r.length, l.length);
        for (int i = 0; i < max; i++) {
            int rv = i < r.length ? parseIntSafe(r[i]) : 0;
            int lv = i < l.length ? parseIntSafe(l[i]) : 0;

            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void showUpdateDialog(String tag, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Aggiornamento disponibile")
                .setMessage("Nuova versione trovata: " + tag + "\n\nVuoi aprire il download?")
                .setCancelable(false)
                .setPositiveButton("Aggiorna", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    startActivity(intent);
                })
                .setNegativeButton("Più tardi", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}