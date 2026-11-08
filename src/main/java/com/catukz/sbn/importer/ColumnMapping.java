package com.catukz.sbn.importer;

import java.util.List;

/** Nama kolom standar aplikasi yang dipetakan dari kolom file CSV. */
public final class ColumnMapping {
    public static final String DATE = "Tanggal";
    public static final String TRACK = "Judul lagu";
    public static final String PLATFORM = "Platform";
    public static final String COUNTRY = "Negara";
    public static final String STREAMS = "Stream";
    public static final String AMOUNT = "Pendapatan";
    public static final String CURRENCY = "Mata uang";

    public static final List<String> APP_COLUMNS = List.of(
            DATE, TRACK, PLATFORM, COUNTRY, STREAMS, AMOUNT, CURRENCY);

    private ColumnMapping() {}
}
