package pl.proxion.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import pl.proxion.model.HttpTransaction;
import pl.proxion.model.Header;
import pl.proxion.service.RequestSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    // Publiczne pola dla elementów UI
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
    public TextField searchField;

    // Pola dla zakładek
    public TabPane mainTabPane;
    public Tab proxyTab;
    public Tab requestBuilderTab;
    public Tab rewriteTab; // Nowa zakładka

    private ObservableList<HttpTransaction> trafficData = FXCollections.observableArrayList();
    public ObservableList<HttpTransaction> filteredTrafficData = FXCollections.observableArrayList();
    private ObservableList<Header> headersData = FXCollections.observableArrayList();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private String currentFilter = "";

    // Kontroler rewrite rules
    private RewriteController rewriteController = new RewriteController();

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

        // Inicjalizuj rewrite controller jeśli zakładka istnieje
        if (rewriteTab != null && rewriteTab.getContent() instanceof VBox) {
            rewriteController.initializeRewriteTab((VBox) rewriteTab.getContent());
            System.out.println("✅ Rewrite rules initialized");
        }

        // Ukryj progress indicator na starcie
        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
            System.out.println("✅ Progress indicator initialized");
        }

        // Inicjalizuj filtrowaną listę
        filteredTrafficData.setAll(trafficData);

        System.out.println("✅ MainController fully initialized");
    }

    private void setupTrafficTable() {
        trafficTable.setItems(filteredTrafficData);

        TableColumn<HttpTransaction, String> methodColumn = new TableColumn<>("Method");
        methodColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMethod()));
        methodColumn.setPrefWidth(60);

        TableColumn<HttpTransaction, String> urlColumn = new TableColumn<>("URL");
        urlColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUrl()));
        urlColumn.setPrefWidth(300);

        TableColumn<HttpTransaction, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStatusCode())));
        statusColumn.setPrefWidth(60);

        // Nowa kolumna - czy zmodyfikowane
        TableColumn<HttpTransaction, String> modifiedColumn = new TableColumn<>("Modified");
        modifiedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isModified() ? "✓" : ""));
        modifiedColumn.setPrefWidth(60);

        trafficTable.getColumns().setAll(methodColumn, urlColumn, statusColumn, modifiedColumn);
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
        httpMethodComboBox.getItems().addAll("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        httpMethodComboBox.getSelectionModel().selectFirst();

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
        String statusInfo = transaction.isModified() ?
                String.format("Status: %d → %d (MODIFIED)", transaction.getOriginalStatusCode(), transaction.getStatusCode()) :
                String.format("Status: %d", transaction.getStatusCode());

        return String.format("%s\n\nHeaders:\n%s\n\nBody:\n%s",
                statusInfo,
                transaction.getResponseHeaders(),
                transaction.getResponseBody());
    }

    public void handleSendRequest() {
        System.out.println("🔄 Handling send request...");

        final String method = httpMethodComboBox.getValue();
        String urlValue = urlTextField.getText().trim();
        final String body = requestBodyTextArea.getText();

        System.out.println("Method: " + method + ", URL: " + urlValue);

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

        progressIndicator.setVisible(true);
        sendButton.setDisable(true);
        responseTextArea.setText("Sending request...");

        System.out.println("⏳ Sending " + method + " request to: " + finalUrl);

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

    public void handleClearTraffic() {
        trafficData.clear();
        filteredTrafficData.clear();
        requestDetails.clear();
        responseDetails.clear();
        System.out.println("🧹 Cleared all traffic data");
    }

    public void handleFilterTraffic() {
        if (searchField != null && !searchField.getText().isEmpty()) {
            currentFilter = searchField.getText().toLowerCase();
            applyFilter();
        } else {
            filteredTrafficData.setAll(trafficData);
        }
    }

    public void handleSearchTraffic(String searchText) {
        currentFilter = searchText.toLowerCase();
        applyFilter();
    }

    private void applyFilter() {
        if (currentFilter.isEmpty()) {
            filteredTrafficData.setAll(trafficData);
            return;
        }

        ObservableList<HttpTransaction> filteredList = FXCollections.observableArrayList();

        for (HttpTransaction transaction : trafficData) {
            if (matchesFilter(transaction, currentFilter)) {
                filteredList.add(transaction);
            }
        }

        filteredTrafficData.setAll(filteredList);
        System.out.println("🔍 Filter applied: " + filteredList.size() + " items match '" + currentFilter + "'");
    }

    private boolean matchesFilter(HttpTransaction transaction, String filter) {
        if (transaction.getMethod() != null && transaction.getMethod().toLowerCase().contains(filter)) {
            return true;
        }
        if (transaction.getUrl() != null && transaction.getUrl().toLowerCase().contains(filter)) {
            return true;
        }
        if (transaction.getRequestHeaders() != null && transaction.getRequestHeaders().toLowerCase().contains(filter)) {
            return true;
        }
        if (transaction.getRequestBody() != null && transaction.getRequestBody().toLowerCase().contains(filter)) {
            return true;
        }
        if (String.valueOf(transaction.getStatusCode()).contains(filter)) {
            return true;
        }
        return false;
    }

    public void handleModifyResponse() {
        HttpTransaction selected = trafficTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openResponseModifier(selected);
        }
    }

    private void openResponseModifier(HttpTransaction transaction) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Response Modifier");
        alert.setHeaderText("Modify Response");
        alert.setContentText("This feature is not implemented yet.");
        alert.showAndWait();
    }

    public void addHttpTransaction(HttpTransaction transaction) {
        Platform.runLater(() -> {
            trafficData.add(transaction);

            if (currentFilter.isEmpty() || matchesFilter(transaction, currentFilter)) {
                filteredTrafficData.add(transaction);
            }

            System.out.println("📥 Added HTTP transaction: " + transaction.getMethod() + " " + transaction.getUrl());

            trafficTable.getSelectionModel().select(transaction);
            displayTransactionDetails(transaction);
        });
    }

    public int applyStatusCodeRewrite(int originalStatusCode, String url) {
        return rewriteController.applyRewriteRules(originalStatusCode, url);
    }

    public RewriteController getRewriteController() {
        return rewriteController;
    }

    public void shutdown() {
        executorService.shutdown();
        System.out.println("🛑 MainController shutdown");
    }
}