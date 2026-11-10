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
import java.util.LinkedHashMap;
import java.util.Map;

public class MainView {
    private final BorderPane root = new BorderPane();
    private final Database db;
    private final SecretKey key;
    private final Runnable onLock;

    private final HBox chipBar = new HBox(8);
    private final StackPane content = new StackPane();
    private final ToggleGroup chipGroup = new ToggleGroup();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final com.catukz.sbn.scrape.AutoSync autoSync;

    private String selectedDistributor = null; // null = Semua
    private String nav = "dashboard";

    public MainView(Database db, SecretKey key, Vault vault, Runnable onLock, com.catukz.sbn.scrape.AutoSync autoSync) {
        this.db = db;
        this.key = key;
        this.onLock = onLock;
        this.autoSync = autoSync;
        root.getStyleClass().add("main-root");
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());
        render();
    }

    private VBox buildSidebar() {
        VBox side = new VBox(5);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(18, 14, 18, 14));
        side.setPrefWidth(200);

        javafx.scene.Node mark = logoMark(30);
        Label brand = new Label("Catukz.Sbn");
        brand.getStyleClass().add("brand");
        HBox brandBox = new HBox(10, mark, brand);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.getStyleClass().add("brand-box");

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("section-label");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button lock = new Button("Kunci aplikasi");
        lock.getStyleClass().add("nav-btn");
        lock.setMaxWidth(Double.MAX_VALUE);
        lock.setAlignment(Pos.CENTER_LEFT);
        lock.setOnAction(e -> onLock.run());

        side.getChildren().addAll(
                brandBox,
                menuLabel,
                navButton("Dashboard", "dashboard"),
                navButton("Upload music", "upload"),
                navButton("Import CSV", "import"),
                navButton("Akun", "akun"),
                navButton("Settings", "settings"),
                spacer,
                lock);
        setActiveNav("dashboard");
        return side;
    }

    private Button navButton(String text, String id) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> { setActiveNav(id); nav = id; render(); });
        navButtons.put(id, b);
        return b;
    }

    private javafx.scene.Node logoMark(double size) {
        try {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png"), size, size, true, true));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            return iv;
        } catch (Exception e) {
            Label l = new Label("C");
            l.getStyleClass().add("logo-mark");
            return l;
        }
    }

    private void setActiveNav(String id) {
        for (Map.Entry<String, Button> e : navButtons.entrySet()) {
            e.getValue().getStyleClass().remove("active");
            if (e.getKey().equals(id)) e.getValue().getStyleClass().add("active");
        }
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
        t.setUserData(distributor);
        t.setOnAction(e -> {
            if (!t.isSelected()) { t.setSelected(true); return; }
            selectedDistributor = distributor;
            if (nav.equals("dashboard")) render();
        });
        return t;
    }

    /** Dipanggil saat user mengklik sebuah akun di panel Akun. */
    public void selectDistributor(String dist) {
        selectedDistributor = dist;
        for (javafx.scene.Node n : chipBar.getChildren()) {
            if (n instanceof ToggleButton tb) {
                boolean match = (dist == null && tb.getUserData() == null)
                        || (dist != null && dist.equals(tb.getUserData()));
                tb.setSelected(match);
            }
        }
        nav = "dashboard";
        setActiveNav("dashboard");
        render();
    }

    private void render() {
        content.getChildren().setAll(buildContentForNav());
    }

    private Parent buildContentForNav() {
        switch (nav) {
            case "import":   return new ImportView(db).getRoot();
            case "akun":     return new AccountView(db, key, selectedDistributor).getRoot();
            case "settings": return new SettingsView(db, autoSync).getRoot();
            case "upload":   return uploadPlaceholder();
            default:         return new DashboardView(db, selectedDistributor, key, this::render, this::selectDistributor).getRoot();
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
