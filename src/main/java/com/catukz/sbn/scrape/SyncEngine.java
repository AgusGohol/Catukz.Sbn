package com.catukz.sbn.scrape;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Account;
import com.catukz.sbn.security.Crypto;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Mesin login + sinkron analitik memakai Selenium (Chrome asli).
 * Profil Chrome khusus app disimpan agar sesi login bertahan.
 * Mencatat tiap langkah ke ~/.catukz-sbn/sync-log-<dist>.txt untuk diagnosa.
 */
public class SyncEngine {

    public interface Listener {
        void status(String msg);
        void done(boolean success, String message);
    }

    private final Database db;
    private final SecretKey key;
    private Path logFile;

    public SyncEngine(Database db, SecretKey key) {
        this.db = db;
        this.key = key;
    }

    /** Sinkron akun tertentu berdasarkan ID. */
    public void sync(long accountId, boolean headless, Listener l) {
        Thread t = new Thread(() -> {
            Account acc = findAccountById(accountId);
            if (acc == null) { l.done(false, "Akun tidak ditemukan."); return; }
            runWithFallback(acc, headless, l);
        }, "sync-acct-" + accountId);
        t.setDaemon(true);
        t.start();
    }

    /** Sinkron akun pertama dari sebuah distributor (dipakai auto-sync & tombol lama). */
    public void sync(String distributor, boolean headless, Listener l) {
        Thread t = new Thread(() -> {
            Account acc = findAccount(distributor);
            if (acc == null) { l.done(false, "Belum ada akun " + distributor + ". Tambah dulu di menu Akun."); return; }
            runWithFallback(acc, headless, l);
        }, "sync-" + distributor);
        t.setDaemon(true);
        t.start();
    }

    // Kode hasil runSync: DONE = selesai (l.done sudah dipanggil); LOGIN_REQUIRED = butuh login/verifikasi berjendela.
    private static final int DONE = 0;
    private static final int LOGIN_REQUIRED = 1;

