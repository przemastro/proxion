package pl.proxion.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import pl.proxion.model.HttpTransaction;
import pl.proxion.model.Header;
import pl.proxion.service.RequestSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

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
    public TextArea responseHeadersTextArea;
    public Label responseStatusLabel;
    public Label responseTimeLabel;
    public Button copyResponseButton;
    public Button clearRequestButton;
    public TabPane responseTabPane;
    public Tab responseBodyTab;
    public Tab responseHeadersTab;
    public TabPane mainTabPane;
    public Tab proxyTab;
    public Tab requestBuilderTab;
    public Tab rewriteTab;

    private ObservableList<HttpTransaction> trafficData = FXCollections.observableArrayList();
    public ObservableList<HttpTransaction> filteredTrafficData = FXCollections.observableArrayList();
    private ObservableList<Header> headersData = FXCollections.observableArrayList();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private String currentFilter = "";

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

        if (rewriteTab != null && rewriteTab.getContent() instanceof VBox) {
            rewriteController.initializeRewriteTab((VBox) rewriteTab.getContent());
            System.out.println("✅ Rewrite rules initialized");
        }

        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
            System.out.println("✅ Progress indicator initialized");
        }

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

        if (copyResponseButton != null) {
            copyResponseButton.setOnAction(e -> handleCopyResponse());
        }

        if (clearRequestButton != null) {
            clearRequestButton.setOnAction(e -> handleClearRequest());
        }

        if (responseTimeLabel != null) {
            responseTimeLabel.setVisible(false);
        }
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

    public void handleSendRequest() {
        System.out.println("🔄 Handling send request...");

        final String method = httpMethodComboBox.getValue();
        String urlValue = urlTextField.getText().trim();
        final String body = requestBodyTextArea.getText();

        System.out.println("Method: " + method + ", URL: " + urlValue);

        if (urlValue.isEmpty()) {
            System.out.println("❌ URL is empty");
            showResponseError("❌ Please enter a URL");
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

        if (responseTimeLabel != null) {
            responseTimeLabel.setVisible(false);
        }

        showResponseMessage("⏳ Sending request...");

        final long startTime = System.currentTimeMillis();

        executorService.submit(() -> {
            try {
                System.out.println("📤 Executing request in background thread...");
                RequestSender.RequestResult result = RequestSender.sendRequest(method, finalUrl, headersData, body);
                final long responseTime = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    displayResponseResult(result, responseTime);
                    progressIndicator.setVisible(false);
                    sendButton.setDisable(false);
                    System.out.println("✅ Request completed in " + responseTime + "ms");
                });
            } catch (Exception e) {
                System.err.println("❌ Error in background thread: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    showResponseError("❌ Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    sendButton.setDisable(false);
                });
            }
        });
    }

    private void displayResponseResult(RequestSender.RequestResult result, long responseTime) {
        if (responseStatusLabel != null) {
            responseStatusLabel.setText("Status: " + result.getStatusCode());
            responseStatusLabel.setStyle(getStatusStyle(result.getStatusCode()));
        }

        if (responseTimeLabel != null) {
            responseTimeLabel.setText("Time: " + responseTime + "ms");
            responseTimeLabel.setVisible(true);
        }

        String formattedBody = formatResponseBody(result.getBody(), result.getContentType());
        responseTextArea.setText(formattedBody);

        if (responseHeadersTextArea != null) {
            responseHeadersTextArea.setText(result.getHeaders());
        }

        if (responseTabPane != null && responseBodyTab != null) {
            responseTabPane.getSelectionModel().select(responseBodyTab);
        }
    }

    private String getStatusStyle(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "-fx-text-fill: green; -fx-font-weight: bold;";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "-fx-text-fill: orange; -fx-font-weight: bold;";
        } else if (statusCode >= 500) {
            return "-fx-text-fill: red; -fx-font-weight: bold;";
        } else {
            return "-fx-text-fill: blue; -fx-font-weight: bold;";
        }
    }

    private String formatResponseBody(String body, String contentType) {
        if (body == null || body.trim().isEmpty()) {
            return "[Empty response]";
        }

        if (contentType != null && contentType.contains("json")) {
            try {
                return formatJson(body);
            } catch (Exception e) {
                return body;
            }
        }

        if (contentType != null && (contentType.contains("xml") || body.trim().startsWith("<"))) {
            try {
                return formatXml(body);
            } catch (Exception e) {
                return body;
            }
        }

        return body;
    }

    private String formatJson(String json) {
        int indent = 0;
        StringBuilder formatted = new StringBuilder();
        boolean inQuotes = false;

        for (char c : json.toCharArray()) {
            if (c == '\"' && (formatted.length() == 0 || formatted.charAt(formatted.length()-1) != '\\')) {
                inQuotes = !inQuotes;
            }

            if (!inQuotes) {
                if (c == '{' || c == '[') {
                    formatted.append(c).append("\n");
                    indent += 2;
                    formatted.append(" ".repeat(indent));
                } else if (c == '}' || c == ']') {
                    formatted.append("\n");
                    indent -= 2;
                    formatted.append(" ".repeat(indent));
                    formatted.append(c);
                } else if (c == ',') {
                    formatted.append(c).append("\n");
                    formatted.append(" ".repeat(indent));
                } else if (c == ':') {
                    formatted.append(c).append(" ");
                } else {
                    formatted.append(c);
                }
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString();
    }

    private String formatXml(String xml) {
        try {
            return xml.replaceAll("><", ">\n<")
                    .replaceAll("(<[^/][^>]*>)", "$1\n")
                    .replaceAll("(</[^>]*>)", "\n$1\n");
        } catch (Exception e) {
            return xml;
        }
    }

    private void showResponseMessage(String message) {
        responseTextArea.setText(message);
        if (responseHeadersTextArea != null) {
            responseHeadersTextArea.clear();
        }
        if (responseStatusLabel != null) {
            responseStatusLabel.setText("");
        }
    }

    private void showResponseError(String error) {
        responseTextArea.setText(error);
        if (responseHeadersTextArea != null) {
            responseHeadersTextArea.clear();
        }
        if (responseStatusLabel != null) {
            responseStatusLabel.setText("Status: Error");
            responseStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }
    }

    public void handleCopyResponse() {
        if (responseTextArea != null && !responseTextArea.getText().isEmpty()) {
            String selection = responseTextArea.getSelectedText();
            String textToCopy = selection.isEmpty() ? responseTextArea.getText() : selection;

            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);

            if (responseStatusLabel != null) {
                String originalText = responseStatusLabel.getText();
                responseStatusLabel.setText("✓ Copied to clipboard!");
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(() -> responseStatusLabel.setText(originalText));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }
    }

    public void handleClearRequest() {
        requestBodyTextArea.clear();
        headersData.clear();
        setupHeadersTable();
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