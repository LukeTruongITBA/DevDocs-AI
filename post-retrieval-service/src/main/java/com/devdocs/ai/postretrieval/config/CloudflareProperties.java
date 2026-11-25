package com.devdocs.ai.postretrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm.cloudflare")
public record CloudflareProperties(
        String accountId,
        String apiToken,
        String model
) {
    public String apiUrl() {
        return "https://api.cloudflare.com/client/v4/accounts/"
                + accountId
                + "/ai/v1/chat/completions";
    }
}