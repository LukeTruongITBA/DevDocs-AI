package com.devdocs.ai.ingestdynamic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(IngestionPropertiesConfiguration.class)
                    .withPropertyValues(
                            "app.ingestion.chunk-size=1200",
                            "app.ingestion.chunk-overlap=240",
                            "app.ingestion.max-pages=25",
                            "app.ingestion.request-timeout-seconds=30",
                            "app.ingestion.max-batch-sources=4",
                            "app.ingestion.max-extracted-characters=100000",
                            "app.ingestion.max-chunks-per-source=200"
                    );

    @Test
    void shouldBindAllIngestionPropertiesThroughSpringConfigurationProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            IngestionProperties properties = context.getBean(IngestionProperties.class);
            assertThat(properties.chunkSize()).isEqualTo(1200);
            assertThat(properties.chunkOverlap()).isEqualTo(240);
            assertThat(properties.maxPages()).isEqualTo(25);
            assertThat(properties.requestTimeoutSeconds()).isEqualTo(30);
            assertThat(properties.maxBatchSources()).isEqualTo(4);
            assertThat(properties.maxExtractedCharacters()).isEqualTo(100_000);
            assertThat(properties.maxChunksPerSource()).isEqualTo(200);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestionProperties.class)
    static class IngestionPropertiesConfiguration {
    }
}
