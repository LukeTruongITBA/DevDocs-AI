
package com.devdocs.ai.postretrieval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PostRetrievalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostRetrievalApplication.class, args);
    }
}
