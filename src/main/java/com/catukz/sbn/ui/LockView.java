package com.catukz.sbn.ui;

import com.catukz.sbn.security.Vault;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import javax.crypto.SecretKey;
import java.util.function.Consumer;

public class LockView {
    private final VBox root = new VBox(14);

    public LockView(Vault vault, Consumer<SecretKey> onUnlocked) {
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("lock-root");

        javafx.scene.Node mark;
        try {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png"), 72, 72, true, true));
            iv.setFitWidth(72);
            iv.setFitHeight(72);
            mark = iv;
        } catch (Exception e) {
            Label m = new Label("C");
            m.getStyleClass().add("logo-mark-lg");
            mark = m;
        }
        Label logo = new Label("Catukz.Sbn");
        logo.getStyleClass().add("logo");
        Label sub = new Label("Music Distributor Dashboard");
        sub.getStyleClass().add("muted");

        boolean first = !vault.isInitialized();
        Label prompt = new Label(first ? "Buat master password baru" : "Masukkan master password");
        prompt.getStyleClass().add("h2");

        PasswordField pw = new PasswordField();
        pw.setPromptText("Master password");
        pw.setMaxWidth(320);

        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Ulangi master password");
        pw2.setMaxWidth(320);
        pw2.setManaged(first);
        pw2.setVisible(first);

        Label error = new Label();
        error.getStyleClass().add("error");

        Button go = new Button(first ? "Buat & Masuk" : "Buka");
        go.setDefaultButton(true);
        go.getStyleClass().add("primary");
        go.setMaxWidth(320);

        Runnable submit = () -> {
            error.setText("");
            String p = pw.getText();
            if (p == null || p.length() < 6) { error.setText("Minimal 6 karakter"); return; }
            if (first) {
                if (!p.equals(pw2.getText())) { error.setText("Password tidak sama"); return; }
                onUnlocked.accept(vault.initialize(p.toCharArray()));
            } else {
                SecretKey key = vault.unlock(p.toCharArray());
                if (key == null) { error.setText("Master password salah"); pw.clear(); return; }
                onUnlocked.accept(key);
            }
        };
        go.setOnAction(e -> submit.run());
        pw.setOnAction(e -> submit.run());
        pw2.setOnAction(e -> submit.run());

        VBox card = new VBox(12, prompt, pw, pw2, go, error);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("card");
        card.setMaxWidth(380);
        card.setPadding(new Insets(28));

        VBox gap = new VBox(6, logo, sub);
        gap.setAlignment(Pos.CENTER);
        root.getChildren().addAll(mark, gap, card);
    }

    public Parent getRoot() { return root; }
}
