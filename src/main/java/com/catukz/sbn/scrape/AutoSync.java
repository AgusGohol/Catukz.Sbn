package com.catukz.sbn.scrape;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Account;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Penjadwal sinkronisasi otomatis di latar belakang (Chrome headless).
 * Mengecek berkala apakah ada distributor yang jatuh tempo, lalu sync.
 */
public class AutoSync {
    private final Database db;
    private final SecretKey key;
    private ScheduledExecutorService exec;
    private volatile boolean busy;

    public AutoSync(Database db, SecretKey key) {
        this.db = db;
        this.key = key;
    }

    public boolean isEnabled() {
        return "true".equals(db.getMeta("autosync.enabled"));
    }

    public int intervalHours() {
        try { return Integer.parseInt(db.getMeta("autosync.intervalHours")); }
        catch (Exception e) { return 24; }
    }

    public void setEnabled(boolean enabled, int hours) {
        db.setMeta("autosync.enabled", String.valueOf(enabled));
        db.setMeta("autosync.intervalHours", String.valueOf(hours));
        stop();
        if (enabled) start();
    }

    public synchronized void start() {
        if (exec != null || !isEnabled()) return;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autosync");
            t.setDaemon(true);
            return t;
        });
        // Cek tiap 30 menit; sync hanya yang sudah jatuh tempo.
        exec.scheduleWithFixedDelay(this::tick, 2, 30, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (exec != null) { exec.shutdownNow(); exec = null; }
    }

    private void tick() {
        if (busy || !isEnabled()) return;
        int hours = intervalHours();
        for (Account a : db.listAccounts()) {
            if (!Recipe.isSupported(a.distributor) || !due(a.distributor, hours)) continue;
            busy = true;
            try {
                CountDownLatch latch = new CountDownLatch(1);
                new SyncEngine(db, key).sync(a.distributor, true, new SyncEngine.Listener() {
                    @Override public void status(String msg) { }
                    @Override public void done(boolean success, String message) {
                        db.setMeta("autosync.last." + a.distributor, LocalDateTime.now().toString());
                        db.setMeta("autosync.lastResult." + a.distributor, success ? "OK" : message);
                        latch.countDown();
                    }
                });
                latch.await(5, TimeUnit.MINUTES);
            } catch (Exception ignore) {
            } finally {
                busy = false;
            }
        }
    }

    private boolean due(String dist, int hours) {
        String last = db.getMeta("sync." + dist + ".lastSync");
        if (last == null) return true;
        try {
            return Duration.between(LocalDateTime.parse(last), LocalDateTime.now()).toHours() >= hours;
        } catch (Exception e) {
            return true;
        }
    }
}
