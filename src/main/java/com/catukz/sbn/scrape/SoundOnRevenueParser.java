package com.catukz.sbn.scrape;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mem-parsing halaman My Earnings (royalty) SoundOn: total pendapatan + saldo. */
public class SoundOnRevenueParser {

    public static class Result {
        public double yearlyEarnings;
        public String currency = "IDR";
        public String balanceText;   // teks saldo (bisa **** jika mata belum diklik)
        public double balance;
    }

    public static Result parse(String html) {
        Document doc = Jsoup.parse(html);
        Result r = new Result();

        for (Element h6 : doc.select("h6")) {
            if (h6.text().trim().startsWith("Your earnings in")) {
                Element sib = h6.nextElementSibling();
                Element val = null;
                if (sib != null) val = sib.tagName().equals("h3") ? sib : sib.selectFirst("h3, span");
                if (val == null && h6.parent() != null) val = h6.parent().selectFirst("h3");
                if (val != null) {
                    String t = val.text().trim();
                    r.yearlyEarnings = money(t);
                    r.currency = currencyOf(t);
                }
                break;
            }
        }

        Element eye = doc.selectFirst("[aria-label^=eye]");
        if (eye != null) {
            Element prev = eye.previousElementSibling();
            if (prev != null) {
                r.balanceText = prev.text().trim();
                r.balance = money(r.balanceText);
            }
        }
        return r;
    }

    static double money(String s) {
        if (s == null) return 0;
        Matcher m = Pattern.compile("[0-9][0-9,\\.]*").matcher(s);
        if (!m.find()) return 0;
        String num = m.group().replace(",", "");
        try { return Double.parseDouble(num); } catch (Exception e) { return 0; }
    }

    static String currencyOf(String s) {
        Matcher m = Pattern.compile("\\b([A-Z]{3})\\b").matcher(s == null ? "" : s);
        return m.find() ? m.group(1) : "IDR";
    }

    public static void main(String[] a) throws Exception {
        com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(java.nio.file.Paths.get(a[0])));
        Result r = parse(n.path("html").asText());
        System.out.println("yearlyEarnings=" + r.yearlyEarnings + " " + r.currency);
        System.out.println("balanceText=" + r.balanceText + " balance=" + r.balance);
    }
}
