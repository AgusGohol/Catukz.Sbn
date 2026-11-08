package com.catukz.sbn.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Mengecek rilis terbaru di GitHub Releases dan membandingkan versi.
 * Ganti OWNER/REPO sesuai repo GitHub Anda.
 */
public class UpdateChecker {
    public static final String OWNER = "AgusGohol";
    public static final String REPO = "Catukz.Sbn";

    public static class Result {
        public final boolean updateAvailable;
        public final String latestTag;
        public final String currentVersion;
        public final String htmlUrl;
        public final String error;

        Result(boolean updateAvailable, String latestTag, String currentVersion, String htmlUrl, String error) {
            this.updateAvailable = updateAvailable;
            this.latestTag = latestTag;
            this.currentVersion = currentVersion;
            this.htmlUrl = htmlUrl;
            this.error = error;
        }
    }

    public static Result check(String currentVersion) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Catukz.Sbn")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                return new Result(false, null, currentVersion, null, "Belum ada release di GitHub");
            }
            if (resp.statusCode() != 200) {
                return new Result(false, null, currentVersion, null, "HTTP " + resp.statusCode());
            }
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(resp.body());
            String tag = root.path("tag_name").asText("");
            String url = root.path("html_url").asText("");
            String latest = tag.replaceFirst("^v", "");
            boolean newer = isNewer(latest, currentVersion);
            return new Result(newer, tag, currentVersion, url, null);
        } catch (Exception e) {
            return new Result(false, null, currentVersion, null, e.getMessage());
        }
    }

    static boolean isNewer(String latest, String current) {
        try {
            String[] a = latest.split("\\.");
            String[] b = current.split("\\.");
            int n = Math.max(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int ai = i < a.length ? parseInt(a[i]) : 0;
                int bi = i < b.length ? parseInt(b[i]) : 0;
                if (ai != bi) return ai > bi;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int parseInt(String s) {
        s = s.replaceAll("[^0-9]", "");
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }
}
