
package com.devdocs.ai.preretrieval.query.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SynonymDictionary {

    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "ioc", List.of("inversion of control"),
            "di", List.of("dependency injection"),
            "orm", List.of("object relational mapping"),
            "mvc", List.of("model view controller"),
            "rest api", List.of("representational state transfer api"),
            "jpa", List.of("java persistence api", "jakarta persistence")
    );

    public Map<String, List<String>> getSynonyms() {
        return SYNONYMS;
    }
}
