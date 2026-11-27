package com.devdocs.ai.ingestdynamic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(EmbeddingPropertiesConfiguration.class)
                    .withPropertyValues(
                            "app.embedding.api-url=https://example.test/embed",
                            "app.embedding.api-key=test-key",
                            "app.embedding.provider=gemini",
                            "app.embedding.model=models/gemini-embedding-001",
                            "app.embedding.dimensions=1536",
                            "app.embedding.batch-size=64",
                            "app.embedding.max-attempts=2",
                            "app.embedding.initial-backoff-millis=100",
                            "app.embedding.max-backoff-millis=1000",
                            "app.embedding.jitter-ratio=0.25"
                    );

    @Test
    void shouldBindEmbeddingRetryPolicyWithoutChangingVectorContract() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            EmbeddingProperties properties =
                    context.getBean(EmbeddingProperties.class);
            assertThat(properties.model())
                    .isEqualTo("models/gemini-embedding-001");
            assertThat(properties.dimensions()).isEqualTo(1536);
            assertThat(properties.batchSize()).isEqualTo(64);
            assertThat(properties.maxAttempts()).isEqualTo(2);
            assertThat(properties.initialBackoffMillis()).isEqualTo(100);
            assertThat(properties.maxBackoffMillis()).isEqualTo(1000);
            assertThat(properties.jitterRatio()).isEqualTo(0.25);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmbeddingProperties.class)
    static class EmbeddingPropertiesConfiguration {
    }
}