    private int runSync(Account acc, boolean headless, Listener l) {
        final String distributor = acc.distributor;
        final long accountId = acc.id;
        Recipe r = Recipe.get(distributor);
        if (r == null) { l.done(false, "Resep sinkron untuk " + distributor + " belum tersedia."); return DONE; }
        final String email = acc.email;
        final String pass = decryptPw(acc);
        if (pass == null) { l.done(false, "Gagal membaca kredensial."); return DONE; }

        Path dir = Paths.get(System.getProperty("user.home"), ".catukz-sbn");
        logFile = dir.resolve("sync-log-" + distributor + ".txt");
        try { Files.writeString(logFile, ""); } catch (Exception ignore) {}
        log("=== MULAI sync " + distributor + " (headless=" + headless + ") ===");

        WebDriver driver = null;
        try {
            l.status("Menyiapkan Chrome…");
            // Profil terpisah PER AKUN: tiap akun punya memori/login Chrome sendiri,
            // jadi beberapa akun (termasuk sesama SoundOn) tidak saling bentrok.
            Path profile = dir.resolve("chrome").resolve(distributor + "-" + accountId);
            Files.createDirectories(profile);

            ChromeOptions opts = new ChromeOptions();
            opts.addArguments("--user-data-dir=" + profile.toAbsolutePath());
            opts.addArguments("--window-size=1280,900");
            opts.addArguments("--disable-blink-features=AutomationControlled");
            opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            opts.setExperimentalOption("useAutomationExtension", false);
            if (headless) opts.addArguments("--headless=new");

            driver = new ChromeDriver(opts);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            l.status("Membuka halaman analitik…");
            driver.get(r.analyticsUrl);
            sleep(3500);
            log("Setelah buka analitik, URL = " + driver.getCurrentUrl());

            if (driver.getCurrentUrl().contains("/login")) {
                l.status("Mengisi kredensial & login…");
                log("Di halaman login, mengisi kredensial");
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
                WebElement em = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type=email]")));
                WebElement pw = driver.findElement(By.cssSelector("input[type=password]"));
                em.clear(); em.sendKeys(email);
                pw.clear(); pw.sendKeys(pass);
                WebElement btn = driver.findElements(By.tagName("button")).stream()
                        .filter(b -> b.getText() != null && b.getText().trim().equalsIgnoreCase("Log in"))
                        .findFirst().orElse(null);
                log("Tombol Log in ditemukan: " + (btn != null));
                if (btn != null) btn.click();

                // Tunggu sampai keluar dari halaman login (ramah verifikasi manual)
                int loginWait = headless ? 25 : 180;
                l.status(headless ? "Menunggu login…" : "Selesaikan verifikasi (jika ada) di jendela Chrome…");
                try {
                    new WebDriverWait(driver, Duration.ofSeconds(loginWait))
                            .until(d -> !d.getCurrentUrl().contains("/login"));
                    log("Lolos halaman login, URL = " + driver.getCurrentUrl());
                } catch (Exception timeout) {
                    log("TIMEOUT menunggu lolos login (headless=" + headless + "). URL = " + driver.getCurrentUrl());
                    capture(js, dir, distributor, driver.getCurrentUrl());
                    // Jangan panggil l.done di sini: biarkan pemanggil memutuskan fallback ke jendela.
                    return LOGIN_REQUIRED;
                }
            }

            // Scraping analitik per region: Indonesia (ID) + Negara lain (US), rentang 365 hari
            final String mp = "acct." + accountId + ".";
            int rangeDays = 365;
            try { rangeDays = Integer.parseInt(db.getMeta("ui.rangeDays")); } catch (Exception ignore) {}
            if (rangeDays < 1) rangeDays = 1; if (rangeDays > 730) rangeDays = 730;
            db.setMeta(mp + "rangeDays", String.valueOf(rangeDays));
            log("Rentang analitik: " + rangeDays + " hari");
            String baseAnalytics = "https://www.soundon.global/library/analytics?endTime=now-2&startTime=now-"
                    + (rangeDays + 1) + "&lang=en&region=";
            String resultMsg;
            if ("SoundOn".equals(distributor)) {
                String idHtml = null;
                long totalAll = 0;
                for (String reg : new String[]{"ID", "US"}) {
                    l.status("Membaca region " + reg + "…");
                    driver.get(baseAnalytics + reg);
                    sleep(5500);
                    String h = String.valueOf(js.executeScript("return document.documentElement.outerHTML;"));
                    SoundOnParser.Result res = SoundOnParser.parse(h);
                    db.setMeta(mp + reg + ".streams", String.valueOf(res.totalStreams));
                    db.setMeta(mp + reg + ".listeners", String.valueOf(res.listeners));
                    totalAll += res.totalStreams;
                    log("Region " + reg + ": streams=" + res.totalStreams + " listeners=" + res.listeners + " tracks=" + res.tracks.size());
                    if ("ID".equals(reg)) idHtml = h;
                }
                capture(js, dir, distributor, "multi-region");
                storeSoundOn(idHtml, accountId); // simpan lagu/rilisan dari region ID untuk akun ini
                db.setMeta(mp + "totalStreams", String.valueOf(totalAll));
                resultMsg = "Sinkron berhasil — ID: " + String.format("%,d", metaL(mp + "ID.streams"))
                        + " streams, US: " + String.format("%,d", metaL(mp + "US.streams")) + " streams.";
            } else {
                driver.get(r.analyticsUrl);
                sleep(6000);
                capture(js, dir, distributor, driver.getCurrentUrl());
                resultMsg = "Snapshot tersimpan. Ekstraksi untuk " + distributor + " belum dibuat.";
            }

            // Halaman earnings/royalty: klik ikon mata untuk reveal saldo, lalu parse
            if (r.earningsUrl != null) {
                try {
                    l.status("Membuka halaman earnings…");
                    driver.get(r.earningsUrl);
                    sleep(5000);
                    try {
                        java.util.List<WebElement> eyes = driver.findElements(By.cssSelector("[aria-label^=eye]"));
                        if (!eyes.isEmpty()) {
                            eyes.get(0).click();
                            sleep(1200);
                            log("Klik ikon mata untuk reveal saldo");
                        }
                    } catch (Exception ex) {
                        log("Tidak bisa klik ikon mata: " + ex);
                    }
                    String revHtml = String.valueOf(js.executeScript("return document.documentElement.outerHTML;"));
                    log("Earnings URL = " + driver.getCurrentUrl());
                    capture(js, dir, distributor + "-earnings", driver.getCurrentUrl());

                    if ("SoundOn".equals(distributor)) {
                        SoundOnRevenueParser.Result rev = SoundOnRevenueParser.parse(revHtml);
                        db.setMeta(mp + "earnings", String.valueOf(rev.yearlyEarnings));
                        db.setMeta(mp + "earningsCurrency", rev.currency);
                        db.setMeta(mp + "balanceText", rev.balanceText == null ? "-" : rev.balanceText);
                        if (rev.balance > 0) db.setMeta(mp + "balance", String.valueOf(rev.balance));
                        log("Revenue: earnings=" + rev.yearlyEarnings + " " + rev.currency + " balance=" + rev.balanceText);
                        if (rev.yearlyEarnings > 0) {
                            resultMsg = resultMsg + " Pendapatan "
                                    + String.format("%,.0f", rev.yearlyEarnings) + " " + rev.currency + ".";
                        }

                        // (1) Coba baca data bulanan dari grafik ECharts
                        try {
                            Object ec = js.executeScript(
                                    "try{if(typeof echarts==='undefined')return 'NO_ECHARTS';var out=[];"
                                  + "document.querySelectorAll('div').forEach(function(d){var ins=echarts.getInstanceByDom(d);"
                                  + "if(ins){try{var op=ins.getOption();out.push({x:(op.xAxis&&op.xAxis[0]&&op.xAxis[0].data)||null,"
                                  + "series:(op.series||[]).map(function(s){return {name:s.name,type:s.type,data:s.data};})});}catch(e){}}});"
                                  + "return JSON.stringify(out);}catch(e){return 'ERR:'+e;}");
                            String ecStr = String.valueOf(ec);
                            Files.writeString(dir.resolve("sync-debug-SoundOn-echarts.json"), ecStr);
                            log("ECHARTS len=" + ecStr.length());
                        } catch (Exception ex) {
                            log("echarts dump err: " + ex);
                        }

                        // (2) Klik tab 'Monthly statements', tangkap & parse pendapatan per bulan
                        try {
                            for (WebElement tab : driver.findElements(By.cssSelector("[role=tab]"))) {
                                String tt = tab.getText();
                                if (tt != null && tt.toLowerCase().contains("monthly")) { tab.click(); sleep(2800); break; }
                            }
                            String monthlyHtml = String.valueOf(js.executeScript("return document.documentElement.outerHTML;"));
                            capture(js, dir, "SoundOn-monthly", driver.getCurrentUrl());
                            java.util.List<SoundOnMonthlyParser.MonthEarning> months = SoundOnMonthlyParser.parse(monthlyHtml);
                            if (!months.isEmpty()) {
                                for (SoundOnMonthlyParser.MonthEarning me : months) {
                                    com.catukz.sbn.model.Earning e = new com.catukz.sbn.model.Earning();
                                    e.distributor = "SoundOn";
                                    e.accountId = accountId;
                                    e.track = "Royalty " + me.month;
                                    e.platform = "SoundOn";
                                    e.country = "";
                                    e.streams = 0;
                                    e.amount = me.amount;
                                    e.currency = me.currency;
                                    try { e.date = java.time.LocalDate.parse(me.month + "-01"); }
                                    catch (Exception ex) { e.date = java.time.LocalDate.now(); }
                                    db.addEarning(e);
                                }
                                db.setMeta(mp + "monthlyCount", String.valueOf(months.size()));
                                log("Pendapatan bulanan tersimpan: " + months.size() + " bulan");
                            }
                        } catch (Exception ex) {
                            log("Gagal buka Monthly statements: " + ex);
                        }
                    }
                } catch (Exception ee) {
                    log("Gagal proses earnings: " + ee);
                }
            }

            db.setMeta(mp + "lastSync", LocalDateTime.now().toString());
            db.setMeta("sync." + distributor + ".lastSync", LocalDateTime.now().toString()); // legacy
            log("=== SELESAI: " + resultMsg + " ===");
            l.done(true, resultMsg);
            return DONE;
        } catch (Exception e) {
            log("ERROR: " + e);
            l.done(false, "Gagal sync: " + shortMsg(e));
            return DONE;
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Jalankan sinkron headless (tanpa jendela). Jika SoundOn butuh login/verifikasi yang
     * tak bisa diselesaikan tanpa jendela, buka Chrome berjendela SEKALI untuk login,
     * lalu sesi tersimpan di profil akun sehingga sinkron berikutnya kembali senyap.
     */
    private void runWithFallback(Account acc, boolean preferHeadless, Listener l) {
        int code = runSync(acc, preferHeadless, l);
        if (code != LOGIN_REQUIRED) return;
        if (!preferHeadless) {
            // Sudah berjendela tapi tetap belum lolos — serahkan ke pengguna.
            l.done(false, "Login belum selesai (mungkin butuh verifikasi). Selesaikan di jendela Chrome lalu coba lagi.");
            return;
        }
        l.status("Butuh verifikasi — membuka jendela Chrome sekali untuk login…");
        int code2 = runSync(acc, false, l);
        if (code2 == LOGIN_REQUIRED) {
            l.done(false, "Login belum selesai. Selesaikan verifikasi di jendela Chrome, lalu sinkron lagi.");
        }
    }

    private String storeSoundOn(String html, long accountId) {
        try {
            SoundOnParser.Result res = SoundOnParser.parse(html);
            log("Parse: totalStreams=" + res.totalStreams + " listeners=" + res.listeners + " tracks=" + res.tracks.size());
            if (res.tracks.isEmpty() && res.totalStreams == 0) {
                return "Berhasil login, tapi data analitik belum terbaca (struktur halaman mungkin berubah).";
            }
            db.deleteEarningsForAccount(accountId);
            db.deleteReleasesForAccount(accountId);
            db.deleteLegacyData("SoundOn"); // bersihkan data lama tanpa account_id
            java.time.LocalDate today = java.time.LocalDate.now();
            for (SoundOnParser.Track t : res.tracks) {
                com.catukz.sbn.model.Release rel = new com.catukz.sbn.model.Release();
                rel.distributor = "SoundOn";
                rel.accountId = accountId;
                rel.title = t.title;
                rel.artist = t.artist.isEmpty() ? "-" : t.artist;
                rel.status = "Live";
                rel.streams = t.streams;
                rel.earnings = 0;
                rel.releaseDate = today;
                db.addRelease(rel);

                com.catukz.sbn.model.Earning e = new com.catukz.sbn.model.Earning();
                e.distributor = "SoundOn";
                e.accountId = accountId;
                e.track = t.title;
                e.platform = "SoundOn";
                e.country = "";
                e.streams = t.streams;
                e.amount = 0;
                e.currency = "USD";
                e.date = today;
                db.addEarning(e);
            }
            db.setMeta("acct." + accountId + ".listeners", String.valueOf(res.listeners));
            return "Sinkron berhasil: " + res.tracks.size() + " lagu, "
                    + String.format("%,d", res.totalStreams) + " streams, "
                    + String.format("%,d", res.listeners) + " listeners.";
        } catch (Exception e) {
            log("storeSoundOn error: " + e);
            return "Login berhasil tapi gagal mengurai data: " + shortMsg(e);
        }
    }

    private void capture(JavascriptExecutor js, Path dir, String distributor, String url) {
        try {
            Object snap = js.executeScript(
                    "try{return JSON.stringify({url:location.href,title:document.title,html:document.documentElement.outerHTML.slice(0,400000),text:(document.body?document.body.innerText:'').slice(0,30000)});}catch(e){return JSON.stringify({error:String(e)});}");
            String snapshot = snap == null ? "" : snap.toString();
            Files.writeString(dir.resolve("sync-debug-" + distributor + ".json"), snapshot);
            log("Snapshot ditangkap (" + snapshot.length() + " chars) dari " + url);
        } catch (Exception e) {
            log("Gagal capture: " + e);
        }
    }

    private void log(String msg) {
        try {
            Files.writeString(logFile, LocalDateTime.now() + "  " + msg + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignore) {}
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignore) {}
    }

    private long metaL(String k) {
        try { return Long.parseLong(db.getMeta(k)); } catch (Exception e) { return 0; }
    }

    private Account findAccount(String distributor) {
        List<Account> list = db.listAccounts();
        for (Account a : list) if (distributor.equals(a.distributor)) return a;
        return null;
    }

    private Account findAccountById(long id) {
        for (Account a : db.listAccounts()) if (a.id == id) return a;
        return null;
    }

    private String decryptPw(Account a) {
        try {
            String encB64 = db.getAccountEncPw(a.id);
            return new String(Crypto.decrypt(key, Base64.getDecoder().decode(encB64)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
