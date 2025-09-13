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

    public static String sendRequest(String method, String url, List<Header> headers, String body) {
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

            System.out.println("✅ Response received: " + statusCode);

            return String.format("Status: %d\n\nHeaders:\n%s\n\nBody:\n%s",
                    statusCode,
                    response.getAllHeaders(),
                    responseBody);

        } catch (Exception e) {
            System.err.println("❌ Error sending request: " + e.getMessage());
            e.printStackTrace();
            return "❌ Error sending request: " + e.getMessage();
        }
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