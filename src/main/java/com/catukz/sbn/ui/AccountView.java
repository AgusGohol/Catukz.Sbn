package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Account;
import com.catukz.sbn.security.Crypto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AccountView {
    private final VBox root = new VBox(14);
    private final Database db;
    private final SecretKey key;
    private final String selectedDistributor;
    private final ListView<Account> list = new ListView<>();

    public AccountView(Database db, SecretKey key, String selectedDistributor) {
        this.db = db;
        this.key = key;
        this.selectedDistributor = selectedDistributor;
        root.setPadding(new Insets(4));

        Label h = new Label("Akun distributor");
        h.getStyleClass().add("h1");
        Label note = new Label("Kelola akun login tiap distributor. Password tersimpan terenkripsi.");
        note.getStyleClass().add("subtitle");
        VBox titleBox = new VBox(2, h, note);

        Button add = new Button("+  Tambah akun");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> {
            boolean saved = AddAccountDialog.show(
                    root.getScene() == null ? null : root.getScene().getWindow(),
                    db, key, selectedDistributor);
            if (saved) refresh();
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox headerRow = new HBox(10, titleBox, sp, add);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); setGraphic(null); return; }
                HBox rowBox = new HBox(12);
                rowBox.setAlignment(Pos.CENTER_LEFT);

                Label badge = new Label(a.distributor);
                badge.getStyleClass().addAll("pill", "pill-accent");

                VBox cell = new VBox(4, badge, labelMuted(a.email));

                Region s = new Region();
                HBox.setHgrow(s, Priority.ALWAYS);
                Button show = new Button("Lihat password");
                show.setOnAction(ev -> showPassword(a));
                Button del = new Button("Hapus");
                del.setOnAction(ev -> { db.deleteAccount(a.id); refresh(); });

                rowBox.getChildren().addAll(cell, s, show, del);
                setText(null);
                setGraphic(rowBox);
            }
        });
        list.setPlaceholder(new Label("Belum ada akun — klik “+ Tambah akun”"));
        VBox.setVgrow(list, Priority.ALWAYS);

        root.getChildren().addAll(headerRow, list);
        refresh();
    }

    private Label labelMuted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("label");
        return l;
    }

    private void showPassword(Account a) {
        try {
            String encB64 = db.getAccountEncPw(a.id);
            String plain = new String(Crypto.decrypt(key, Base64.getDecoder().decode(encB64)), StandardCharsets.UTF_8);
            Alert al = new Alert(Alert.AlertType.INFORMATION, "Password: " + plain);
            al.setHeaderText(a.distributor + " — " + a.email);
            al.setTitle("Kredensial");
            try {
                al.getDialogPane().getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
                al.getDialogPane().getStyleClass().add("app-dialog");
            } catch (Exception ignore) {}
            al.showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal dekripsi.").showAndWait();
        }
    }

    private void refresh() {
        list.getItems().setAll(db.listAccounts());
    }

    public Parent getRoot() { return root; }
}
