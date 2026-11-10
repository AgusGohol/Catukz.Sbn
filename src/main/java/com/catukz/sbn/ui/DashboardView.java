package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Account;
import com.catukz.sbn.model.Release;
import com.catukz.sbn.scrape.Recipe;
import com.catukz.sbn.scrape.SyncEngine;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardView {
    private final VBox root = new VBox(16);
    private final Database db;
    private final SecretKey key;
    private final String distributor;
    private final Runnable onSynced;
    private final java.util.function.Consumer<String> onSelectDistributor;

    // null = agregat semua akun distributor; selain itu = ID akun yang dipilih.
    private Long selectedAccountId = null;

    public DashboardView(Database db, String distributor, SecretKey key, Runnable onSynced,
                         java.util.function.Consumer<String> onSelectDistributor) {
        this.db = db;
        this.key = key;
        this.distributor = distributor;
        this.onSynced = onSynced;
        this.onSelectDistributor = onSelectDistributor;
        root.setPadding(new Insets(4));
        render();
    }

    private void render() {
        root.getChildren().clear();
        Long sel = selectedAccountId;
        Account selAcc = sel == null ? null : acctById(sel);

        Label header = new Label(distributor == null ? "Semua distributor" : distributor);
        header.getStyleClass().add("h1");
        String rangeInfo = "";
        if (selAcc != null) {
            String rd = db.getMeta("acct." + selAcc.id + ".rangeDays");
            if (rd != null) rangeInfo = " · data rentang " + rd + " hari";
        }
        Label subtitle = new Label(selAcc == null
                ? "Ringkasan pendapatan, stream, dan rilisan Anda"
                : "Akun: " + selAcc.email + rangeInfo);
        subtitle.getStyleClass().add("subtitle");
        VBox titleBox = new VBox(2, header, subtitle);

        Region hsp = new Region();
        HBox.setHgrow(hsp, Priority.ALWAYS);
        HBox headerRow = new HBox(10, titleBox, hsp);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Tombol "Semua akun" untuk mengosongkan filter akun.
        if (selAcc != null) {
            Button clear = new Button("↩  Semua akun");
            clear.getStyleClass().add("nav-btn");
            clear.setOnAction(e -> { selectedAccountId = null; render(); });
            headerRow.getChildren().add(clear);
        }

        Label syncStatus = new Label();
        syncStatus.getStyleClass().add("muted");
        if (distributor != null) {
            Button sync = new Button("⟳  Sinkronkan");
            sync.getStyleClass().add("primary");
            String last = aLastSync(sel);
            if (last != null && !last.equals("—")) syncStatus.setText("Sync terakhir: " + last);
            sync.setOnAction(e -> runSyncSelected(sel, sync, syncStatus));
            headerRow.getChildren().add(buildRangeSelector(sel, sync, syncStatus));
            VBox syncBox = new VBox(4, sync, syncStatus);
            syncBox.setAlignment(Pos.CENTER_RIGHT);
            String balance = aBalance(sel);
            if (!balance.equals("—")) {
                Label saldo = new Label("Saldo bisa ditarik: " + balance);
                saldo.getStyleClass().add("subtitle");
                syncBox.getChildren().add(1, saldo);
            }
            headerRow.getChildren().add(syncBox);
        }

        long streams = aStreams(sel);
        int releases = aReleaseCount(sel);
        double earnVal = aEarnings(sel);
        String pendapatanText = earnVal > 0
                ? String.format("%,.0f", earnVal) + " " + aEarningsCurrency(sel)
                : String.format("$%,.2f", 0.0);

        HBox cards = new HBox(14);
        cards.getChildren().addAll(
                statCard("PENDAPATAN", pendapatanText, "◉", "icon-blue"),
                statCard("STREAM", String.format("%,d", streams), "♫", "icon-green"),
                statCard("RILISAN", String.valueOf(releases), "▤", "icon-purple"),
                statCard(selAcc == null ? "DISTRIBUTOR" : "AKUN",
                        selAcc == null ? (distributor == null ? "Semua" : distributor) : ("#" + indexOfAccount(selAcc)),
                        "◈", "icon-amber"));

        // Grafik stream akun (bisa diubah Batang/Garis)
        VBox streamCard = buildStreamChartCard(sel);

        // Grafik pendapatan per bulan (dipindah ke kanan; tetap ada agar data royalti tidak hilang)
        VBox earnCard = buildEarningsChartCard(sel);

        HBox charts = new HBox(14, streamCard, earnCard);

        VBox akunTable = buildAkunTable();

        HBox artisLagu = new HBox(14,
                buildPanel("Artis", artisItems(sel)),
                buildPanel("Lagu", laguItems(sel)));

        root.getChildren().addAll(headerRow, cards);
        if (hasRegion(sel)) root.getChildren().add(buildRegionCards(sel));
        root.getChildren().addAll(charts, akunTable, artisLagu);
    }

    // ---------- akses data (hormati akun terpilih) ----------
    private List<Account> scopeAccounts(Long id) {
        if (id != null) { Account a = acctById(id); List<Account> l = new ArrayList<>(); if (a != null) l.add(a); return l; }
        return db.listAccounts(distributor);
    }

    private long aStreams(Long id) {
        long s = 0;
        for (Account a : scopeAccounts(id)) s += metaLong("acct." + a.id + ".totalStreams");
        return s;
    }
    private long aRegionStreams(Long id, String reg) {
        long s = 0;
        for (Account a : scopeAccounts(id)) s += metaLong("acct." + a.id + "." + reg + ".streams");
        return s;
    }
    private long aRegionListeners(Long id, String reg) {
        long s = 0;
        for (Account a : scopeAccounts(id)) s += metaLong("acct." + a.id + "." + reg + ".listeners");
        return s;
    }
    private double aEarnings(Long id) {
        double s = 0;
        for (Account a : scopeAccounts(id)) s += metaDouble("acct." + a.id + ".earnings");
        return s;
    }
    private String aEarningsCurrency(Long id) {
        for (Account a : scopeAccounts(id)) {
            String c = db.getMeta("acct." + a.id + ".earningsCurrency");
            if (c != null && !c.isBlank()) return c;
        }
        return "IDR";
    }
    private String aBalance(Long id) {
        List<Account> accs = scopeAccounts(id);
        if (accs.size() == 1) return cleanBalance(db.getMeta("acct." + accs.get(0).id + ".balanceText"));
        return "—"; // beberapa akun: tidak dijumlah (mata uang bisa berbeda)
    }
    private String aLastSync(Long id) {
        String best = null;
        for (Account a : scopeAccounts(id)) {
            String v = db.getMeta("acct." + a.id + ".lastSync");
            if (v != null && (best == null || v.compareTo(best) > 0)) best = v;
        }
        return fmtTime(best);
    }
    private int aReleaseCount(Long id) {
        if (id != null) return db.countReleasesForAccount(id);
        return db.countReleases(distributor);
    }
    private List<Release> aReleases(Long id) {
        if (id != null) return db.listReleasesForAccount(id);
        return db.listReleases(distributor);
    }
    private List<Database.ArtistStat> aArtists(Long id) {
        if (id != null) return db.listArtistsForAccount(id);
        return db.listArtists(distributor);
    }
    private Map<String, Double> aMonthly(Long id) {
        if (id != null) return db.amountByMonthForAccount(id);
        return db.amountByMonth(distributor);
    }
    private boolean hasRegion(Long id) {
        for (Account a : scopeAccounts(id)) if (db.getMeta("acct." + a.id + ".ID.streams") != null) return true;
        return false;
    }

    private Account acctById(long id) {
        for (Account a : db.listAccounts()) if (a.id == id) return a;
        return null;
    }
    private int indexOfAccount(Account acc) {
        List<Account> accs = db.listAccounts(distributor);
        for (int i = 0; i < accs.size(); i++) if (accs.get(i).id == acc.id) return i + 1;
        return 1;
    }

    // ---------- Kartu per region ----------
    private HBox buildRegionCards(Long sel) {
        HBox box = new HBox(14);
        box.getChildren().addAll(
                regionCard(sel, "INDONESIA", "Indonesia", "ID"),
                regionCard(sel, "NEGARA LAIN", "Amerika Serikat", "US"));
        return box;
    }

    private VBox regionCard(Long sel, String title, String regionName, String regionCode) {
        long streams = aRegionStreams(sel, regionCode);
        long listeners = aRegionListeners(sel, regionCode);
        String saldo = aBalance(sel);
        int akun = scopeAccounts(sel).size();

        Label t = new Label(title);
        t.getStyleClass().add("h2");
        Label rn = new Label(regionName);
        rn.getStyleClass().add("region-badge");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(t, sp, rn);
        head.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12);
        g.setVgap(12);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setPercentWidth(50);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setPercentWidth(50);
        g.getColumnConstraints().addAll(c1, c2);
        g.add(miniStat("Streams", String.format("%,d", streams), "val-purple"), 0, 0);
        g.add(miniStat("Pendengar", String.format("%,d", listeners), "val-green"), 1, 0);
        g.add(miniStat("Saldo Tersedia", saldo, "val-blue"), 0, 1);
        g.add(miniStat("Jumlah Akun", String.valueOf(akun), "val-pink"), 1, 1);

        VBox card = new VBox(14, head, g);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(16));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox miniStat(String label, String value, String valueClass) {
        Label l = new Label(label);
        l.getStyleClass().add("subtitle");
        Label v = new Label(value);
        v.getStyleClass().addAll("mini-stat-value", valueClass);
        VBox box = new VBox(4, l, v);
        box.getStyleClass().add("mini-stat");
        box.setPadding(new Insets(12, 14, 12, 14));
        javafx.scene.layout.GridPane.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ---------- Pemilih rentang waktu (seperti "Last N days") ----------
    private HBox buildRangeSelector(Long sel, Button sync, Label note) {
        Label l = new Label("Rentang:");
        l.getStyleClass().add("subtitle");
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll("7 hari", "14 hari", "28 hari", "90 hari", "365 hari", "Custom…");
        cb.setValue(rangeLabel(currentRangeDays()));
        cb.getStyleClass().add("range-combo");
        cb.setOnAction(e -> {
            String v = cb.getValue();
            if (v == null) return;
            int days;
            if (v.startsWith("Custom")) {
                TextInputDialog dlg = new TextInputDialog(String.valueOf(currentRangeDays()));
                dlg.setTitle("Rentang kustom");
                dlg.setHeaderText("Masukkan jumlah hari (1–730):");
                dlg.getDialogPane().getStylesheets().addAll(root.getStylesheets());
                java.util.Optional<String> res = dlg.showAndWait();
                if (res.isEmpty()) { cb.setValue(rangeLabel(currentRangeDays())); return; }
                try { days = Math.max(1, Math.min(730, Integer.parseInt(res.get().trim()))); }
                catch (Exception ex) { cb.setValue(rangeLabel(currentRangeDays())); return; }
            } else {
                days = Integer.parseInt(v.replaceAll("[^0-9]", ""));
            }
            // Jangan lakukan apa-apa kalau nilainya sama dengan yang sekarang.
            if (days == currentRangeDays()) { cb.setValue(rangeLabel(days)); return; }
            db.setMeta("ui.rangeDays", String.valueOf(days));
            cb.setValue(rangeLabel(days));
            note.setText("Rentang " + days + " hari — menyinkronkan otomatis…");
            // Auto-sinkron: begitu rentang diganti, langsung tarik data periode baru.
            runSyncSelected(sel, sync, note);
        });
        HBox box = new HBox(8, l, cb);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private int currentRangeDays() {
        try { return Integer.parseInt(db.getMeta("ui.rangeDays")); } catch (Exception e) { return 365; }
    }

    private String rangeLabel(int d) {
        switch (d) {
            case 7: return "7 hari";
            case 14: return "14 hari";
            case 28: return "28 hari";
            case 90: return "90 hari";
            case 365: return "365 hari";
            default: return d + " hari";
        }
    }

    // ---------- Grafik Stream akun (Batang/Garis) ----------
    private boolean isLineChart() { return "line".equals(db.getMeta("ui.chartType")); }

    /** Kartu grafik "Stream akun": streams per lagu untuk akun terpilih, bisa ganti Batang/Garis. */
    private VBox buildStreamChartCard(Long sel) {
        boolean line = isLineChart();

        Label title = new Label("Stream akun");
        title.getStyleClass().add("h2");

        // Toggle Batang | Garis
        ToggleButton barBtn = new ToggleButton("Batang");
        ToggleButton lineBtn = new ToggleButton("Garis");
        ToggleGroup tg = new ToggleGroup();
        barBtn.setToggleGroup(tg);
        lineBtn.setToggleGroup(tg);
        barBtn.getStyleClass().add("seg-btn");
        lineBtn.getStyleClass().add("seg-btn");
        (line ? lineBtn : barBtn).setSelected(true);
        barBtn.setOnAction(e -> { if (isLineChart()) { db.setMeta("ui.chartType", "bar"); render(); } else barBtn.setSelected(true); });
        lineBtn.setOnAction(e -> { if (!isLineChart()) { db.setMeta("ui.chartType", "line"); render(); } else lineBtn.setSelected(true); });
        HBox seg = new HBox(barBtn, lineBtn);
        seg.getStyleClass().add("seg");
        seg.setAlignment(Pos.CENTER_RIGHT);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(10, title, sp, seg);
        head.setAlignment(Pos.CENTER_LEFT);

        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        XYChart<String, Number> chart = line ? new LineChart<>(x, y) : new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(250);
        chart.getStyleClass().add("stream-chart");
        if (chart instanceof BarChart<?, ?> bc) bc.setCategoryGap(14);
        if (chart instanceof LineChart<?, ?> lc) lc.setCreateSymbols(true);

        List<Release> rels = aReleases(sel);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (Release r : rels) s.getData().add(new XYChart.Data<>(shorten(r.title), r.streams));
        chart.getData().add(s);
        for (int i = 0; i < s.getData().size(); i++) {
            Release r = rels.get(i);
            decoratePoint(s.getData().get(i), line, "#34D399", r.title + "\nStream: " + String.format("%,d", r.streams));
        }

        VBox card = new VBox(10, head, chart);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(16));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Kartu grafik "Pendapatan per bulan" (royalti bulanan). */
    private VBox buildEarningsChartCard(Long sel) {
        Label title = new Label("Pendapatan per bulan");
        title.getStyleClass().add("h2");
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(250);
        chart.setBarGap(2);
        chart.setCategoryGap(18);
        String earnCur = aEarningsCurrency(sel);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> e : aMonthly(sel).entrySet()) {
            XYChart.Data<String, Number> d = new XYChart.Data<>(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0);
            decorateBar(d, "#EC4899", e.getKey() + "\nPendapatan: " + String.format("%,.0f", e.getValue()) + " " + earnCur);
            series.getData().add(d);
        }
        chart.getData().add(series);
        VBox card = new VBox(10, title, chart);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(16));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Hiasi satu titik/batang: warna + tooltip. Mode garis mewarnai simbol, mode batang mewarnai batang. */
    private void decoratePoint(XYChart.Data<String, Number> d, boolean line, String color, String tip) {
        Runnable apply = () -> {
            Node n = d.getNode();
            if (n == null) return;
            if (line) {
                n.setStyle("-fx-background-color: " + color + ", white; -fx-background-insets: 0, 2; "
                        + "-fx-background-radius: 6; -fx-padding: 5;");
            } else {
                n.setStyle("-fx-bar-fill: " + color + "; -fx-background-color: " + color + "; -fx-background-radius: 5 5 0 0;");
            }
            javafx.scene.control.Tooltip t = new javafx.scene.control.Tooltip(tip);
            t.setShowDelay(javafx.util.Duration.millis(80));
            t.setHideDelay(javafx.util.Duration.ZERO);
            t.getStyleClass().add("chart-tip");
            javafx.scene.control.Tooltip.install(n, t);
        };
        if (d.getNode() != null) apply.run();
        d.nodeProperty().addListener((o, ov, nv) -> { if (nv != null) apply.run(); });
    }

    /** Warnai batang + pasang tooltip langsung di node-nya (tidak bergantung parsing CSS). */
    private void decorateBar(XYChart.Data<String, Number> d, String color, String tip) {
        String style = "-fx-bar-fill: " + color + "; -fx-background-color: " + color + "; -fx-background-radius: 5 5 0 0;";
        Runnable apply = () -> {
            Node n = d.getNode();
            if (n == null) return;
            n.setStyle(style);
            javafx.scene.control.Tooltip t = new javafx.scene.control.Tooltip(tip);
            t.setShowDelay(javafx.util.Duration.millis(80));
            t.setHideDelay(javafx.util.Duration.ZERO);
            t.getStyleClass().add("chart-tip");
            javafx.scene.control.Tooltip.install(n, t);
        };
        if (d.getNode() != null) apply.run();
        d.nodeProperty().addListener((o, ov, nv) -> { if (nv != null) apply.run(); });
    }

    private long metaLong(String k) {
        try { return Long.parseLong(db.getMeta(k)); } catch (Exception e) { return 0; }
    }
    private double metaDouble(String k) {
        try { return Double.parseDouble(db.getMeta(k)); } catch (Exception e) { return 0; }
    }

    // ---------- Tabel Akun (kaya) ----------
    private VBox buildAkunTable() {
        Label title = new Label("Akun");
        title.getStyleClass().add("h2");

        HBox header = new HBox(10);
        header.getStyleClass().add("akun-head");
        header.setPadding(new Insets(6, 8, 6, 8));
        header.getChildren().addAll(
                headCell("#", 26),
                headCell("NAMA AKUN", 210),
                headCell("STATUS", 110),
                headCell("STREAMS", 72),
                headCell("PENDENGAR", 86),
                headCell("ARTIS", 54),
                headCell("LAGU", 54),
                headCell("SALDO", 120),
                headCell("UPDATE", 100),
                headCell("AKSI", 150));

        VBox rows = new VBox(2, header);
        List<Account> accounts = db.listAccounts(distributor);
        int i = 1;
        for (Account a : accounts) rows.getChildren().add(accountRow(i++, a));
        if (accounts.isEmpty()) {
            Label empty = new Label("Belum ada akun — tambah di menu Akun.");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
        }

        VBox card = new VBox(10, title, rows);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(16));
        return card;
    }

    private HBox accountRow(int idx, Account a) {
        long streams = metaLong("acct." + a.id + ".totalStreams");
        String listeners = orDash(db.getMeta("acct." + a.id + ".listeners"));
        int artists = db.listArtistsForAccount(a.id).size();
        int songs = db.countReleasesForAccount(a.id);
        String saldo = cleanBalance(db.getMeta("acct." + a.id + ".balanceText"));
        String update = fmtTime(db.getMeta("acct." + a.id + ".lastSync"));

        HBox name = new HBox(10, avatar(a.email), nameBlock(a.distributor, "Akun #" + idx));
        name.setAlignment(Pos.CENTER_LEFT);
        name.setPrefWidth(210);
        name.setMinWidth(210);
        name.setMaxWidth(210);

        HBox row = new HBox(10);
        row.getStyleClass().add("akun-row");
        if (selectedAccountId != null && selectedAccountId == a.id) row.getStyleClass().add("akun-row-selected");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 8, 10, 8));
        row.getChildren().addAll(
                cell(String.valueOf(idx), 26),
                name,
                statusCell(a.distributor),
                cell(streams > 0 ? String.format("%,d", streams) : "—", 72),
                cell(listeners, 86),
                cell(String.valueOf(artists), 54),
                cell(String.valueOf(songs), 54),
                cell(saldo, 120),
                cell(update, 100),
                actionCell(a));
        row.setStyle("-fx-cursor: hand;");
        row.setOnMouseClicked(e -> selectAccount(a.id));
        return row;
    }

    private void selectAccount(long id) {
        selectedAccountId = id;
        render();
    }

    private javafx.scene.layout.StackPane avatar(String email) {
        String ini = (email == null || email.isEmpty()) ? "?" : email.substring(0, 1).toUpperCase();
        Label l = new Label(ini);
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        javafx.scene.layout.StackPane sp = new javafx.scene.layout.StackPane(l);
        sp.setMinSize(34, 34);
        sp.setMaxSize(34, 34);
        String[] colors = {"#EC4899", "#8B5CF6", "#34D399", "#F59E0B", "#3B82F6", "#F472B6"};
        int idx = Math.abs(email == null ? 0 : email.hashCode()) % colors.length;
        sp.setStyle("-fx-background-color: " + colors[idx] + "; -fx-background-radius: 17;");
        return sp;
    }

    private VBox nameBlock(String name, String sub) {
        Label n = new Label(name);
        n.getStyleClass().add("label");
        n.setStyle("-fx-font-weight: bold;");
        Label s = new Label(sub);
        s.getStyleClass().add("subtitle");
        return new VBox(1, n, s);
    }

    private VBox statusCell(String dist) {
        Label dot = new Label("● Chrome tertutup");
        dot.setStyle("-fx-text-fill: #8A7682; -fx-font-size: 12px;");
        Label hint = new Label("Klik Buka");
        hint.getStyleClass().add("subtitle");
        VBox v = new VBox(1, dot, hint);
        v.setPrefWidth(110);
        v.setMinWidth(110);
        return v;
    }

    private HBox actionCell(Account a) {
        Button buka = new Button("Buka");
        buka.getStyleClass().add("primary");
        buka.setStyle("-fx-padding: 5 12; -fx-font-size: 12px;");
        buka.setOnAction(e -> {
            e.consume();
            buka.setDisable(true);
            buka.setText("Sync…");
            new SyncEngine(db, key).sync(a.id, true, new SyncEngine.Listener() {
                @Override public void status(String m) { }
                @Override public void done(boolean ok, String msg) {
                    Platform.runLater(() -> {
                        buka.setDisable(false); buka.setText("Buka");
                        if (ok) { selectedAccountId = a.id; render(); if (onSynced != null) onSynced.run(); }
                    });
                }
            });
        });
        Button data = new Button("Data");
        data.setStyle("-fx-padding: 5 12; -fx-font-size: 12px;");
        data.setOnAction(e -> { e.consume(); selectAccount(a.id); });
        HBox h = new HBox(6, buka, data);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPrefWidth(150);
        h.setMinWidth(150);
        return h;
    }

    private Label cell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("label");
        l.setPrefWidth(w);
        l.setMinWidth(w);
        return l;
    }

    private Label headCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("akun-head-cell");
        l.setPrefWidth(w);
        l.setMinWidth(w);
        return l;
    }

    private String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private String cleanBalance(String s) {
        return (s == null || s.isBlank() || s.contains("*")) ? "—" : s;
    }

    private String fmtTime(String last) {
        if (last == null) return "—";
        String v = last.replace("T", " ");
        return v.substring(0, Math.min(16, v.length()));
    }

    private String shorten(String s) {
        if (s == null) return "";
        return s.length() > 11 ? s.substring(0, 10) + "…" : s;
    }

    private VBox buildPanel(String title, List<Node> items) {
        Label header = new Label(title);
        header.getStyleClass().add("h2");
        VBox list = new VBox(8);
        if (items.isEmpty()) {
            Label empty = new Label("Belum ada data");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        } else {
            list.getChildren().addAll(items);
        }
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("flat-scroll");
        sp.setPrefHeight(300);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox panel = new VBox(10, header, sp);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(300);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private List<Node> artisItems(Long sel) {
        List<Node> out = new ArrayList<>();
        for (Database.ArtistStat s : aArtists(sel)) {
            out.add(itemRow(s.artist, String.format("%,d streams · %d lagu", s.streams, s.tracks), null));
        }
        return out;
    }

    private List<Node> laguItems(Long sel) {
        List<Node> out = new ArrayList<>();
        for (Release r : aReleases(sel)) {
            out.add(itemRow(r.title, r.distributor + " · " + String.format("%,d streams", r.streams), r.status));
        }
        return out;
    }

    private Node itemRow(String title, String sub, String badge) {
        Label t = new Label(title);
        t.getStyleClass().add("label");
        t.setWrapText(true);
        VBox text = new VBox(1, t);
        if (sub != null) {
            Label s = new Label(sub);
            s.getStyleClass().add("subtitle");
            text.getChildren().add(s);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(8, text);
        row.setAlignment(Pos.CENTER_LEFT);
        if (badge != null) {
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label b = new Label(badge);
            b.getStyleClass().add("pill");
            switch (badge.toLowerCase()) {
                case "live": b.getStyleClass().add("pill-live"); break;
                case "pending": b.getStyleClass().add("pill-pending"); break;
                case "rejected": b.getStyleClass().add("pill-rejected"); break;
                default: b.getStyleClass().add("pill-accent"); break;
            }
            row.getChildren().addAll(sp, b);
        }
        row.getStyleClass().add("list-item");
        return row;
    }

    private void runSyncSelected(Long sel, Button btn, Label status) {
        if (distributor != null && !Recipe.isSupported(distributor)) {
            status.setText("Sinkron untuk " + distributor + " belum didukung (SoundOn dulu).");
            return;
        }
        btn.setDisable(true);
        status.setText("Memulai sinkron…");
        SyncEngine engine = new SyncEngine(db, key);
        SyncEngine.Listener listener = new SyncEngine.Listener() {
            @Override public void status(String msg) { Platform.runLater(() -> status.setText(msg)); }
            @Override public void done(boolean success, String message) {
                Platform.runLater(() -> {
                    status.setText(message);
                    btn.setDisable(false);
                    if (success && onSynced != null) {
                        new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                                javafx.util.Duration.seconds(1.5), ev -> onSynced.run())).play();
                    }
                });
            }
        };
        // Mode senyap (headless): tanpa jendela Chrome. Fallback otomatis buka jendela
        // sekali bila SoundOn butuh login/verifikasi.
        if (sel != null) engine.sync((long) sel, true, listener);
        else engine.sync(distributor, true, listener);
    }

    private VBox statCard(String label, String value, String icon, String iconClass) {
        Label ic = new Label(icon);
        ic.getStyleClass().addAll("stat-icon", iconClass);

        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        VBox text = new VBox(2, l, v);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox top = new HBox(sp, ic);

        VBox c = new VBox(10, top, text);
        c.getStyleClass().add("stat-card");
        c.setPadding(new Insets(16));
        c.setPrefWidth(190);
        HBox.setHgrow(c, Priority.ALWAYS);
        return c;
    }

    public Parent getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("flat-scroll");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
