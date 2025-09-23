package pl.proxion.model;

import javafx.collections.ObservableList;

public class SavedRequest extends RequestItem {
    private String method;
    private String url;
    private String body;
    private ObservableList<Header> headers;
    private int lastStatusCode;
    private long lastExecuted;

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

    public void setMethod(String method) { this.method = method; }
    public void setUrl(String url) { this.url = url; }
    public void setBody(String body) { this.body = body; }
}