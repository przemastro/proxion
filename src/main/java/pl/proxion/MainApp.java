package pl.proxion;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pl.proxion.controller.MainController;
import pl.proxion.proxy.ProxyServer;

import java.net.InetAddress;

public class MainApp extends Application {

    private ProxyServer proxyServer;
    private TextArea logArea;
    private MainController mainController;

    @Override
    public void start(Stage primaryStage) {
        try {
            mainController = new MainController();

            BorderPane root = createUIProgrammatically();

            Scene scene = new Scene(root, 1400, 900);

            primaryStage.setTitle("Proxion - Local Debugging Proxy");
            primaryStage.setScene(scene);
            primaryStage.show();

            log("🚀 Starting Proxion application...");
            log("Java version: " + System.getProperty("java.version"));

            new Thread(this::startProxyServer).start();
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Error starting application: " + e.getMessage());
        }
    }

    private BorderPane createUIProgrammatically() {
        BorderPane mainRoot = new BorderPane();

        Label titleLabel = new Label("Proxion - Local Debugging Proxy");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10px;");
        mainRoot.setTop(titleLabel);

        TabPane tabPane = new TabPane();

        // Proxy Monitor Tab with Rewrite Rules sidebar
        Tab proxyTab = new Tab("Proxy Monitor");
        proxyTab.setClosable(false);

        SplitPane proxySplitPane = new SplitPane();
        proxySplitPane.setDividerPositions(0.7);

        // Left side - Traffic monitor
        VBox trafficContent = new VBox(5);
        trafficContent.setPadding(new Insets(5));

        HBox toolbar = new HBox(5);
        toolbar.setPadding(new Insets(5));

        Button clearButton = new Button("Clear");
        Button filterButton = new Button("Filter");
        TextField searchField = new TextField();
        searchField.setPromptText("Search requests...");
        searchField.setPrefWidth(200);

        toolbar.getChildren().addAll(clearButton, filterButton, searchField);

        TableView<pl.proxion.model.HttpTransaction> trafficTable = new TableView<>();

        TableColumn<pl.proxion.model.HttpTransaction, String> methodColumn = new TableColumn<>("Method");
        TableColumn<pl.proxion.model.HttpTransaction, String> urlColumn = new TableColumn<>("URL");
        TableColumn<pl.proxion.model.HttpTransaction, String> statusColumn = new TableColumn<>("Status");
        TableColumn<pl.proxion.model.HttpTransaction, String> modifiedColumn = new TableColumn<>("Modified");

        trafficTable.getColumns().addAll(methodColumn, urlColumn, statusColumn, modifiedColumn);
        trafficTable.setPrefHeight(400);

        SplitPane detailsSplitPane = new SplitPane();
        detailsSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        detailsSplitPane.setDividerPositions(0.5);

        VBox requestBox = new VBox();
        requestBox.setPadding(new Insets(5));
        Label requestLabel = new Label("Request Details");
        TextArea requestDetails = new TextArea();
        requestDetails.setPrefHeight(200);
        requestBox.getChildren().addAll(requestLabel, requestDetails);

        VBox responseBox = new VBox();
        responseBox.setPadding(new Insets(5));
        Label responseLabel = new Label("Response Details");
        TextArea responseDetails = new TextArea();
        responseDetails.setPrefHeight(200);
        responseBox.getChildren().addAll(responseLabel, responseDetails);

        detailsSplitPane.getItems().addAll(requestBox, responseBox);

        trafficContent.getChildren().addAll(toolbar, trafficTable, detailsSplitPane);

        // Right side - Rewrite Rules
        VBox rewriteSidebar = new VBox(10);
        rewriteSidebar.setPadding(new Insets(10));
        rewriteSidebar.setStyle("-fx-background-color: #f5f5f5;");

        Label rewriteLabel = new Label("Rewrite Rules");
        rewriteLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        TableView<pl.proxion.model.RewriteRule> rewriteTable = new TableView<>();
        rewriteTable.setPrefHeight(500);

        HBox rewriteButtons = new HBox(5);
        Button addRewriteRuleButton = new Button("Add");
        Button editRewriteRuleButton = new Button("Edit");
        Button deleteRewriteRuleButton = new Button("Delete");
        rewriteButtons.getChildren().addAll(addRewriteRuleButton, editRewriteRuleButton, deleteRewriteRuleButton);
        rewriteButtons.setSpacing(5);

        rewriteSidebar.getChildren().addAll(rewriteLabel, rewriteTable, rewriteButtons);

        proxySplitPane.getItems().addAll(trafficContent, rewriteSidebar);
        proxyTab.setContent(proxySplitPane);

        // Request Builder Tab
        Tab requestBuilderTab = new Tab("Request Builder");
        requestBuilderTab.setClosable(false);

        SplitPane requestBuilderSplitPane = new SplitPane();
        requestBuilderSplitPane.setDividerPositions(0.25);

        // Left sidebar - Collections
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setStyle("-fx-background-color: #f5f5f5;");

        Label collectionsLabel = new Label("Collections");
        collectionsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        TreeView<pl.proxion.model.RequestItem> collectionsTreeView = new TreeView<>();
        collectionsTreeView.setPrefHeight(400);

        HBox collectionButtons = new HBox(5);
        Button newCollectionButton = new Button("New");
        Button deleteCollectionButton = new Button("Delete");
        Button saveRequestButton = new Button("Save Request");
        collectionButtons.getChildren().addAll(newCollectionButton, deleteCollectionButton, saveRequestButton);
        collectionButtons.setSpacing(5);

        sidebar.getChildren().addAll(collectionsLabel, collectionsTreeView, collectionButtons);

        // Right side - Request Builder
        VBox requestBuilderContent = new VBox(5);
        requestBuilderContent.setPadding(new Insets(10));

        HBox historyBar = new HBox(5);
        Label historyLabel = new Label("History:");
        ComboBox<pl.proxion.model.SavedRequest> historyComboBox = new ComboBox<>();
        historyComboBox.setPrefWidth(400);
        historyBar.getChildren().addAll(historyLabel, historyComboBox);
        historyBar.setSpacing(5);

        HBox requestLine = new HBox(5);
        ComboBox<String> httpMethodComboBox = new ComboBox<>();
        httpMethodComboBox.getItems().addAll("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        httpMethodComboBox.getSelectionModel().selectFirst();

        TextField urlTextField = new TextField();
        urlTextField.setPromptText("Enter URL");
        urlTextField.setPrefWidth(500);

        Button sendButton = new Button("Send");
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        requestLine.getChildren().addAll(httpMethodComboBox, urlTextField, sendButton, progressIndicator);
        requestLine.setSpacing(5);

        // Create auth components
        ComboBox<String> authTypeComboBox = new ComboBox<>();
        authTypeComboBox.getItems().addAll("No Auth", "Bearer Token", "Basic Auth", "API Key");
        authTypeComboBox.getSelectionModel().selectFirst();

        TextField authTokenField = new TextField();
        authTokenField.setPromptText("Enter token");

        TextField authUsernameField = new TextField();
        authUsernameField.setPromptText("Username");

        TextField authPasswordField = new TextField();
        authPasswordField.setPromptText("Password");

        TextField authKeyField = new TextField();
        authKeyField.setPromptText("Header name (e.g., X-API-Key)");

        TextField authValueField = new TextField();
        authValueField.setPromptText("API key value");

        VBox authPanel = createAuthPanel(authTypeComboBox, authTokenField, authUsernameField,
                authPasswordField, authKeyField, authValueField);
        Accordion authAccordion = new Accordion();
        TitledPane authPane = new TitledPane("Authorization", authPanel);
        authAccordion.getPanes().add(authPane);

        // Headers as accordion
        Accordion headersAccordion = new Accordion();
        VBox headersPanel = createHeadersPanel();
        TitledPane headersPane = new TitledPane("Headers", headersPanel);
        headersAccordion.getPanes().add(headersPane);

        TableView<pl.proxion.model.Header> headersTableView = new TableView<>();
        headersTableView.setPrefHeight(150);

        TableColumn<pl.proxion.model.Header, String> nameColumn = new TableColumn<>("Name");
        TableColumn<pl.proxion.model.Header, String> valueColumn = new TableColumn<>("Value");
        headersTableView.getColumns().addAll(nameColumn, valueColumn);

        HBox headerButtons = new HBox(5);
        Button addHeaderButton = new Button("Add Header");
        Button removeHeaderButton = new Button("Remove Header");
        headerButtons.getChildren().addAll(addHeaderButton, removeHeaderButton);

        headersPanel.getChildren().addAll(headersTableView, headerButtons);

        Label bodyLabel = new Label("Request Body");
        TextArea requestBodyTextArea = new TextArea();
        requestBodyTextArea.setPrefHeight(150);
        requestBodyTextArea.setPromptText("Enter request body (for POST/PUT/PATCH)");

        Label responseLabelBuilder = new Label("Response");

        HBox responseStatusBar = new HBox(10);
        Label responseStatusLabel = new Label();
        Label responseTimeLabel = new Label();
        responseTimeLabel.setVisible(false);
        Button copyResponseButton = new Button("Copy Response");
        Button clearRequestButton = new Button("Clear Request");
        responseStatusBar.getChildren().addAll(responseStatusLabel, responseTimeLabel, copyResponseButton, clearRequestButton);
        responseStatusBar.setSpacing(10);

        TabPane responseTabPane = new TabPane();
        Tab responseBodyTab = new Tab("Body");
        TextArea responseTextArea = new TextArea();
        responseTextArea.setPrefHeight(250);
        responseBodyTab.setContent(responseTextArea);

        Tab responseHeadersTab = new Tab("Headers");
        TextArea responseHeadersTextArea = new TextArea();
        responseHeadersTextArea.setPrefHeight(250);
        responseHeadersTab.setContent(responseHeadersTextArea);

        responseTabPane.getTabs().addAll(responseBodyTab, responseHeadersTab);

        requestBuilderContent.getChildren().addAll(
                historyBar, requestLine, authAccordion, headersAccordion,
                bodyLabel, requestBodyTextArea, responseLabelBuilder,
                responseStatusBar, responseTabPane
        );
        requestBuilderContent.setSpacing(5);

        requestBuilderSplitPane.getItems().addAll(sidebar, requestBuilderContent);
        requestBuilderTab.setContent(requestBuilderSplitPane);

        tabPane.getTabs().addAll(proxyTab, requestBuilderTab);
        mainRoot.setCenter(tabPane);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 12px;");
        mainRoot.setBottom(logArea);

        // Initialize MainController fields
        mainController.trafficTable = trafficTable;
        mainController.requestDetails = requestDetails;
        mainController.responseDetails = responseDetails;
        mainController.httpMethodComboBox = httpMethodComboBox;
        mainController.urlTextField = urlTextField;
        mainController.headersTableView = headersTableView;
        mainController.requestBodyTextArea = requestBodyTextArea;
        mainController.responseTextArea = responseTextArea;
        mainController.sendButton = sendButton;
        mainController.progressIndicator = progressIndicator;
        mainController.searchField = searchField;
        mainController.responseHeadersTextArea = responseHeadersTextArea;
        mainController.responseStatusLabel = responseStatusLabel;
        mainController.responseTimeLabel = responseTimeLabel;
        mainController.copyResponseButton = copyResponseButton;
        mainController.clearRequestButton = clearRequestButton;
        mainController.responseTabPane = responseTabPane;
        mainController.responseBodyTab = responseBodyTab;
        mainController.historyComboBox = historyComboBox;
        mainController.saveRequestButton = saveRequestButton;
        mainController.collectionsTreeView = collectionsTreeView;
        mainController.newCollectionButton = newCollectionButton;
        mainController.deleteCollectionButton = deleteCollectionButton;
        mainController.authTypeComboBox = authTypeComboBox;
        mainController.authTokenField = authTokenField;
        mainController.authUsernameField = authUsernameField;
        mainController.authPasswordField = authPasswordField;
        mainController.authKeyField = authKeyField;
        mainController.authValueField = authValueField;
        mainController.authAccordion = authAccordion;
        mainController.headersAccordion = headersAccordion;
        mainController.rewriteTable = rewriteTable;
        mainController.addRewriteRuleButton = addRewriteRuleButton;
        mainController.editRewriteRuleButton = editRewriteRuleButton;
        mainController.deleteRewriteRuleButton = deleteRewriteRuleButton;

        mainController.filteredTrafficData = FXCollections.observableArrayList();
        mainController.mainTabPane = tabPane;
        mainController.proxyTab = proxyTab;
        mainController.requestBuilderTab = requestBuilderTab;

        // Set up event handlers
        sendButton.setOnAction(event -> mainController.handleSendRequest());
        addHeaderButton.setOnAction(event -> mainController.handleAddHeader());
        removeHeaderButton.setOnAction(event -> mainController.handleRemoveHeader());
        clearButton.setOnAction(event -> mainController.handleClearTraffic());
        filterButton.setOnAction(event -> mainController.handleFilterTraffic());
        saveRequestButton.setOnAction(event -> mainController.handleSaveRequest());
        newCollectionButton.setOnAction(event -> mainController.handleNewCollection());
        deleteCollectionButton.setOnAction(event -> mainController.handleDeleteCollection());
        addRewriteRuleButton.setOnAction(event -> mainController.getRewriteController().showAddRewriteRuleDialog());
        editRewriteRuleButton.setOnAction(event -> mainController.getRewriteController().editSelectedRule());
        deleteRewriteRuleButton.setOnAction(event -> mainController.getRewriteController().deleteSelectedRule());

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            mainController.handleSearchTraffic(newValue);
        });

