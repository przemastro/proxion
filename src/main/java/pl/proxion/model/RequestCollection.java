package pl.proxion.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class RequestCollection extends RequestItem {
    private ObservableList<SavedRequest> requests;

    public RequestCollection(String name) {
        super(name);
        this.requests = FXCollections.observableArrayList();
    }

    public void addRequest(SavedRequest request) {
        requests.add(request);
    }

    public void removeRequest(SavedRequest request) {
        requests.remove(request);
    }

    public ObservableList<SavedRequest> getRequests() {
        return requests;
    }
}