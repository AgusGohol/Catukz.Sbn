package com.catukz.sbn.scrape;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/** Mem-parsing HTML halaman Analytics SoundOn menjadi angka stream & daftar lagu. */
public class SoundOnParser {

    public static class Track {
        public String title = "";
        public String artist = "";
        public long streams;
        public long listeners;
    }

    public static class Result {
        public long totalStreams;
        public long listeners;
        public long saves;
        public List<Track> tracks = new ArrayList<>();
    }

    public static Result parse(String html) {
        Document doc = Jsoup.parse(html);
        Result r = new Result();
        r.totalStreams = metric(overview(doc, "Streams"));
        r.listeners = metric(overview(doc, "Listeners"));
        r.saves = metric(overview(doc, "Saves"));

        Elements rows = doc.select("table.semi-table tbody tr.semi-table-row");
        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.isEmpty()) continue;
            Track t = new Track();
            Element titleEl = row.selectFirst("strong span");
            if (titleEl == null) titleEl = row.selectFirst("strong");
            t.title = titleEl != null ? titleEl.text().trim() : "";
            Element artistEl = row.selectFirst("[class^=artist-tags] a");
            t.artist = artistEl != null ? artistEl.text().trim() : "";
            // Kolom: 0 Judul, 1 Videos, 2 Views, 3 Engagement, 4 Streams, 5 Listeners, 6 Action
            if (tds.size() > 4) t.streams = metric(tds.get(4).text());
            if (tds.size() > 5) t.listeners = metric(tds.get(5).text());
            if (!t.title.isEmpty()) r.tracks.add(t);
        }
        return r;
    }

    private static String overview(Document doc, String label) {
        for (Element h6 : doc.select("h6")) {
            if (h6.text().trim().equals(label)) {
                Element sib = h6.nextElementSibling();
                Element h5 = sib != null ? sib.selectFirst("h5") : null;
                if (h5 == null && h6.parent() != null) h5 = h6.parent().selectFirst("h5");
                return h5 != null ? h5.text().trim() : null;
            }
        }
        return null;
    }

    /** "36.4K" -> 36400, "2.7K" -> 2700, "311" -> 311, "1.2M" -> 1200000. */
    public static long metric(String s) {
        if (s == null) return 0;
        s = s.trim().replace(",", "");
        if (s.isEmpty() || s.equals("-")) return 0;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'K' || last == 'k') { mult = 1_000; s = s.substring(0, s.length() - 1); }
        else if (last == 'M' || last == 'm') { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
        else if (last == 'B' || last == 'b') { mult = 1_000_000_000L; s = s.substring(0, s.length() - 1); }
        try { return Math.round(Double.parseDouble(s.trim()) * mult); }
        catch (Exception e) { return 0; }
    }

    /** Self-test: baca snapshot json lalu cetak hasil parse. */
    public static void main(String[] a) throws Exception {
        com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(java.nio.file.Paths.get(a[0])));
        Result r = parse(n.path("html").asText());
        System.out.println("totalStreams=" + r.totalStreams + "  listeners=" + r.listeners + "  saves=" + r.saves);
        System.out.println("jumlah tracks=" + r.tracks.size());
        for (Track t : r.tracks) {
            System.out.println("  - " + t.title + "  [" + t.artist + "]  streams=" + t.streams + " listeners=" + t.listeners);
        }
    }
}
