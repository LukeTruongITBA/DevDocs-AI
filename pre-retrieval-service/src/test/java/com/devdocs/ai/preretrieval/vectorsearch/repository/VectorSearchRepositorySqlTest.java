package com.devdocs.ai.preretrieval.vectorsearch.repository;

import com.devdocs.ai.preretrieval.filter.MetadataFilter;
import com.devdocs.ai.preretrieval.filter.MetadataFilterParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorSearchRepositorySqlTest {

    @Test
    void standardFlowKeepsVersionScopeOnly() {
        List<Object> parameters = new ArrayList<>();

        String sql = VectorSearchRepository.buildSql(EmbeddingColumn.OLLAMA, 9L, null, parameters);

        assertTrue(sql.contains("WHERE c.embedding_ollama IS NOT NULL\n          AND c.version_id = ?"));
        assertFalse(sql.contains("AND ("));
        assertEquals(List.of(9L), parameters);
    }

    @Test
    void filterIsAppendedAsBoundGroupWithOptionalVersion() {
        MetadataFilter filter = MetadataFilterParser.parse(
                List.of("source_type:eq:WEB", "document_id:in:12,13"), "OR");
        List<Object> parameters = new ArrayList<>();

        String sql = VectorSearchRepository.buildSql(EmbeddingColumn.GEMINI, null, filter, parameters);

        assertTrue(sql.contains("WHERE c.embedding IS NOT NULL\n"
                + "          AND (d.source_type = ? OR d.id IN (?, ?))"));
        assertFalse(sql.contains("c.version_id = ?"));
        assertEquals(List.of("WEB", 12L, 13L), parameters);
        assertTrue(sql.contains("ORDER BY c.embedding <=> q.embedding"));
    }

    @Test
    void versionAndFilterAreAnded() {
        MetadataFilter filter = MetadataFilterParser.parse(List.of("page:gte:2"), null);
        List<Object> parameters = new ArrayList<>();

        String sql = VectorSearchRepository.buildSql(EmbeddingColumn.OLLAMA, 9L, filter, parameters);

        assertTrue(sql.contains("AND c.version_id = ?\n          AND (COALESCE(c.page_number, "
                + "(c.metadata->'page'->>'pdf_page_number')::int) >= ?)"));
        assertEquals(List.of(9L, 2), parameters);
    }
}
