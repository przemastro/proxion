package pl.proxion.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class HttpTransaction {
    private String id;
    private LocalDateTime timestamp;
    private String method;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private String responseHeaders;
    private String responseBody;
    private int statusCode;
    private int originalStatusCode; // Nowe pole - oryginalny status code
    private boolean modified;
    private boolean intercepted;
    private String modifiedResponse;
    private boolean isEncrypted;

    public HttpTransaction() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.modified = false;
        this.intercepted = false;
        this.statusCode = 0;
        this.originalStatusCode = 0;
        this.isEncrypted = false;
    }
}