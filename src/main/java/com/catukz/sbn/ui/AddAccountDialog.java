package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Distributor;
import com.catukz.sbn.security.Crypto;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Popup untuk menambah akun distributor (Email/Gmail + Password). */
public class AddAccountDialog {

    public static boolean show(Window owner, Database db, SecretKey key, String preselectDistributor) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tambah akun");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) dialog.initOwner(owner);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(null);
        pane.getStyleClass().add("app-dialog");
        try {
            pane.getStylesheets().add(AddAccountDialog.class.getResource("/styles.css").toExternalForm());
        } catch (Exception ignore) {}

        // --- Header (logo + judul) ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        try {
            ImageView iv = new ImageView(new Image(AddAccountDialog.class.getResourceAsStream("/logo.png"), 40, 40, true, true));
            iv.setFitWidth(40);
            iv.setFitHeight(40);
            header.getChildren().add(iv);
        } catch (Exception ignore) {}
        Label title = new Label("Tambah akun");
        title.getStyleClass().add("h1");
        Label sub = new Label("Simpan kredensial distributor dengan aman");
        sub.getStyleClass().add("subtitle");
        header.getChildren().add(new VBox(1, title, sub));

        // --- Form ---
        ComboBox<String> dist = new ComboBox<>();
        for (Distributor d : Distributor.values()) dist.getItems().add(d.label());
        if (preselectDistributor != null && dist.getItems().contains(preselectDistributor)) {
            dist.getSelectionModel().select(preselectDistributor);
        } else {
            dist.getSelectionModel().selectFirst();
        }
        dist.setMaxWidth(Double.MAX_VALUE);

        TextField email = new TextField();
        email.setPromptText("email@gmail.com");
        PasswordField pw = new PasswordField();
        pw.setPromptText("password");

        Label error = new Label();
        error.getStyleClass().add("error");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(16, 2, 2, 2));
        GridPane.setHgrow(dist, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(email, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(pw, javafx.scene.layout.Priority.ALWAYS);
        g.add(new Label("Distributor"), 0, 0); g.add(dist, 1, 0);
        g.add(new Label("Email / Gmail"), 0, 1); g.add(email, 1, 1);
        g.add(new Label("Password"), 0, 2); g.add(pw, 1, 2);
        g.add(error, 1, 3);

        Label lock = new Label("🔒  Password dienkripsi AES-256 dengan master password Anda.");
        lock.getStyleClass().add("muted");

        VBox box = new VBox(8, header, g, lock);
        box.setPadding(new Insets(20, 20, 8, 20));
        box.setPrefWidth(420);
        pane.setContent(box);

        ButtonType saveBt = new ButtonType("Simpan akun", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBt = new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(cancelBt, saveBt);

        Button saveButton = (Button) pane.lookupButton(saveBt);
        saveButton.getStyleClass().add("primary");
        saveButton.addEventFilter(ActionEvent.ACTION, ev -> {
            if (email.getText().trim().isEmpty() || pw.getText().isEmpty()) {
                error.setText("Email & password wajib diisi.");
                ev.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveBt) {
            byte[] enc = Crypto.encrypt(key, pw.getText().getBytes(StandardCharsets.UTF_8));
            db.addAccount(dist.getValue(), dist.getValue(), email.getText().trim(),
                    Base64.getEncoder().encodeToString(enc));
            return true;
        }
        return false;
    }
}
