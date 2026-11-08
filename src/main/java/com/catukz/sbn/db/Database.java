package com.catukz.sbn.db;

import com.catukz.sbn.model.Account;
import com.catukz.sbn.model.Earning;
import com.catukz.sbn.model.Release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Database {
    private final Connection conn;

    public Database() {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".catukz-sbn");
            Files.createDirectories(dir);
            Path dbFile = dir.resolve("catukz.db");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
            init();
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuka database", e);
        }
    }

    private void init() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, distributor TEXT, label TEXT, email TEXT, enc_pw TEXT, last_sync TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS earnings (id INTEGER PRIMARY KEY AUTOINCREMENT, distributor TEXT, date TEXT, track TEXT, platform TEXT, country TEXT, streams INTEGER, amount REAL, currency TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS releases (id INTEGER PRIMARY KEY AUTOINCREMENT, distributor TEXT, title TEXT, artist TEXT, status TEXT, release_date TEXT, streams INTEGER, earnings REAL)");
            s.execute("CREATE TABLE IF NOT EXISTS mappings (distributor TEXT, app_col TEXT, file_col TEXT, PRIMARY KEY(distributor, app_col))");
        }
    }

    // ---------- meta ----------
    public String getMeta(String k) {
        try (PreparedStatement p = conn.prepareStatement("SELECT v FROM meta WHERE k=?")) {
            p.setString(1, k);
            ResultSet rs = p.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void setMeta(String k, String v) {
        try (PreparedStatement p = conn.prepareStatement(
                "INSERT INTO meta(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            p.setString(1, k);
            p.setString(2, v);
            p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // ---------- accounts ----------
    public void addAccount(String distributor, String label, String email, String encPw) {
        try (PreparedStatement p = conn.prepareStatement(
                "INSERT INTO accounts(distributor,label,email,enc_pw,last_sync) VALUES(?,?,?,?,?)")) {
            p.setString(1, distributor);
            p.setString(2, label);
            p.setString(3, email);
            p.setString(4, encPw);
            p.setString(5, null);
            p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deleteAccount(long id) {
        try (PreparedStatement p = conn.prepareStatement("DELETE FROM accounts WHERE id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Account> listAccounts() {
        List<Account> out = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id,distributor,label,email,last_sync FROM accounts ORDER BY distributor")) {
            while (rs.next()) {
                Account a = new Account();
                a.id = rs.getLong(1);
                a.distributor = rs.getString(2);
                a.label = rs.getString(3);
                a.email = rs.getString(4);
                a.lastSync = rs.getString(5);
                out.add(a);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public String getAccountEncPw(long id) {
        try (PreparedStatement p = conn.prepareStatement("SELECT enc_pw FROM accounts WHERE id=?")) {
            p.setLong(1, id);
            ResultSet rs = p.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // ---------- earnings ----------
    public void addEarning(Earning e) {
        try (PreparedStatement p = conn.prepareStatement(
                "INSERT INTO earnings(distributor,date,track,platform,country,streams,amount,currency) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setString(1, e.distributor);
            p.setString(2, e.date == null ? null : e.date.toString());
            p.setString(3, e.track);
            p.setString(4, e.platform);
            p.setString(5, e.country);
            p.setLong(6, e.streams);
            p.setDouble(7, e.amount);
            p.setString(8, e.currency);
            p.executeUpdate();
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public int countEarnings() {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM earnings")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public double totalAmount(String distributorOrNull) {
        return aggDouble("SELECT COALESCE(SUM(amount),0) FROM earnings", distributorOrNull);
    }

    public long totalStreams(String distributorOrNull) {
        return (long) aggDouble("SELECT COALESCE(SUM(streams),0) FROM earnings", distributorOrNull);
    }

    private double aggDouble(String base, String distributor) {
        String sql = base + (distributor == null ? "" : " WHERE distributor=?");
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            if (distributor != null) p.setString(1, distributor);
            ResultSet rs = p.executeQuery();
            rs.next();
            return rs.getDouble(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Map<String, Double> amountByDistributor() {
        Map<String, Double> m = new LinkedHashMap<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT distributor, SUM(amount) FROM earnings GROUP BY distributor ORDER BY SUM(amount) DESC")) {
            while (rs.next()) m.put(rs.getString(1), rs.getDouble(2));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return m;
    }

    public Map<String, Double> amountByMonth(String distributor) {
        Map<String, Double> m = new LinkedHashMap<>();
        String sql = "SELECT substr(date,1,7) ym, SUM(amount) FROM earnings"
                + (distributor == null ? "" : " WHERE distributor=?")
                + " GROUP BY ym ORDER BY ym";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            if (distributor != null) p.setString(1, distributor);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                String ym = rs.getString(1);
                if (ym != null) m.put(ym, rs.getDouble(2));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return m;
    }

    // ---------- releases ----------
    public void addRelease(Release r) {
        try (PreparedStatement p = conn.prepareStatement(
                "INSERT INTO releases(distributor,title,artist,status,release_date,streams,earnings) VALUES(?,?,?,?,?,?,?)")) {
            p.setString(1, r.distributor);
            p.setString(2, r.title);
            p.setString(3, r.artist);
            p.setString(4, r.status);
            p.setString(5, r.releaseDate == null ? null : r.releaseDate.toString());
            p.setLong(6, r.streams);
            p.setDouble(7, r.earnings);
            p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countReleases(String distributor) {
        String sql = "SELECT COUNT(*) FROM releases" + (distributor == null ? "" : " WHERE distributor=?");
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            if (distributor != null) p.setString(1, distributor);
            ResultSet rs = p.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Release> listReleases(String distributor) {
        List<Release> out = new ArrayList<>();
        String sql = "SELECT id,distributor,title,artist,status,release_date,streams,earnings FROM releases"
                + (distributor == null ? "" : " WHERE distributor=?")
                + " ORDER BY release_date DESC";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            if (distributor != null) p.setString(1, distributor);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                Release r = new Release();
                r.id = rs.getLong(1);
                r.distributor = rs.getString(2);
                r.title = rs.getString(3);
                r.artist = rs.getString(4);
                r.status = rs.getString(5);
                String d = rs.getString(6);
                r.releaseDate = d == null ? null : LocalDate.parse(d);
                r.streams = rs.getLong(7);
                r.earnings = rs.getDouble(8);
                out.add(r);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    // ---------- mappings ----------
    public void saveMapping(String distributor, Map<String, String> appToFile) {
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM mappings WHERE distributor=?")) {
                del.setString(1, distributor);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO mappings(distributor,app_col,file_col) VALUES(?,?,?)")) {
                for (Map.Entry<String, String> e : appToFile.entrySet()) {
                    ins.setString(1, distributor);
                    ins.setString(2, e.getKey());
                    ins.setString(3, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
        }
    }

    public Map<String, String> getMapping(String distributor) {
        Map<String, String> m = new HashMap<>();
        try (PreparedStatement p = conn.prepareStatement("SELECT app_col,file_col FROM mappings WHERE distributor=?")) {
            p.setString(1, distributor);
            ResultSet rs = p.executeQuery();
            while (rs.next()) m.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return m;
    }
}
