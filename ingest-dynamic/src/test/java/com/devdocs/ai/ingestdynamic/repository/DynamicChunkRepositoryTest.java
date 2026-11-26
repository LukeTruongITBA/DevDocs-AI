
package com.devdocs.ai.ingestdynamic.repository;

import com.devdocs.ai.ingestdynamic.config.EmbeddingProperties;
import com.devdocs.ai.ingestdynamic.dto.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicChunkRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private DynamicChunkRepository repository;

    @BeforeEach
    void setUp() {
        EmbeddingProperties properties = new EmbeddingProperties(
                "https://example.com/embedding",
                "test-key",
                "test-model",
                1536,
                10
        );

        repository = new DynamicChunkRepository(
                jdbcTemplate,
                objectMapper,
                properties
        );
    }

    @Test
    void shouldReplaceChunksAndAssignGlobalIndexes() throws Exception {
        List<TextChunk> chunks = List.of(
                new TextChunk(
                        0, "First page chunk",
                        "https://docs.example.com/a",
                        "Page A", "3.6"
                ),
                new TextChunk(
                        0, "Second page chunk",
                        "https://docs.example.com/b",
                        "Page B", "3.6"
                )
        );

        List<float[]> embeddings = List.of(
                vector(0.1f, 0.2f, 0.3f),
                vector(0.4f, 0.5f, 0.6f)
        );

        when(objectMapper.writeValueAsString(anyMap()))
                .thenReturn("{}");

        when(jdbcTemplate.batchUpdate(
                anyString(),
                any(BatchPreparedStatementSetter.class)
        )).thenReturn(new int[]{1, 1});

        repository.replaceChunks(1L, 2L, chunks, embeddings);

        ArgumentCaptor<BatchPreparedStatementSetter> captor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        verify(jdbcTemplate).batchUpdate(
                anyString(),
                captor.capture()
        );

        BatchPreparedStatementSetter setter = captor.getValue();

        assertEquals(2, setter.getBatchSize());

        PreparedStatement statement = mock(PreparedStatement.class);

        setter.setValues(statement, 0);
        setter.setValues(statement, 1);

        verify(statement).setInt(2, 0);
        verify(statement).setInt(2, 1);

        verify(statement, times(2)).setNull(4, Types.INTEGER);

        ArgumentCaptor<String> vectors = ArgumentCaptor.forClass(String.class);
        verify(statement, times(2)).setString(eq(7), vectors.capture());
        assertTrue(vectors.getAllValues().getFirst().startsWith("[0.1,0.2,0.3,0.0"));
        assertEquals(1536, vectors.getAllValues().getFirst().split(",").length);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>)
                        ArgumentCaptor.forClass(Map.class);
        verify(objectMapper, times(2)).writeValueAsString(metadataCaptor.capture());
        Map<String, Object> metadata = metadataCaptor.getAllValues().getFirst();
        assertTrue(metadata.keySet().containsAll(Set.of(
                "doc_id", "version", "source", "page", "chunk_index", "checksum"
        )));
        assertEquals("https://docs.example.com/a", metadata.get("source"));
        assertEquals("WEB", metadata.get("source_type"));
        assertEquals(Map.of(
                "type", "web",
                "url", "https://docs.example.com/a",
                "title", "Page A"
        ), metadata.get("page"));
    }

    @Test
    void shouldRejectMismatchedEmbeddingCount() {
        List<TextChunk> chunks = List.of(
                new TextChunk(
                        0, "Content",
                        "https://docs.example.com",
                        "Documentation", "3.6"
                )
        );

        List<float[]> embeddings = List.of();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replaceChunks(
                        1L, 2L, chunks, embeddings
                )
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRejectIncorrectEmbeddingDimensions() {
        List<TextChunk> chunks = List.of(
                new TextChunk(
                        0, "Content",
                        "https://docs.example.com",
                        "Documentation", "3.6"
                )
        );

        List<float[]> embeddings = List.of(
                new float[]{0.1f, 0.2f}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replaceChunks(
                        1L, 2L, chunks, embeddings
                )
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRejectNonFiniteEmbeddingValues() {
        List<TextChunk> chunks = List.of(
                new TextChunk(
                        0, "Content",
                        "https://docs.example.com",
                        "Documentation", "3.6"
                )
        );

        List<float[]> embeddings = List.of(
                vector(0.1f, Float.NaN, 0.3f)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replaceChunks(
                        1L, 2L, chunks, embeddings
                )
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRejectMissingWebChunkMetadataBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () ->
                repository.replaceChunks(
                        1L, 2L, Collections.singletonList(null),
                        List.of(vector(0.1f))
                ));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRejectConfiguredDimensionThatDoesNotMatchDatabaseVector() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmbeddingProperties(
                        "https://example.com/embedding", "key", "model", 768, 10
                ));
        verifyNoInteractions(jdbcTemplate);
    }

    private float[] vector(float... initialValues) {
        float[] vector = new float[1536];
        System.arraycopy(initialValues, 0, vector, 0, initialValues.length);
        return vector;
    }
}
