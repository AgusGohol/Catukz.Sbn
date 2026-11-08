package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.importer.ColumnMapping;
import com.catukz.sbn.importer.CsvImporter;
import com.catukz.sbn.model.Distributor;
import com.catukz.sbn.model.Earning;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportView {
    private final VBox root = new VBox(12);
    private final Database db;

    private Path selectedFile;
    private List<String> headers = new ArrayList<>();
    private final Map<String, ComboBox<String>> mapCombos = new LinkedHashMap<>();
    private final GridPane mapGrid = new GridPane();
    private final Label status = new Label();

    public ImportView(Database db) {
        this.db = db;
        root.setPadding(new Insets(4));

        Label h = new Label("Import CSV");
        h.getStyleClass().add("h1");

        ComboBox<String> distCombo = new ComboBox<>();
        for (Distributor d : Distributor.values()) distCombo.getItems().add(d.label());
        distCombo.getSelectionModel().selectFirst();

        Button pick = new Button("Pilih file CSV…");
        Label fileLabel = new Label("Belum ada file");
        fileLabel.getStyleClass().add("muted");

        pick.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Pilih laporan CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showOpenDialog(root.getScene().getWindow());
            if (f != null) {
                selectedFile = f.toPath();
                fileLabel.setText(f.getName());
                loadHeaders(distCombo.getValue());
            }
        });

        distCombo.setOnAction(e -> { if (selectedFile != null) loadHeaders(distCombo.getValue()); });

        mapGrid.setHgap(10);
        mapGrid.setVgap(8);
        mapGrid.setPadding(new Insets(8, 0, 8, 0));

        Button importBtn = new Button("Import ke dashboard");
        importBtn.getStyleClass().add("primary");
        importBtn.setOnAction(e -> doImport(distCombo.getValue()));

        HBox row1 = new HBox(10, new Label("Distributor:"), distCombo, pick, fileLabel);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label mapTitle = new Label("Pemetaan kolom (petakan sekali, tersimpan otomatis per distributor):");
        mapTitle.getStyleClass().add("muted");

        root.getChildren().addAll(h, row1, mapTitle, mapGrid, importBtn, status);
    }

    private void loadHeaders(String distributor) {
        try {
            headers = CsvImporter.readHeader(selectedFile);
            buildMapGrid(distributor);
            status.setText(headers.size() + " kolom terbaca dari file.");
        } catch (Exception ex) {
            status.setText("Gagal baca file: " + ex.getMessage());
        }
    }

    private void buildMapGrid(String distributor) {
        mapGrid.getChildren().clear();
        mapCombos.clear();
        Map<String, String> saved = db.getMapping(distributor);
        List<String> options = new ArrayList<>();
        options.add("— tidak ada —");
        options.addAll(headers);
        int r = 0;
        for (String appCol : ColumnMapping.APP_COLUMNS) {
            Label l = new Label(appCol);
            ComboBox<String> cb = new ComboBox<>();
            cb.getItems().addAll(options);
            String savedCol = saved.get(appCol);
            if (savedCol != null && headers.contains(savedCol)) cb.getSelectionModel().select(savedCol);
            else cb.getSelectionModel().select(autoGuess(appCol, headers));
            mapCombos.put(appCol, cb);
            mapGrid.add(l, 0, r);
            mapGrid.add(cb, 1, r);
            r++;
        }
    }

    private String autoGuess(String appCol, List<String> headers) {
        String key = appCol.toLowerCase();
        for (String hh : headers) {
            String hl = hh.toLowerCase();
            if (key.contains("tanggal") && (hl.contains("date") || hl.contains("month") || hl.contains("tanggal"))) return hh;
            if (key.contains("judul") && (hl.contains("title") || hl.contains("track") || hl.contains("song") || hl.contains("lagu"))) return hh;
            if (key.contains("platform") && (hl.contains("store") || hl.contains("platform") || hl.contains("dsp") || hl.contains("service"))) return hh;
            if (key.contains("negara") && (hl.contains("country") || hl.contains("region") || hl.contains("negara"))) return hh;
            if (key.contains("stream") && (hl.contains("stream") || hl.contains("quantity") || hl.contains("plays") || hl.contains("qty"))) return hh;
            if (key.contains("pendapatan") && (hl.contains("earning") || hl.contains("revenue") || hl.contains("amount") || hl.contains("payout") || hl.contains("net"))) return hh;
            if (key.contains("mata") && hl.contains("curr")) return hh;
        }
        return "— tidak ada —";
    }

    private void doImport(String distributor) {
        if (selectedFile == null) { status.setText("Pilih file dulu."); return; }
        try {
            Map<String, String> mapping = new LinkedHashMap<>();
            for (Map.Entry<String, ComboBox<String>> e : mapCombos.entrySet()) {
                String v = e.getValue().getValue();
                if (v != null && !v.startsWith("—")) mapping.put(e.getKey(), v);
            }
            if (!mapping.containsKey(ColumnMapping.AMOUNT) && !mapping.containsKey(ColumnMapping.STREAMS)) {
                status.setText("Petakan minimal kolom Pendapatan atau Stream.");
                return;
            }
            db.saveMapping(distributor, mapping);
            List<Map<String, String>> rows = CsvImporter.readRows(selectedFile);
            int n = 0;
            for (Map<String, String> row : rows) {
                Earning earning = CsvImporter.toEarning(row, mapping, distributor);
                db.addEarning(earning);
                n++;
            }
            status.setText("Berhasil import " + n + " baris dari " + distributor + ". Buka Dashboard untuk melihat.");
        } catch (Exception ex) {
            status.setText("Gagal import: " + ex.getMessage());
        }
    }

    public Parent getRoot() { return root; }
}
