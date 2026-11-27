package com.devdocs.ai.preretrieval.vectorsearch.service;

import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.repository.VectorSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private VectorSearchRepository vectorSearchRepository;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    @Test
    void shouldSearchWhenRequestIsValid() {
        List<Double> embedding = validEmbedding();
        VectorSearchRequest request =
                new VectorSearchRequest(5L, embedding, 5);

        when(vectorSearchRepository.search(5L, embedding, 5))
                .thenReturn(List.of());

        assertEquals(
                List.of(),
                vectorSearchService.search(request)
        );

        verify(vectorSearchRepository).search(5L, embedding, 5);
    }

    @Test
    void shouldAcceptOllamaVectorAfterV4Migration() {
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 768; i++) {
            embedding.add(0.1);
        }
        VectorSearchRequest request = new VectorSearchRequest(5L, embedding, 5);

        when(vectorSearchRepository.search(5L, embedding, 5)).thenReturn(List.of());

        assertEquals(List.of(), vectorSearchService.search(request));
        verify(vectorSearchRepository).search(5L, embedding, 5);
    }

    @Test
    void shouldRejectUnsupportedDimensionCount() {
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            embedding.add(0.1);
        }
        VectorSearchRequest request = new VectorSearchRequest(5L, embedding, 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );
        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectNullRequest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(null)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectInvalidVersionId() {
        VectorSearchRequest request =
                new VectorSearchRequest(0L, validEmbedding(), 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectEmbeddingWithWrongDimensions() {
        VectorSearchRequest request =
                new VectorSearchRequest(5L, List.of(0.1, 0.2), 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectEmbeddingWithNullValue() {
        List<Double> embedding = validEmbedding();
        embedding.set(10, null);

        VectorSearchRequest request =
                new VectorSearchRequest(5L, embedding, 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectEmbeddingWithNonFiniteValue() {
        List<Double> embedding = validEmbedding();
        embedding.set(10, Double.NaN);

        VectorSearchRequest request =
                new VectorSearchRequest(5L, embedding, 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldRejectTopKOutsideAllowedRange() {
        VectorSearchRequest request =
                new VectorSearchRequest(5L, validEmbedding(), 101);

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorSearchService.search(request)
        );

        verifyNoInteractions(vectorSearchRepository);
    }

    private List<Double> validEmbedding() {
        List<Double> embedding = new ArrayList<>();

        for (int i = 0; i < 1536; i++) {
            embedding.add(0.1);
        }

        return embedding;
    }
}
