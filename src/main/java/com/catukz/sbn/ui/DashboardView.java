package com.catukz.sbn.ui;

import com.catukz.sbn.db.Database;
import com.catukz.sbn.model.Release;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;

public class DashboardView {
    private final VBox root = new VBox(14);

    public DashboardView(Database db, String distributor) {
        root.setPadding(new Insets(4));

        Label header = new Label(distributor == null ? "Semua distributor" : distributor);
        header.getStyleClass().add("h1");

        double amount = db.totalAmount(distributor);
        long streams = db.totalStreams(distributor);
        int releases = db.countReleases(distributor);

        HBox cards = new HBox(12);
        cards.getChildren().addAll(
                statCard("Pendapatan", String.format("$%,.2f", amount)),
                statCard("Stream", String.format("%,d", streams)),
                statCard("Rilisan", String.valueOf(releases)),
                statCard("Distributor", distributor == null ? "Semua" : distributor));

        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel("Bulan");
        y.setLabel("Pendapatan (USD)");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> e : db.amountByMonth(distributor).entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }
        chart.getData().add(series);

        TableView<Release> table = new TableView<>();
        table.getColumns().add(col("Judul", r -> r.title));
        table.getColumns().add(col("Distributor", r -> r.distributor));
        table.getColumns().add(col("Status", r -> r.status));
        table.getColumns().add(col("Stream", r -> String.format("%,d", r.streams)));
        table.getColumns().add(col("Earning", r -> String.format("$%,.2f", r.earnings)));
        table.getItems().addAll(db.listReleases(distributor));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(200);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label relLabel = new Label("Rilisan");
        relLabel.getStyleClass().add("h2");

        root.getChildren().addAll(header, cards, chart, relLabel, table);
    }

    private interface Getter { String get(Release r); }

    private TableColumn<Release, String> col(String title, Getter g) {
        TableColumn<Release, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(g.get(cd.getValue())));
        return c;
    }

    private VBox statCard(String label, String value) {
        VBox c = new VBox(4);
        c.getStyleClass().add("stat-card");
        c.setPadding(new Insets(14));
        c.setPrefWidth(180);
        HBox.setHgrow(c, Priority.ALWAYS);
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        c.getChildren().addAll(l, v);
        return c;
    }

    public Parent getRoot() { return root; }
}
