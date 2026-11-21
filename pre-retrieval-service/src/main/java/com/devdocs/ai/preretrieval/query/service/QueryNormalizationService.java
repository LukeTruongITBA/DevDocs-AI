
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.query.dto.QueryNormalizationResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class QueryNormalizationService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SynonymDictionary synonymDictionary;

    public QueryNormalizationService(SynonymDictionary synonymDictionary) {
        this.synonymDictionary = synonymDictionary;
    }

    public QueryNormalizationResponse normalize(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        String normalizedQuery = Normalizer.normalize(
                query.trim(),
                Normalizer.Form.NFC
        );

        normalizedQuery = WHITESPACE
                .matcher(normalizedQuery)
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);

        List<String> synonymTerms = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry
                : synonymDictionary.getSynonyms().entrySet()) {

            String keyword = entry.getKey();

            Pattern keywordPattern = Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])"
                            + Pattern.quote(keyword)
                            + "(?![\\p{L}\\p{N}_])"
            );

            if (keywordPattern.matcher(normalizedQuery).find()) {
                for (String synonym : entry.getValue()) {
                    if (!synonymTerms.contains(synonym)) {
                        synonymTerms.add(synonym);
                    }
                }
            }
        }

        return new QueryNormalizationResponse(
                query,
                normalizedQuery,
                List.copyOf(synonymTerms)
        );
    }
}
