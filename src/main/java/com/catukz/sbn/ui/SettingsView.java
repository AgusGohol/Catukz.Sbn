package com.catukz.sbn.ui;

import com.catukz.sbn.App;
import com.catukz.sbn.db.Database;
import com.catukz.sbn.scrape.AutoSync;
import com.catukz.sbn.update.UpdateChecker;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsView {
    private final VBox root = new VBox(14);

    public SettingsView(Database db, AutoSync autoSync) {
        root.setPadding(new Insets(4));

        Label h = new Label("Settings");
        h.getStyleClass().add("h1");

        Label ver = new Label("Versi aplikasi: " + App.VERSION);
        Label repo = new Label("Repo update: github.com/" + UpdateChecker.OWNER + "/" + UpdateChecker.REPO);
        repo.getStyleClass().add("muted");

        Label updStatus = new Label("");
        Button check = new Button("Cek update dari GitHub");
        check.getStyleClass().add("primary");
        check.setOnAction(e -> {
            updStatus.setText("Mengecek…");
            new Thread(() -> {
                UpdateChecker.Result r = UpdateChecker.check(App.VERSION);
                Platform.runLater(() -> {
                    if (r.error != null) updStatus.setText("Info: " + r.error);
                    else if (r.updateAvailable) updStatus.setText("Update tersedia: " + r.latestTag + " — " + r.htmlUrl);
                    else updStatus.setText("Sudah versi terbaru (" + r.currentVersion + ").");
                });
            }).start();
        });

        // ---------- Auto-sync ----------
        Label asTitle = new Label("Auto-sync terjadwal");
        asTitle.getStyleClass().add("h2");
        Label asDesc = new Label("Sinkron otomatis di latar belakang (headless) memakai sesi login tersimpan. "
                + "Jadwal santai agar aman & tidak membebani server distributor.");
        asDesc.getStyleClass().add("muted");
        asDesc.setWrapText(true);

        Map<String, Integer> intervals = new LinkedHashMap<>();
        intervals.put("Tiap 6 jam", 6);
        intervals.put("Tiap 12 jam", 12);
        intervals.put("1x sehari", 24);

        CheckBox enable = new CheckBox("Aktifkan auto-sync");
        enable.setSelected(autoSync.isEnabled());

        ComboBox<String> interval = new ComboBox<>();
        interval.getItems().addAll(intervals.keySet());
        String cur = "1x sehari";
        for (Map.Entry<String, Integer> en : intervals.entrySet())
            if (en.getValue() == autoSync.intervalHours()) cur = en.getKey();
        interval.getSelectionModel().select(cur);

        Label asStatus = new Label(statusLine(db, autoSync));
        asStatus.getStyleClass().add("subtitle");

        Runnable apply = () -> {
            int hours = intervals.getOrDefault(interval.getValue(), 24);
            autoSync.setEnabled(enable.isSelected(), hours);
            asStatus.setText(statusLine(db, autoSync));
        };
        enable.setOnAction(e -> { interval.setDisable(!enable.isSelected()); apply.run(); });
        interval.setOnAction(e -> apply.run());
        interval.setDisable(!enable.isSelected());

        HBox asRow = new HBox(12, enable, new Label("Interval:"), interval);
        asRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox autoBox = new VBox(8, asTitle, asDesc, asRow, asStatus);
        autoBox.getStyleClass().add("panel");
        autoBox.setPadding(new Insets(16));

        Label note = new Label("Catatan: jika sesi login kedaluwarsa, lakukan Sinkronkan manual sekali (berjendela) untuk memperbarui sesi.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        root.getChildren().addAll(h, ver, repo, check, updStatus, new Separator(), autoBox, note);
    }

    private String statusLine(Database db, AutoSync autoSync) {
        if (!autoSync.isEnabled()) return "Status: nonaktif.";
        String last = db.getMeta("autosync.last.SoundOn");
        String lastTxt = last == null ? "belum pernah" : last.replace("T", " ").substring(0, Math.min(16, last.length()));
        return "Status: aktif, tiap " + autoSync.intervalHours() + " jam. Auto-sync terakhir: " + lastTxt + ".";
    }

    public Parent getRoot() { return root; }
}
