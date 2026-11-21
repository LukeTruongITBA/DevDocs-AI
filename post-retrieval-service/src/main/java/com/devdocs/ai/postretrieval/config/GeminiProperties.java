
package com.devdocs.ai.postretrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm.gemini")
public record GeminiProperties(
        String apiUrl,
        String apiKey,
        String model
) {
}
