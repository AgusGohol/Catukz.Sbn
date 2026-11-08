package com.catukz.sbn;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.security.Vault;
import com.catukz.sbn.ui.LockView;
import com.catukz.sbn.ui.MainView;
import com.catukz.sbn.util.SampleData;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.crypto.SecretKey;

public class App extends Application {
    public static final String VERSION = "0.1.0";

    private Stage stage;
    private Database db;
    private Vault vault;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.db = new Database();
        this.vault = new Vault(db);
        stage.setTitle("Catukz.Sbn — Music Distributor Dashboard");
        stage.setWidth(1100);
        stage.setHeight(680);
        showLock();
        stage.show();
    }

    private void showLock() {
        LockView lock = new LockView(vault, this::onUnlocked);
        setRoot(lock.getRoot());
    }

    private void onUnlocked(SecretKey key) {
        SampleData.seedIfEmpty(db);
        MainView main = new MainView(db, key, vault, this::showLock);
        setRoot(main.getRoot());
    }

    private void setRoot(Parent root) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            var css = getClass().getResource("/styles.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
