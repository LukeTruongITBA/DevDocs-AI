
package com.devdocs.ai.ingestdynamic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IngestDynamicApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestDynamicApplication.class, args);
    }
}
