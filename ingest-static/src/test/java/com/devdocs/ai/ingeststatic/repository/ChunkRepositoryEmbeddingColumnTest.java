package com.devdocs.ai.ingeststatic.repository;

import com.devdocs.ai.ingeststatic.chunk.ChunkEmbedding;
import com.devdocs.ai.ingeststatic.chunk.TextChunk;
import com.devdocs.ai.ingeststatic.config.EmbeddingProperties;
import com.devdocs.ai.ingeststatic.dto.DocumentVersionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verifies V4 column routing: Ollama writes embedding_ollama, Gemini writes embedding. */
class ChunkRepositoryEmbeddingColumnTest {

    private static final String OLLAMA_MODEL = "nomic-embed-text:137m-v1.5-fp16";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final DocumentVersionInfo version = new DocumentVersionInfo(
            5L, 4L, "1.0", "a".repeat(64), "guide.pdf", "STATIC");

    private final TextChunk chunk = new TextChunk(0, 1, "content", sha256("content"));

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ChunkRepository repository(String provider, int dimensions) {
        return new ChunkRepository(jdbcTemplate, new ObjectMapper(),
                new EmbeddingProperties("http://localhost:11434/api/embed", "",
                        "ollama".equals(provider) ? OLLAMA_MODEL : "models/gemini-embedding-001",
                        dimensions, 8, provider));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ollamaInsertTargetsEmbeddingOllamaWithModelProvenance() throws Exception {
        ChunkRepository repository = repository("ollama", 768);

        repository.insertAllWithEmbeddings(version, List.of(chunk),
                List.of(new ChunkEmbedding(0, vector(768))), "guide.pdf");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<Object>> setter =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sql.capture(), anyList(), anyInt(), setter.capture());
        assertTrue(sql.getValue().contains("embedding_ollama_model"));
        assertFalse(sql.getValue().contains("metadata,\n                embedding\n"));

        ArgumentCaptor<List<Object>> rows = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), rows.capture(), anyInt(), any());
        PreparedStatement statement = mock(PreparedStatement.class);
        setter.getValue().setValues(statement, rows.getValue().getFirst());
        verify(statement).setString(8, OLLAMA_MODEL);
    }

    @Test
    void ollamaMissingEmbeddingLookupUsesOllamaColumn() {
        ChunkRepository repository = repository("ollama", 768);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L)))
                .thenReturn(Collections.emptyList());

        repository.findByVersionId(5L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(5L));
        assertTrue(sql.getValue().contains("embedding_ollama IS NULL"));
    }

    @Test
    void geminiInsertKeepsLegacyEmbeddingColumn() {
        ChunkRepository repository = repository("gemini", 1536);

        repository.insertAllWithEmbeddings(version, List.of(chunk),
                List.of(new ChunkEmbedding(0, vector(1536))), "guide.pdf");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sql.capture(), anyList(), anyInt(), any());
        assertFalse(sql.getValue().contains("embedding_ollama"));
    }

    @Test
    void ollamaRejectsGeminiSizedVectors() {
        ChunkRepository repository = repository("ollama", 768);

        assertThrows(IllegalArgumentException.class,
                () -> repository.insertAllWithEmbeddings(version, List.of(chunk),
                        List.of(new ChunkEmbedding(0, vector(1536))), "guide.pdf"));
        verifyNoInteractions(jdbcTemplate);
    }

    private static List<Double> vector(int dimensions) {
        List<Double> values = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            values.add(0.01);
        }
        return values;
    }
}
