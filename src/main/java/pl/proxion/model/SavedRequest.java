package pl.proxion.model;

import javafx.collections.ObservableList;

public class SavedRequest extends RequestItem {
    private String method;
    private String url;
    private String body;
    private ObservableList<Header> headers;
    private int lastStatusCode;
    private long lastExecuted;
    private String authType;
    private String authToken;
    private String authUsername;
    private String authPassword;
    private String authKey;
    private String authValue;

    public SavedRequest(String name, String method, String url, String body) {
        this(name, method, url, body, null, -1, System.currentTimeMillis());
    }

    public SavedRequest(String name, String method, String url, String body,
                        ObservableList<Header> headers, int lastStatusCode, long lastExecuted) {
        super(name);
        this.method = method;
        this.url = url;
        this.body = body;
        this.headers = headers;
        this.lastStatusCode = lastStatusCode;
        this.lastExecuted = lastExecuted;
    }

    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public String getBody() { return body; }
    public ObservableList<Header> getHeaders() { return headers; }
    public int getLastStatusCode() { return lastStatusCode; }
    public long getLastExecuted() { return lastExecuted; }
    public String getAuthType() { return authType; }
    public String getAuthToken() { return authToken; }
    public String getAuthUsername() { return authUsername; }
    public String getAuthPassword() { return authPassword; }
    public String getAuthKey() { return authKey; }
    public String getAuthValue() { return authValue; }

    public void setMethod(String method) { this.method = method; }
    public void setUrl(String url) { this.url = url; }
    public void setBody(String body) { this.body = body; }
    public void setAuthType(String authType) { this.authType = authType; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public void setAuthUsername(String authUsername) { this.authUsername = authUsername; }
    public void setAuthPassword(String authPassword) { this.authPassword = authPassword; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }
    public void setAuthValue(String authValue) { this.authValue = authValue; }

    public void addHeader(Header header) {
        if (headers == null) {
            headers = javafx.collections.FXCollections.observableArrayList();
        }
        headers.add(header);
    }
}