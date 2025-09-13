package pl.proxion;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
            // Tworzymy kontroler
            mainController = new MainController();

            // Tworzymy interfejs programowo zamiast z FXML
            BorderPane root = createUIProgrammatically();

            // Ustawiamy kontroler
            setupController(mainController, root);

            Scene scene = new Scene(root, 1000, 800);

            primaryStage.setTitle("Proxion - Local Debugging Proxy");
            primaryStage.setScene(scene);
            primaryStage.show();

            log("🚀 Starting Proxion application...");
            log("Java version: " + System.getProperty("java.version"));

            // Uruchamiamy proxy w osobnym wątku
            new Thread(this::startProxyServer).start();
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Error starting application: " + e.getMessage());
        }
    }

    private BorderPane createUIProgrammatically() {
        BorderPane mainRoot = new BorderPane();

        // Nagłówek
        Label titleLabel = new Label("Proxion - Local Debugging Proxy");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10px;");
        mainRoot.setTop(titleLabel);

        // Zakładki
        TabPane tabPane = new TabPane();

        // Zakładka Proxy Monitor
        Tab proxyTab = new Tab("Proxy Monitor");
        proxyTab.setClosable(false);

        VBox proxyContent = new VBox(5);

        // Toolbar z przyciskami i wyszukiwaniem
        HBox toolbar = new HBox(5);
        toolbar.setPadding(new javafx.geometry.Insets(5));

        Button clearButton = new Button("Clear");
        Button filterButton = new Button("Filter");
        TextField searchField = new TextField();
        searchField.setPromptText("Search requests...");
        searchField.setPrefWidth(200);

        toolbar.getChildren().addAll(clearButton, filterButton, searchField);

        SplitPane proxySplitPane = new SplitPane();
        TableView<pl.proxion.model.HttpTransaction> trafficTable = new TableView<>();

        TableColumn<pl.proxion.model.HttpTransaction, String> methodColumn = new TableColumn<>("Method");
        TableColumn<pl.proxion.model.HttpTransaction, String> urlColumn = new TableColumn<>("URL");
        TableColumn<pl.proxion.model.HttpTransaction, String> statusColumn = new TableColumn<>("Status");

        trafficTable.getColumns().addAll(methodColumn, urlColumn, statusColumn);

        SplitPane detailsSplitPane = new SplitPane();
        detailsSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);

        VBox requestBox = new VBox();
        TextArea requestDetails = new TextArea();
        requestBox.getChildren().addAll(new Label("Request Details"), requestDetails);

        VBox responseBox = new VBox();
        TextArea responseDetails = new TextArea();
        responseBox.getChildren().addAll(new Label("Response Details"), responseDetails);

        detailsSplitPane.getItems().addAll(requestBox, responseBox);
        proxySplitPane.getItems().addAll(trafficTable, detailsSplitPane);

        proxyContent.getChildren().addAll(toolbar, proxySplitPane);
        proxyTab.setContent(proxyContent);

        // Zakładka Request Builder
        Tab requestBuilderTab = new Tab("Request Builder");
        requestBuilderTab.setClosable(false);

        VBox requestBuilderContent = new VBox(5);
        HBox requestLine = new HBox(5);
        ComboBox<String> httpMethodComboBox = new ComboBox<>();
        httpMethodComboBox.getItems().addAll("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        httpMethodComboBox.getSelectionModel().selectFirst();

        TextField urlTextField = new TextField();
        urlTextField.setPromptText("Enter URL");
        urlTextField.setPrefWidth(300);

        Button sendButton = new Button("Send");
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        requestLine.getChildren().addAll(httpMethodComboBox, urlTextField, sendButton, progressIndicator);
        requestLine.setSpacing(5);

        Label headersLabel = new Label("Headers");
        TableView<pl.proxion.model.Header> headersTableView = new TableView<>();

        TableColumn<pl.proxion.model.Header, String> nameColumn = new TableColumn<>("Name");
        TableColumn<pl.proxion.model.Header, String> valueColumn = new TableColumn<>("Value");
        headersTableView.getColumns().addAll(nameColumn, valueColumn);
        headersTableView.setPrefHeight(150);

        HBox headerButtons = new HBox(5);
        Button addHeaderButton = new Button("Add Header");
        Button removeHeaderButton = new Button("Remove Header");
        headerButtons.getChildren().addAll(addHeaderButton, removeHeaderButton);

        Label bodyLabel = new Label("Request Body");
        TextArea requestBodyTextArea = new TextArea();
        requestBodyTextArea.setPrefHeight(150);
        requestBodyTextArea.setPromptText("Enter request body (for POST/PUT/PATCH)");

        Label responseLabel = new Label("Response");
        TextArea responseTextArea = new TextArea();
        responseTextArea.setPrefHeight(300);

        requestBuilderContent.getChildren().addAll(
                requestLine, headersLabel, headersTableView, headerButtons,
                bodyLabel, requestBodyTextArea, responseLabel, responseTextArea
        );
        requestBuilderContent.setSpacing(5);
        requestBuilderContent.setPadding(new javafx.geometry.Insets(10));
        requestBuilderTab.setContent(requestBuilderContent);

        tabPane.getTabs().addAll(proxyTab, requestBuilderTab);
        mainRoot.setCenter(tabPane);

        // Logi na dole
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        mainRoot.setBottom(logArea);

        // Przekaż referencje do kontrolera
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

        // Dodaj nowe referencje
        mainController.searchField = searchField;
        mainController.filteredTrafficData = FXCollections.observableArrayList();

        // Ustaw obsługę zdarzeń
        sendButton.setOnAction(event -> mainController.handleSendRequest());
        addHeaderButton.setOnAction(event -> mainController.handleAddHeader());
        removeHeaderButton.setOnAction(event -> mainController.handleRemoveHeader());
        clearButton.setOnAction(event -> mainController.handleClearTraffic());
        filterButton.setOnAction(event -> mainController.handleFilterTraffic());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            mainController.handleSearchTraffic(newValue);
        });

        // Inicjalizuj kontroler
        Platform.runLater(() -> {
            mainController.initialize();
        });

        return mainRoot;
    }

    private void setupController(MainController controller, BorderPane root) {
        // Tutaj powiąż elementy UI z kontrolerem
        // To jest uproszczone - w prawdziwej aplikacji potrzebowałbyś
        // @FXML annotations lub ręcznego wiązania
    }

    private void startProxyServer() {
        try {
            log("🔧 Initializing proxy server on port 8888...");
            proxyServer = new ProxyServer(8888, mainController);

            log("🌐 Starting proxy server...");

            // Uruchom serwer w osobnym wątku
            new Thread(() -> {
                try {
                    proxyServer.start();
                    log("✅ Proxy server started successfully on port 8888");

                    // Wyświetl instrukcje konfiguracji
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