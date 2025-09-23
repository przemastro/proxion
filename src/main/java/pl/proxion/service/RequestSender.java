package pl.proxion.service;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import pl.proxion.model.Header;

import java.util.List;

public class RequestSender {

    public static class RequestResult {
        private int statusCode;
        private String headers;
        private String body;
        private String contentType;
        private long contentLength;

        public RequestResult(int statusCode, String headers, String body, String contentType, long contentLength) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        // Getters
        public int getStatusCode() { return statusCode; }
        public String getHeaders() { return headers; }
        public String getBody() { return body; }
        public String getContentType() { return contentType; }
        public long getContentLength() { return contentLength; }
    }

    public static RequestResult sendRequest(String method, String url, List<Header> headers, String body) {
        System.out.println("📤 Sending " + method + " request to: " + url);

        if (headers != null && !headers.isEmpty()) {
            System.out.println("📋 Headers:");
            for (Header header : headers) {
                System.out.println("  " + header.getName() + ": " + header.getValue());
            }
        }

        if (body != null && !body.trim().isEmpty()) {
            System.out.println("📝 Body: " + body);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpRequestBase request = createRequest(method, url);

            // Dodaj nagłówki
            for (Header header : headers) {
                if (header.getName() != null && !header.getName().trim().isEmpty()) {
                    request.addHeader(header.getName(), header.getValue());
                }
            }

            // Dodaj body
            if (body != null && !body.trim().isEmpty() &&
                    (method.equalsIgnoreCase("POST") ||
                            method.equalsIgnoreCase("PUT") ||
                            method.equalsIgnoreCase("PATCH"))) {

                StringEntity entity = new StringEntity(body);
                if (request instanceof HttpEntityEnclosingRequestBase) {
                    ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
                }
            }

            // Wykonaj request
            System.out.println("⏳ Executing request...");
            HttpResponse response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            String responseHeaders = formatHeaders(response.getAllHeaders());
            String contentType = getHeaderValue(response, "Content-Type");
            long contentLength = response.getEntity().getContentLength();

            System.out.println("✅ Response received: " + statusCode);

            return new RequestResult(statusCode, responseHeaders, responseBody, contentType, contentLength);

        } catch (Exception e) {
            System.err.println("❌ Error sending request: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error sending request: " + e.getMessage(), e);
        }
    }

    private static String formatHeaders(org.apache.http.Header[] headers) {
        StringBuilder sb = new StringBuilder();
        for (org.apache.http.Header header : headers) {
            sb.append(header.getName()).append(": ").append(header.getValue()).append("\n");
        }
        return sb.toString();
    }

    private static String getHeaderValue(HttpResponse response, String headerName) {
        org.apache.http.Header header = response.getFirstHeader(headerName);
        return header != null ? header.getValue() : null;
    }

    private static HttpRequestBase createRequest(String method, String url) {
        switch (method.toUpperCase()) {
            case "POST": return new HttpPost(url);
            case "PUT": return new HttpPut(url);
            case "DELETE": return new HttpDelete(url);
            case "PATCH": return new HttpPatch(url);
            case "HEAD": return new HttpHead(url);
            case "OPTIONS": return new HttpOptions(url);
            default: return new HttpGet(url);
        }
    }
}