package pl.proxion.model;

import lombok.Data;

@Data
public class RewriteRule {
    private String originalStatusCode;
    private String newStatusCode;
    private String endpointPattern; // Nowe pole dla wzorca endpointu
    private boolean enabled;
    private String description;

    public RewriteRule() {
        this("", "", "", true, "");
    }

    public RewriteRule(String originalStatusCode, String newStatusCode, String endpointPattern, boolean enabled, String description) {
        this.originalStatusCode = originalStatusCode;
        this.newStatusCode = newStatusCode;
        this.endpointPattern = endpointPattern;
        this.enabled = enabled;
        this.description = description;
    }

    public boolean matches(int statusCode, String url) {
        if (!enabled) return false;

        // Sprawdź czy status code pasuje
        boolean statusMatches = false;
        if (originalStatusCode.endsWith("xx")) {
            String prefix = originalStatusCode.substring(0, 1);
            String statusStr = String.valueOf(statusCode);
            statusMatches = statusStr.startsWith(prefix);
        } else {
            statusMatches = originalStatusCode.equals(String.valueOf(statusCode));
        }

        // Sprawdź czy endpoint pasuje (jeśli podany)
        boolean endpointMatches = endpointPattern == null || endpointPattern.isEmpty() ||
                url.contains(endpointPattern);

        return statusMatches && endpointMatches;
    }
}