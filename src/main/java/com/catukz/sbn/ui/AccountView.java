package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Account;
import com.catukz.sbn.model.Distributor;
import com.catukz.sbn.security.Crypto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AccountView {
    private final VBox root = new VBox(12);
    private final Database db;
    private final SecretKey key;
    private final ListView<Account> list = new ListView<>();

    public AccountView(Database db, SecretKey key) {
        this.db = db;
        this.key = key;
        root.setPadding(new Insets(4));

        Label h = new Label("Akun distributor");
        h.getStyleClass().add("h1");
        Label note = new Label("Password disimpan terenkripsi (AES-256) dengan master password Anda.");
        note.getStyleClass().add("muted");

        ComboBox<String> dist = new ComboBox<>();
        for (Distributor d : Distributor.values()) dist.getItems().add(d.label());
        dist.getSelectionModel().selectFirst();

        TextField email = new TextField();
        email.setPromptText("email@contoh.com");
        PasswordField pw = new PasswordField();
        pw.setPromptText("password");

        Button add = new Button("Simpan akun");
        add.getStyleClass().add("primary");
        Label status = new Label();

        add.setOnAction(e -> {
            String em = email.getText().trim();
            String p = pw.getText();
            if (em.isEmpty() || p.isEmpty()) { status.setText("Email & password wajib diisi."); return; }
            byte[] enc = Crypto.encrypt(key, p.getBytes(StandardCharsets.UTF_8));
            db.addAccount(dist.getValue(), dist.getValue(), em, Base64.getEncoder().encodeToString(enc));
            email.clear();
            pw.clear();
            status.setText("Akun " + em + " tersimpan.");
            refresh();
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Distributor"), 0, 0); form.add(dist, 1, 0);
        form.add(new Label("Email"), 0, 1);       form.add(email, 1, 1);
        form.add(new Label("Password"), 0, 2);    form.add(pw, 1, 2);
        form.add(add, 1, 3);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); setGraphic(null); return; }
                HBox rowBox = new HBox(10);
                rowBox.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(a.distributor + "  ·  " + a.email);
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                Button show = new Button("Lihat password");
                show.setOnAction(ev -> showPassword(a));
                Button del = new Button("Hapus");
                del.setOnAction(ev -> { db.deleteAccount(a.id); refresh(); });
                rowBox.getChildren().addAll(name, sp, show, del);
                setText(null);
                setGraphic(rowBox);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Label savedLabel = new Label("Akun tersimpan:");
        savedLabel.getStyleClass().add("h2");

        root.getChildren().addAll(h, note, form, status, savedLabel, list);
        refresh();
    }

    private void showPassword(Account a) {
        try {
            String encB64 = db.getAccountEncPw(a.id);
            String plain = new String(Crypto.decrypt(key, Base64.getDecoder().decode(encB64)), StandardCharsets.UTF_8);
            Alert al = new Alert(Alert.AlertType.INFORMATION, "Password: " + plain);
            al.setHeaderText(a.distributor + " — " + a.email);
            al.setTitle("Kredensial");
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
