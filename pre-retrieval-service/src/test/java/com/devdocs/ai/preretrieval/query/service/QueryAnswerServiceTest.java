
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryAnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QueryAnswerServiceTest {

    private QueryPipelineService queryPipelineService;
    private QueryAnswerService queryAnswerService;
    private MockRestServiceServer mockServer;

    private final String originalQuery =
            "How does Spring Boot auto-configuration work?";

    private final String normalizedQuery =
            "how does spring boot auto-configuration work?";

    private final Long versionId = 6L;
    private final Integer topK = 5;

    private StitchedContext stitchedContext;

    @BeforeEach
    void setUp() {
        queryPipelineService = mock(QueryPipelineService.class);

        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        queryAnswerService = new QueryAnswerService(
                queryPipelineService,
                builder,
                "http://post-retrieval-service:8085"
        );

        stitchedContext = new StitchedContext(
                5L,
                versionId,
                "Spring Boot",
                "4.2.0",
                "https://docs.spring.io/spring-boot/",
                0,
                0,
                "Spring Boot auto-configuration content",
                List.of()
        );
    }

    @Test
    void answerShouldGenerateAnswerAndReturnCitations() {
        QueryPipelineRequest request = new QueryPipelineRequest(
                originalQuery,
                versionId,
                topK
        );

        QueryPipelineResponse retrieval = new QueryPipelineResponse(
                originalQuery,
                normalizedQuery,
                List.of("auto configuration"),
                1536,
                versionId,
                topK,
                List.of(),
                List.of(),
                List.of(stitchedContext)
        );

        when(queryPipelineService.retrieve(request))
                .thenReturn(retrieval);

        mockServer.expect(requestTo(
                        "http://post-retrieval-service:8085/api/answers"
                ))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "question": "How does Spring Boot auto-configuration work?",
                          "retrievedContext": [
                            "Spring Boot auto-configuration content"
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "answer": "Spring Boot uses auto-configuration to configure beans based on dependencies and application settings."
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        QueryAnswerResponse response =
                queryAnswerService.answer(request);

        assertNotNull(response);
        assertEquals(originalQuery, response.originalQuery());
        assertEquals(normalizedQuery, response.normalizedQuery());
        assertTrue(response.answer().contains("auto-configuration"));
        assertEquals(versionId, response.versionId());
        assertEquals(topK, response.topK());
        assertEquals(List.of(stitchedContext), response.citations());

        mockServer.verify();

        verify(queryPipelineService).retrieve(request);
        verifyNoMoreInteractions(queryPipelineService);
    }

    @Test
    void answerShouldNotCallPostRetrievalWhenNoContextExists() {
        QueryPipelineRequest request = new QueryPipelineRequest(
                originalQuery,
                versionId,
                topK
        );

        QueryPipelineResponse retrieval = new QueryPipelineResponse(
                originalQuery,
                normalizedQuery,
                List.of(),
                1536,
                versionId,
                topK,
                List.of(),
                List.of(),
                List.of()
        );

        when(queryPipelineService.retrieve(request))
                .thenReturn(retrieval);

        QueryAnswerResponse response =
                queryAnswerService.answer(request);

        assertNotNull(response);
        assertNotNull(response.answer());
        assertTrue(response.answer().contains("does not contain enough information"));
        assertTrue(response.citations().isEmpty());

        mockServer.verify();

        verify(queryPipelineService).retrieve(request);
        verifyNoMoreInteractions(queryPipelineService);
    }

    @Test
    void answerShouldPropagatePostRetrievalServerError() {
        QueryPipelineRequest request = new QueryPipelineRequest(
                originalQuery,
                versionId,
                topK
        );

        QueryPipelineResponse retrieval = new QueryPipelineResponse(
                originalQuery,
                normalizedQuery,
                List.of(),
                1536,
                versionId,
                topK,
                List.of(),
                List.of(),
                List.of(stitchedContext)
        );

        when(queryPipelineService.retrieve(request))
                .thenReturn(retrieval);

        mockServer.expect(requestTo(
                        "http://post-retrieval-service:8085/api/answers"
                ))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        org.springframework.web.server.ResponseStatusException exception =
                assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> queryAnswerService.answer(request)
                );

        assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        mockServer.verify();

        verify(queryPipelineService).retrieve(request);
        verifyNoMoreInteractions(queryPipelineService);
    }
}


