package com.catukz.sbn.util;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Earning;
import com.catukz.sbn.model.Release;

import java.time.LocalDate;
import java.util.Random;

/** Mengisi data contoh saat database masih kosong, agar aplikasi langsung bisa dilihat. */
public class SampleData {

    public static void seedIfEmpty(Database db) {
        if (db.countEarnings() > 0) return;

        String[] dists = {"DistroKid", "Amuse", "SoundOn", "OffStep"};
        String[] platforms = {"Spotify", "Apple Music", "YouTube", "TikTok", "Deezer"};
        String[] tracks = {"Senja Kota", "Lampu Merah", "Rindu Berat", "Satu Kali", "Nanti Saja"};
        String[] countries = {"ID", "US", "MY", "JP", "DE"};
        Random rnd = new Random(42);

        LocalDate start = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        for (int m = 0; m < 6; m++) {
            LocalDate month = start.plusMonths(m);
            for (String dist : dists) {
                for (String track : tracks) {
                    Earning e = new Earning();
                    e.distributor = dist;
                    e.date = month;
                    e.track = track;
                    e.platform = platforms[rnd.nextInt(platforms.length)];
                    e.country = countries[rnd.nextInt(countries.length)];
                    e.streams = 500 + rnd.nextInt(8000);
                    e.amount = Math.round(e.streams * (0.002 + rnd.nextDouble() * 0.004) * 100.0) / 100.0;
                    e.currency = "USD";
                    db.addEarning(e);
                }
            }
        }

        String[] statuses = {"Live", "Live", "Live", "Pending", "Rejected"};
        for (String dist : dists) {
            for (int i = 0; i < tracks.length; i++) {
                Release r = new Release();
                r.distributor = dist;
                r.title = tracks[i];
                r.artist = "Catukz";
                r.status = statuses[i % statuses.length];
                r.releaseDate = start.plusMonths(rnd.nextInt(6));
                r.streams = 1000 + rnd.nextInt(40000);
                r.earnings = Math.round(r.streams * 0.003 * 100.0) / 100.0;
                db.addRelease(r);
            }
        }
    }
}
