package pl.proxion.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pl.proxion.model.RewriteRule;

public class RewriteController {

    private ObservableList<RewriteRule> rewriteRules = FXCollections.observableArrayList();
    private TableView<RewriteRule> rewriteTable;

    public void initializeRewriteTab(VBox rewriteTabContent) {
        // Tworzenie tabeli z regułami rewrite
        rewriteTable = new TableView<>();
        rewriteTable.setItems(rewriteRules);
        rewriteTable.setEditable(true);

        // Kolumny tabeli
        TableColumn<RewriteRule, String> originalCol = new TableColumn<>("Original Status");
        originalCol.setCellValueFactory(new PropertyValueFactory<>("originalStatusCode"));
        originalCol.setPrefWidth(100);

        TableColumn<RewriteRule, String> newCol = new TableColumn<>("New Status");
        newCol.setCellValueFactory(new PropertyValueFactory<>("newStatusCode"));
        newCol.setPrefWidth(100);

        TableColumn<RewriteRule, String> endpointCol = new TableColumn<>("Endpoint");
        endpointCol.setCellValueFactory(new PropertyValueFactory<>("endpointPattern"));
        endpointCol.setPrefWidth(150);

        TableColumn<RewriteRule, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        enabledCol.setPrefWidth(80);
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));
        enabledCol.setEditable(true);

        TableColumn<RewriteRule, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(200);

        rewriteTable.getColumns().addAll(originalCol, newCol, endpointCol, enabledCol, descCol);

        // Przyciski zarządzania
        Button addButton = new Button("Add Rule");
        Button editButton = new Button("Edit Rule");
        Button deleteButton = new Button("Delete Rule");
        Button enableAllButton = new Button("Enable All");
        Button disableAllButton = new Button("Disable All");

        addButton.setOnAction(e -> showAddRewriteRuleDialog());
        editButton.setOnAction(e -> editSelectedRule());
        deleteButton.setOnAction(e -> deleteSelectedRule());
        enableAllButton.setOnAction(e -> enableAllRules());
        disableAllButton.setOnAction(e -> disableAllRules());

        HBox buttonBox = new HBox(10, addButton, editButton, deleteButton, enableAllButton, disableAllButton);
        buttonBox.setSpacing(10);
        buttonBox.setPadding(new javafx.geometry.Insets(10, 0, 10, 0));

        // Dodaj przykładowe reguły
        addExampleRules();

        // Ustawienie rozmiaru tabeli
        rewriteTable.setPrefHeight(400);

        // Dodaj elementy do kontenera
        rewriteTabContent.getChildren().addAll(rewriteTable, buttonBox);
    }

    private void addExampleRules() {
        rewriteRules.add(new RewriteRule("404", "200", "/api/users", true, "Zmiana 404 na 200 dla endpointu /api/users"));
        rewriteRules.add(new RewriteRule("500", "200", "", true, "Zmiana 500 na 200 dla wszystkich endpointów"));
        rewriteRules.add(new RewriteRule("4xx", "200", "/api/products", false, "Zmiana wszystkich 4xx na 200 dla /api/products"));
        rewriteRules.add(new RewriteRule("301", "302", "", true, "Zmiana 301 na 302 dla wszystkich endpointów"));
    }

    private void showAddRewriteRuleDialog() {
        Dialog<RewriteRule> dialog = new Dialog<>();
        dialog.setTitle("Add Rewrite Rule");
        dialog.setHeaderText("Create new status code rewrite rule");

        // Pola formularza
        TextField originalField = new TextField();
        originalField.setPromptText("e.g., 404 or 4xx");

        TextField newField = new TextField();
        newField.setPromptText("e.g., 200");

        TextField endpointField = new TextField();
        endpointField.setPromptText("e.g., /api/users (leave empty for all endpoints)");

        CheckBox enabledCheck = new CheckBox("Enabled");
        enabledCheck.setSelected(true);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Rule description");
        descArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Original Status:"), 0, 0);
        grid.add(originalField, 1, 0);
        grid.add(new Label("New Status:"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label("Endpoint Pattern:"), 0, 2);
        grid.add(endpointField, 1, 2);
        grid.add(enabledCheck, 0, 3, 2, 1);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descArea, 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Przyciski
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Konwersja wyniku
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new RewriteRule(
                        originalField.getText(),
                        newField.getText(),
                        endpointField.getText(),
                        enabledCheck.isSelected(),
                        descArea.getText()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(rule -> {
            if (isValidRule(rule)) {
                rewriteRules.add(rule);
            } else {
                showAlert("Invalid Rule", "Please enter valid status codes.");
            }
        });
    }

    private boolean isValidRule(RewriteRule rule) {
        if (rule.getOriginalStatusCode().isEmpty() || rule.getNewStatusCode().isEmpty()) {
            return false;
        }

        // Walidacja formatu status code
        return isValidStatusCodeFormat(rule.getOriginalStatusCode()) &&
                isValidStatusCodeFormat(rule.getNewStatusCode());
    }

    private boolean isValidStatusCodeFormat(String statusCode) {
        if (statusCode.endsWith("xx")) {
            String digit = statusCode.substring(0, 1);
            return "12345".contains(digit);
        }

        try {
            int code = Integer.parseInt(statusCode);
            return code >= 100 && code <= 599;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void editSelectedRule() {
        RewriteRule selected = rewriteTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEditRewriteRuleDialog(selected);
        } else {
            showAlert("No Selection", "Please select a rule to edit.");
        }
    }

    private void showEditRewriteRuleDialog(RewriteRule rule) {
        Dialog<RewriteRule> dialog = new Dialog<>();
        dialog.setTitle("Edit Rewrite Rule");
        dialog.setHeaderText("Edit status code rewrite rule");

        // Pola formularza wypełnione danymi
        TextField originalField = new TextField(rule.getOriginalStatusCode());
        TextField newField = new TextField(rule.getNewStatusCode());
        TextField endpointField = new TextField(rule.getEndpointPattern());
        CheckBox enabledCheck = new CheckBox("Enabled");
        enabledCheck.setSelected(rule.isEnabled());
        TextArea descArea = new TextArea(rule.getDescription());
        descArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Original Status:"), 0, 0);
        grid.add(originalField, 1, 0);
        grid.add(new Label("New Status:"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label("Endpoint Pattern:"), 0, 2);
        grid.add(endpointField, 1, 2);
        grid.add(enabledCheck, 0, 3, 2, 1);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descArea, 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Przyciski
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Konwersja wyniku
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new RewriteRule(
                        originalField.getText(),
                        newField.getText(),
                        endpointField.getText(),
                        enabledCheck.isSelected(),
                        descArea.getText()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updatedRule -> {
            if (isValidRule(updatedRule)) {
                int index = rewriteRules.indexOf(rule);
                if (index >= 0) {
                    rewriteRules.set(index, updatedRule);
                }
            } else {
                showAlert("Invalid Rule", "Please enter valid status codes.");
            }
        });
    }

    private void deleteSelectedRule() {
        RewriteRule selected = rewriteTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            rewriteRules.remove(selected);
        } else {
            showAlert("No Selection", "Please select a rule to delete.");
        }
    }

    private void enableAllRules() {
        rewriteRules.forEach(rule -> rule.setEnabled(true));
        rewriteTable.refresh();
    }

    private void disableAllRules() {
        rewriteRules.forEach(rule -> rule.setEnabled(false));
        rewriteTable.refresh();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public int applyRewriteRules(int originalStatusCode, String url) {
        for (RewriteRule rule : rewriteRules) {
            if (rule.matches(originalStatusCode, url)) {
                try {
                    int newCode = Integer.parseInt(rule.getNewStatusCode());
                    System.out.println("🔄 Rewriting status code: " + originalStatusCode + " → " + newCode +
                            " for URL: " + url);
                    return newCode;
                } catch (NumberFormatException e) {
                    System.err.println("❌ Invalid new status code in rule: " + rule.getNewStatusCode());
                }
            }
        }
        return originalStatusCode;
    }

    public ObservableList<RewriteRule> getRewriteRules() {
        return rewriteRules;
    }
}