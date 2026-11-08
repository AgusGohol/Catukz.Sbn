package com.catukz.sbn.importer;

import com.catukz.sbn.model.Earning;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvImporter {

    public static List<String> readHeader(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = r.readLine();
            if (line == null) return List.of();
            return parseLine(stripBom(line));
        }
    }

    public static List<Map<String, String>> readRows(Path file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = r.readLine();
            if (headerLine == null) return rows;
            List<String> headers = parseLine(stripBom(headerLine));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> vals = parseLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < vals.size() ? vals.get(i) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public static Earning toEarning(Map<String, String> row, Map<String, String> mapping, String distributor) {
        Earning e = new Earning();
        e.distributor = distributor;
        e.date = parseDate(get(row, mapping, ColumnMapping.DATE));
        e.track = get(row, mapping, ColumnMapping.TRACK);
        e.platform = get(row, mapping, ColumnMapping.PLATFORM);
        e.country = get(row, mapping, ColumnMapping.COUNTRY);
        e.streams = parseLong(get(row, mapping, ColumnMapping.STREAMS));
        e.amount = parseDouble(get(row, mapping, ColumnMapping.AMOUNT));
        String cur = get(row, mapping, ColumnMapping.CURRENCY);
        e.currency = (cur == null || cur.isBlank()) ? "USD" : cur;
        return e;
    }

    private static String get(Map<String, String> row, Map<String, String> mapping, String appCol) {
        String fileCol = mapping.get(appCol);
        if (fileCol == null) return null;
        return row.get(fileCol);
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '﻿') return s.substring(1);
        return s;
    }

    /** Parser CSV sederhana yang mendukung field berkutip dan koma di dalam kutip. */
    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else inQ = false;
                } else sb.append(c);
            } else {
                if (c == '"') inQ = true;
                else if (c == ',') { out.add(sb.toString().trim()); sb.setLength(0); }
                else sb.append(c);
            }
        }
        out.add(sb.toString().trim());
        return out;
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        // format "yyyy-MM" (tanpa hari) → tambah -01
        if (s.matches("\\d{4}-\\d{2}")) {
            try { return LocalDate.parse(s + "-01"); } catch (Exception ignore) {}
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f); } catch (Exception ignore) {}
        }
        return null;
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        s = s.replaceAll("[^0-9-]", "");
        if (s.isBlank() || s.equals("-")) return 0;
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    private static double parseDouble(String s) {
        if (s == null) return 0;
        s = s.replaceAll("[^0-9.,-]", "");
        if (s.isBlank()) return 0;
        if (s.contains(".") && s.contains(",")) {
            s = s.replace(",", ""); // koma = pemisah ribuan
        } else if (s.contains(",")) {
            s = s.replace(",", "."); // koma = pemisah desimal
        }
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
