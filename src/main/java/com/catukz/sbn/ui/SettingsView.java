package com.catukz.sbn.ui;

import com.catukz.sbn.App;
import com.catukz.sbn.db.Database;
import com.catukz.sbn.update.UpdateChecker;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public class SettingsView {
    private final VBox root = new VBox(14);

    public SettingsView(Database db) {
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
                    if (r.error != null) {
                        updStatus.setText("Info: " + r.error);
                    } else if (r.updateAvailable) {
                        updStatus.setText("Update tersedia: " + r.latestTag
                                + " (Anda " + r.currentVersion + "). Unduh: " + r.htmlUrl);
                    } else {
                        updStatus.setText("Sudah versi terbaru (" + r.currentVersion + ").");
                    }
                });
            }).start();
        });

        TitledPane sync = new TitledPane();
        sync.setText("Auto-sync terjadwal (segera hadir — V4)");
        sync.setExpanded(false);
        VBox sb = new VBox(8);
        sb.setPadding(new Insets(8));
        CheckBox enable = new CheckBox("Aktifkan sinkronisasi terjadwal");
        enable.setDisable(true);
        ComboBox<String> interval = new ComboBox<>();
        interval.getItems().addAll("1x sehari", "2x sehari", "Tiap 6 jam");
        interval.getSelectionModel().selectFirst();
        interval.setDisable(true);
        Label desc = new Label("Scraping akan login otomatis memakai akun tersimpan, dengan jadwal jarang & acak agar aman dari deteksi.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        sb.getChildren().addAll(enable, new Label("Interval:"), interval, desc);
        sync.setContent(sb);

        root.getChildren().addAll(h, ver, repo, check, updStatus, new Separator(), sync);
    }

    public Parent getRoot() { return root; }
}
