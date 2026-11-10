package com.catukz.sbn.scrape;

import java.util.HashMap;
import java.util.Map;

/** URL login & halaman data per distributor. */
public class Recipe {
    public final String distributor;
    public final String loginUrl;
    public final String analyticsUrl;
    public final String earningsUrl; // boleh null kalau belum ada

    public Recipe(String distributor, String loginUrl, String analyticsUrl, String earningsUrl) {
        this.distributor = distributor;
        this.loginUrl = loginUrl;
        this.analyticsUrl = analyticsUrl;
        this.earningsUrl = earningsUrl;
    }

    private static final Map<String, Recipe> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put("SoundOn", new Recipe(
                "SoundOn",
                "https://www.soundon.global/login?next=%2Flibrary%2Fanalytics%3FendTime%3Dnow-2%26startTime%3Dnow-366&lang=en&region=ID",
                "https://www.soundon.global/library/analytics?endTime=now-2&startTime=now-30&lang=en",
                "https://www.soundon.global/library/revenue?lang=en&region=ID"));
    }

    public static Recipe get(String distributor) {
        return REGISTRY.get(distributor);
    }

    public static boolean isSupported(String distributor) {
        return REGISTRY.containsKey(distributor);
    }
}
