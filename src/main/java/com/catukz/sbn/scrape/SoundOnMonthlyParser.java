package com.catukz.sbn.scrape;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mem-parsing tabel "Monthly statements" SoundOn: pendapatan per bulan. */
public class SoundOnMonthlyParser {

    public static class MonthEarning {
        public String month;   // "YYYY-MM"
        public double amount;
        public String currency = "IDR";
    }

    public static List<MonthEarning> parse(String html) {
        Document doc = Jsoup.parse(html);
        List<MonthEarning> out = new ArrayList<>();
        for (Element td : doc.select("td")) {
            String t = td.text().trim();
            if (!t.matches("\\d{4}-\\d{2}")) continue;
            Element row = td.parent();
            if (row == null) continue;
            String amountCell = null;
            for (Element cell : row.select("td")) {
                if (cell.text().contains("IDR")) { amountCell = cell.text(); break; }
            }
            if (amountCell == null) continue;
            MonthEarning me = new MonthEarning();
            me.month = t;
            me.amount = money(amountCell);
            me.currency = currencyOf(amountCell);
            out.add(me);
        }
        return out;
    }

    static double money(String s) {
        if (s == null) return 0;
        Matcher m = Pattern.compile("[0-9][0-9,\\.]*").matcher(s);
        if (!m.find()) return 0;
        try { return Double.parseDouble(m.group().replace(",", "")); } catch (Exception e) { return 0; }
    }

    static String currencyOf(String s) {
        Matcher m = Pattern.compile("\\b([A-Z]{3})\\b").matcher(s == null ? "" : s);
        return m.find() ? m.group(1) : "IDR";
    }

    public static void main(String[] a) throws Exception {
        com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(java.nio.file.Paths.get(a[0])));
        List<MonthEarning> list = parse(n.path("html").asText());
        System.out.println("bulan terbaca: " + list.size());
        double sum = 0;
        for (MonthEarning me : list) { System.out.println("  " + me.month + " = " + me.amount + " " + me.currency); sum += me.amount; }
        System.out.println("TOTAL = " + sum);
    }
}
