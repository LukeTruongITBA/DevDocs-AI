package com.devdocs.ai.preretrieval.filter;

import com.devdocs.ai.preretrieval.query.controller.QueryPipelineController;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.query.service.QueryAnswerService;
import com.devdocs.ai.preretrieval.query.service.QueryPipelineService;
import com.devdocs.ai.preretrieval.vectorsearch.controller.VectorSearchController;
import com.devdocs.ai.preretrieval.vectorsearch.dto.VectorSearchRequest;
import com.devdocs.ai.preretrieval.vectorsearch.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level behaviour of URL metadata filters on both retrieval endpoints. */
class FilterEndpointsTest {

    private QueryPipelineService pipelineService;
    private VectorSearchService vectorSearchService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        pipelineService = mock(QueryPipelineService.class);
        vectorSearchService = mock(VectorSearchService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new QueryPipelineController(pipelineService, mock(QueryAnswerService.class)),
                        new VectorSearchController(vectorSearchService))
                .setControllerAdvice(new FilterExceptionHandler())
                .build();
    }

    @Test
    void invalidFilterReturns400WithValidFields() throws Exception {
        mvc.perform(post("/api/query/retrieve")
                        .param("filter", "content:eq:x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"versionId\":9,\"topK\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid metadata filter"))
                .andExpect(jsonPath("$.details[0]").value("filter #1: unknown field 'content'"))
                .andExpect(jsonPath("$.validFields.source_type.operators[0]").value("eq"))
                .andExpect(jsonPath("$.validFields.page.value").value("positive integer"));

        verifyNoInteractions(pipelineService);
    }

    @Test
    void nullFilterUsesStandardFlow() throws Exception {
        when(pipelineService.retrieve(any(QueryPipelineRequest.class))).thenReturn(response());

        mvc.perform(post("/api/query/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"versionId\":9,\"topK\":5}"))
                .andExpect(status().isOk());

        verify(pipelineService).retrieve(any(QueryPipelineRequest.class));
        verify(pipelineService, never()).retrieve(any(QueryPipelineRequest.class), any(MetadataFilter.class));
    }

    @Test
    void validOrFilterIsPassedToPipeline() throws Exception {
        when(pipelineService.retrieve(any(QueryPipelineRequest.class), any(MetadataFilter.class)))
                .thenReturn(response());

        mvc.perform(post("/api/query/retrieve")
                        .param("filter", "source_type:eq:STATIC", "document_id:in:12,13")
                        .param("filterMode", "OR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MetadataFilter> captor = ArgumentCaptor.forClass(MetadataFilter.class);
        verify(pipelineService).retrieve(any(QueryPipelineRequest.class), captor.capture());
        assertEquals(MetadataFilter.Mode.OR, captor.getValue().mode());
        assertEquals(List.of("source_type eq STATIC", "document_id in 12,13"), captor.getValue().describe());
    }

    @Test
    void singleFilterWithCommasIsNotSplit() throws Exception {
        when(pipelineService.retrieve(any(QueryPipelineRequest.class), any(MetadataFilter.class)))
                .thenReturn(response());

        mvc.perform(post("/api/query/retrieve")
                        .param("filter", "document_id:in:12,13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MetadataFilter> captor = ArgumentCaptor.forClass(MetadataFilter.class);
        verify(pipelineService).retrieve(any(QueryPipelineRequest.class), captor.capture());
        assertEquals(List.of("document_id in 12,13"), captor.getValue().describe());
    }

    @Test
    void singleVectorSearchFilterValueWithCommaIsNotSplit() throws Exception {
        when(vectorSearchService.search(any())).thenReturn(List.of());

        mvc.perform(post("/api/retrieval/search")
                        .param("filter", "source:eq:https://x.org/a,b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embedding\":[0.1],\"topK\":5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorSearchService).search(captor.capture());
        assertEquals(List.of("source eq https://x.org/a,b"), captor.getValue().filter().describe());
    }

    @Test
    void vectorSearchIgnoresFilterSuppliedInJsonBody() throws Exception {
        when(vectorSearchService.search(any())).thenReturn(List.of());

        mvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":9,\"embedding\":[0.1],\"topK\":5,"
                                + "\"filter\":{\"mode\":\"OR\",\"conditions\":[]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorSearchService).search(captor.capture());
        assertNull(captor.getValue().filter());
    }

    @Test
    void vectorSearchTakesFilterFromUrl() throws Exception {
        when(vectorSearchService.search(any())).thenReturn(List.of());

        mvc.perform(post("/api/retrieval/search")
                        .param("filter", "page:gte:2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embedding\":[0.1],\"topK\":5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorSearchService).search(captor.capture());
        assertEquals(List.of("page gte 2"), captor.getValue().filter().describe());
    }

    private static QueryPipelineResponse response() {
        return new QueryPipelineResponse("q", "q", List.of(), 768, 9L, 5,
                List.of(), List.of(), List.of());
    }
}
