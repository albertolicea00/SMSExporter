package com.albertolicea.smsexporter;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/albertolicea00/SMSExporter/releases/latest";

    static final class UpdateInfo {
        final String version;
        final String url;

        UpdateInfo(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }

    private UpdateChecker() {}

    /** Blocking network call. Returns null if no update is available or the check fails. */
    static UpdateInfo fetchLatestIfNewer(String currentVersion) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "SMSExporter-Android");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            JSONObject json = new JSONObject(readAll(conn.getInputStream()));
            String tag = json.optString("tag_name", "");
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;
            String htmlUrl = json.optString("html_url",
                    "https://github.com/albertolicea00/SMSExporter/releases");

            if (latest.isEmpty() || !isNewer(latest, currentVersion)) return null;
            return new UpdateInfo(latest, htmlUrl);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isNewer(String latest, String current) {
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? parseIntSafe(a[i]) : 0;
            int y = i < b.length ? parseIntSafe(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
