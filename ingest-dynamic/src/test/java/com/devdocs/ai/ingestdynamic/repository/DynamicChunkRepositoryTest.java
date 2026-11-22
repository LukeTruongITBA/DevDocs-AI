
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
                3,
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
                new float[]{0.1f, 0.2f, 0.3f},
                new float[]{0.4f, 0.5f, 0.6f}
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

        verify(statement).setString(7, "[0.1,0.2,0.3]");
        verify(statement).setString(7, "[0.4,0.5,0.6]");
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
                new float[]{0.1f, Float.NaN, 0.3f}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.replaceChunks(
                        1L, 2L, chunks, embeddings
                )
        );

        verifyNoInteractions(jdbcTemplate);
    }
}