        // Setup auth type change listener
        authTypeComboBox.setOnAction(e -> mainController.handleAuthTypeChange());

        Platform.runLater(() -> {
            mainController.initialize();
        });

        return mainRoot;
    }

    private VBox createAuthPanel(ComboBox<String> authTypeComboBox, TextField authTokenField,
                                 TextField authUsernameField, TextField authPasswordField,
                                 TextField authKeyField, TextField authValueField) {
        VBox authPanel = new VBox(10);

        VBox bearerPanel = new VBox(5);
        Label bearerLabel = new Label("Bearer Token:");
        bearerPanel.getChildren().addAll(bearerLabel, authTokenField);

        VBox basicPanel = new VBox(5);
        Label basicLabel = new Label("Basic Auth:");
        basicPanel.getChildren().addAll(basicLabel, authUsernameField, authPasswordField);

        VBox apiKeyPanel = new VBox(5);
        Label apiKeyLabel = new Label("API Key:");
        apiKeyPanel.getChildren().addAll(apiKeyLabel, authKeyField, authValueField);

        authPanel.getChildren().addAll(authTypeComboBox, bearerPanel, basicPanel, apiKeyPanel);

        return authPanel;
    }

    private VBox createHeadersPanel() {
        VBox headersPanel = new VBox(10);
        return headersPanel;
    }

    private void startProxyServer() {
        try {
            log("🔧 Initializing proxy server on port 8888...");
            proxyServer = new ProxyServer(8888, mainController);

            log("🌐 Starting proxy server...");

            new Thread(() -> {
                try {
                    proxyServer.start();
                    log("✅ Proxy server started successfully on port 8888");

                    Platform.runLater(() -> {
                        showConfigurationInstructions();
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        log("❌ ERROR starting proxy server: " + e.getMessage());
                        e.printStackTrace();
                    });
                }
            }).start();

        } catch (Exception e) {
            Platform.runLater(() -> {
                log("❌ ERROR initializing proxy server: " + e.getMessage());
                e.printStackTrace();
            });
        }
    }

    private void showConfigurationInstructions() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Proxy Configuration");
        alert.setHeaderText("📱 Configure Your Mobile Device");
        alert.setContentText(
                "To monitor mobile app traffic:\n\n" +
                        "1. Connect your mobile device to the same WiFi as this computer\n" +
                        "2. Go to WiFi settings on your mobile device\n" +
                        "3. Configure proxy:\n" +
                        "   - Server: " + getLocalIP() + "\n" +
                        "   - Port: 8888\n" +
                        "4. Save settings and restart your app\n\n" +
                        "Traffic will appear in the Proxy Monitor tab"
        );
        alert.showAndWait();
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "YOUR_COMPUTER_IP";
        }
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
        System.out.println(message);
    }

    private void showErrorDialog(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Application Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() throws Exception {
        log("🛑 Stopping Proxion...");
        if (proxyServer != null) {
            proxyServer.stop();
            log("✅ Proxy server stopped");
        }
        if (mainController != null) {
            mainController.shutdown();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}