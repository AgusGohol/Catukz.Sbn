package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Distributor;
import com.catukz.sbn.security.Vault;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javax.crypto.SecretKey;

public class MainView {
    private final BorderPane root = new BorderPane();
    private final Database db;
    private final SecretKey key;
    private final Runnable onLock;

    private final HBox chipBar = new HBox(8);
    private final StackPane content = new StackPane();
    private final ToggleGroup chipGroup = new ToggleGroup();

    private String selectedDistributor = null; // null = Semua
    private String nav = "dashboard";

    public MainView(Database db, SecretKey key, Vault vault, Runnable onLock) {
        this.db = db;
        this.key = key;
        this.onLock = onLock;
        root.getStyleClass().add("main-root");
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());
        render();
    }

    private VBox buildSidebar() {
        VBox side = new VBox(4);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(16, 12, 16, 12));
        side.setPrefWidth(180);

        Label brand = new Label("♪  Catukz.Sbn");
        brand.getStyleClass().add("brand");
        brand.setPadding(new Insets(4, 8, 16, 8));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button lock = new Button("🔒  Kunci");
        lock.getStyleClass().add("nav-btn");
        lock.setMaxWidth(Double.MAX_VALUE);
        lock.setAlignment(Pos.CENTER_LEFT);
        lock.setOnAction(e -> onLock.run());

        side.getChildren().addAll(
                brand,
                navButton("Dashboard", "dashboard"),
                navButton("Upload music", "upload"),
                navButton("Import CSV", "import"),
                navButton("Akun", "akun"),
                navButton("Settings", "settings"),
                spacer,
                lock);
        return side;
    }

    private Button navButton(String text, String id) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> { nav = id; render(); });
        return b;
    }

    private VBox buildCenter() {
        VBox center = new VBox(12);
        center.setPadding(new Insets(16));
        chipBar.getStyleClass().add("chip-bar");
        buildChips();
        VBox.setVgrow(content, Priority.ALWAYS);
        center.getChildren().addAll(chipBar, content);
        return center;
    }

    private void buildChips() {
        chipBar.getChildren().clear();
        ToggleButton all = chip("Semua", null);
        all.setSelected(true);
        chipBar.getChildren().add(all);
        for (Distributor d : Distributor.values()) {
            chipBar.getChildren().add(chip(d.label(), d.label()));
        }
    }

    private ToggleButton chip(String text, String distributor) {
        ToggleButton t = new ToggleButton(text);
        t.setToggleGroup(chipGroup);
        t.getStyleClass().add("chip");
        t.setOnAction(e -> {
            if (!t.isSelected()) { t.setSelected(true); return; }
            selectedDistributor = distributor;
            if (nav.equals("dashboard")) render();
        });
        return t;
    }

    private void render() {
        content.getChildren().setAll(buildContentForNav());
    }

    private Parent buildContentForNav() {
        switch (nav) {
            case "import":   return new ImportView(db).getRoot();
            case "akun":     return new AccountView(db, key).getRoot();
            case "settings": return new SettingsView(db).getRoot();
            case "upload":   return uploadPlaceholder();
            default:         return new DashboardView(db, selectedDistributor).getRoot();
        }
    }

    private Parent uploadPlaceholder() {
        VBox v = new VBox(10);
        v.setAlignment(Pos.CENTER);
        Label t = new Label("Upload music");
        t.getStyleClass().add("h1");
        Label m = new Label("Fitur unggah & kirim rilisan ke distributor akan hadir di versi berikutnya.");
        m.getStyleClass().add("muted");
        v.getChildren().addAll(t, m);
        return v;
    }

    public Parent getRoot() { return root; }
}
