
package com.devdocs.ai.preretrieval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PreRetrievalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PreRetrievalApplication.class, args);
    }
}
