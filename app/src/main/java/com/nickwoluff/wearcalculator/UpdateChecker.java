package com.nickwoluff.wearcalculator;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 从 GitHub 最新正式 Release 读取版本信息，网络工作始终在后台线程执行。 */
public final class UpdateChecker {
    private static final String RELEASE_URL = "https://api.github.com/repos/NickWoluff/WearCalculator/releases/latest";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private volatile HttpURLConnection activeConnection;

    public interface Callback {
        void onResult(String tagName, String releaseUrl);
        void onError(int statusCode);
    }

    public UpdateChecker() {
    }

    public void check(Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_URL).openConnection();
                activeConnection = connection;
                if (closed) return;
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "WearCalculator");
                int status = connection.getResponseCode();
                if (status == 404) {
                    postError(callback, status);
                    return;
                }
                if (status == 403 || status == 429 || status < 200 || status >= 300) {
                    postError(callback, status);
                    return;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject release = new JSONObject(body.toString());
                String tag = release.optString("tag_name", "");
                String url = release.optString("html_url", "https://github.com/NickWoluff/WearCalculator/releases");
                if (tag.isEmpty()) throw new IllegalStateException("Missing tag");
                main.post(() -> { if (!closed) callback.onResult(tag, url); });
            } catch (Exception exception) {
                postError(callback, -1);
            } finally {
                if (connection != null) connection.disconnect();
                activeConnection = null;
            }
        });
    }

    private void postError(Callback callback, int status) {
        main.post(() -> { if (!closed) callback.onError(status); });
    }

    public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }
}
