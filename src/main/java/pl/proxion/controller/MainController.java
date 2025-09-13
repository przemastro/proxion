package pl.proxion.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import pl.proxion.model.HttpTransaction;
import pl.proxion.model.Header;
import pl.proxion.service.RequestSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    // Publiczne pola dla elementów UI (bez @FXML)
    public TableView<HttpTransaction> trafficTable;
    public TextArea requestDetails;
    public TextArea responseDetails;
    public ComboBox<String> httpMethodComboBox;
    public TextField urlTextField;
    public TableView<Header> headersTableView;
    public TextArea requestBodyTextArea;
    public TextArea responseTextArea;
    public Button sendButton;
    public ProgressIndicator progressIndicator;

    // Pola dla zakładek (nieużywane w programowym UI)
    public TabPane mainTabPane;
    public Tab proxyTab;
    public Tab requestBuilderTab;

    private ObservableList<HttpTransaction> trafficData = FXCollections.observableArrayList();
    private ObservableList<Header> headersData = FXCollections.observableArrayList();
    private ExecutorService executorService = Executors.newCachedThreadPool();

    public void initialize() {
        System.out.println("🔄 Initializing MainController...");

        if (trafficTable != null) {
            setupTableSelection();
            setupTrafficTable();
            System.out.println("✅ Traffic table initialized");
        }

        if (httpMethodComboBox != null && headersTableView != null) {
            setupRequestBuilderTab();
            System.out.println("✅ Request Builder initialized");
        }

        // Ukryj progress indicator na starcie
        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
            System.out.println("✅ Progress indicator initialized");
        }

        System.out.println("✅ MainController fully initialized");
    }

    private void setupTrafficTable() {
        trafficTable.setItems(trafficData);

        // Konfiguracja kolumn
        TableColumn<HttpTransaction, String> methodColumn = new TableColumn<>("Method");
        methodColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMethod()));
        methodColumn.setPrefWidth(60);

        TableColumn<HttpTransaction, String> urlColumn = new TableColumn<>("URL");
        urlColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUrl()));
        urlColumn.setPrefWidth(300);

        TableColumn<HttpTransaction, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStatusCode())));
        statusColumn.setPrefWidth(60);

        trafficTable.getColumns().setAll(methodColumn, urlColumn, statusColumn);
    }

    private void setupTableSelection() {
        trafficTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        displayTransactionDetails(newSelection);
                    }
                });
    }

    private void setupRequestBuilderTab() {
        // Inicjalizacja combobox z metodami HTTP
        httpMethodComboBox.getItems().addAll("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        httpMethodComboBox.getSelectionModel().selectFirst();

        // Inicjalizacja tabeli nagłówków
        setupHeadersTable();
    }

    private void setupHeadersTable() {
        headersTableView.setItems(headersData);

        TableColumn<Header, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        nameColumn.setPrefWidth(150);

        TableColumn<Header, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));
        valueColumn.setPrefWidth(250);

        headersTableView.getColumns().setAll(nameColumn, valueColumn);

        // Dodaj przykładowe nagłówki
        headersData.add(new Header("Content-Type", "application/json"));
        headersData.add(new Header("User-Agent", "Proxion/1.0"));
    }

    private void displayTransactionDetails(HttpTransaction transaction) {
        requestDetails.setText(formatRequest(transaction));
        responseDetails.setText(formatResponse(transaction));
    }

    private String formatRequest(HttpTransaction transaction) {
        return String.format("%s %s\n\nHeaders:\n%s\n\nBody:\n%s",
                transaction.getMethod(),
                transaction.getUrl(),
                transaction.getRequestHeaders(),
                transaction.getRequestBody());
    }

    private String formatResponse(HttpTransaction transaction) {
        return String.format("Status: %d\n\nHeaders:\n%s\n\nBody:\n%s",
                transaction.getStatusCode(),
                transaction.getResponseHeaders(),
                transaction.getResponseBody());
    }

    public void handleSendRequest() {
        System.out.println("🔄 Handling send request...");

        final String method = httpMethodComboBox.getValue();
        String urlValue = urlTextField.getText().trim();
        final String body = requestBodyTextArea.getText();

        System.out.println("Method: " + method + ", URL: " + urlValue);

        // Walidacja URL
        if (urlValue.isEmpty()) {
            System.out.println("❌ URL is empty");
            responseTextArea.setText("❌ Please enter a URL");
            return;
        }

        if (!urlValue.startsWith("http://") && !urlValue.startsWith("https://")) {
            urlValue = "http://" + urlValue;
            final String finalUrl = urlValue;
            Platform.runLater(() -> urlTextField.setText(finalUrl));
            System.out.println("🔧 Added http:// prefix to URL: " + urlValue);
        }

        final String finalUrl = urlValue;

        // Pokaż progress indicator
        progressIndicator.setVisible(true);
        sendButton.setDisable(true);
        responseTextArea.setText("Sending request...");

        System.out.println("⏳ Sending " + method + " request to: " + finalUrl);

        // Wykonaj request w tle
        executorService.submit(() -> {
            try {
                System.out.println("📤 Executing request in background thread...");
                String response = RequestSender.sendRequest(method, finalUrl, headersData, body);

                Platform.runLater(() -> {
                    responseTextArea.setText(response);
                    progressIndicator.setVisible(false);
                    sendButton.setDisable(false);
                    System.out.println("✅ Request completed successfully");
                });
            } catch (Exception e) {
                System.err.println("❌ Error in background thread: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    responseTextArea.setText("❌ Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    sendButton.setDisable(false);
                });
            }
        });
    }

    public void handleAddHeader() {
        headersData.add(new Header("Header-Name", "Header-Value"));
        System.out.println("➕ Added new header, total: " + headersData.size());
    }

    public void handleRemoveHeader() {
        Header selected = headersTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            headersData.remove(selected);
            System.out.println("➖ Removed header: " + selected.getName());
        }
    }

    public void handleModifyResponse() {
        HttpTransaction selected = trafficTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Otwórz okno dialogowe do modyfikacji odpowiedzi
            openResponseModifier(selected);
        }
    }

    private void openResponseModifier(HttpTransaction transaction) {
        // Kod do otwarcia okna modyfikacji odpowiedzi
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Response Modifier");
        alert.setHeaderText("Modify Response");
        alert.setContentText("This feature is not implemented yet.");
        alert.showAndWait();
    }

    public void addHttpTransaction(HttpTransaction transaction) {
        Platform.runLater(() -> {
            trafficData.add(transaction);
            System.out.println("📥 Added HTTP transaction: " + transaction.getMethod() + " " + transaction.getUrl());
        });
    }

    public void shutdown() {
        executorService.shutdown();
        System.out.println("🛑 MainController shutdown");
    }
}