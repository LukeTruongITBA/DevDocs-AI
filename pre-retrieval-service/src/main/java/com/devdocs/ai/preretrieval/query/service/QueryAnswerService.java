
package com.devdocs.ai.preretrieval.query.service;

import com.devdocs.ai.preretrieval.contextstitching.dto.StitchedContext;
import com.devdocs.ai.preretrieval.query.dto.AnswerGenerationRequest;
import com.devdocs.ai.preretrieval.query.dto.AnswerGenerationResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryAnswerResponse;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineRequest;
import com.devdocs.ai.preretrieval.query.dto.QueryPipelineResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class QueryAnswerService {

    private final QueryPipelineService queryPipelineService;
    private final RestClient restClient;
    private final String postRetrievalBaseUrl;

    public QueryAnswerService(
            QueryPipelineService queryPipelineService,
            RestClient.Builder restClientBuilder,
            @Value("${app.post-retrieval.base-url}") String postRetrievalBaseUrl
    ) {
        this.queryPipelineService = queryPipelineService;
        this.restClient = restClientBuilder.build();
        this.postRetrievalBaseUrl = postRetrievalBaseUrl;
    }

    public QueryAnswerResponse answer(QueryPipelineRequest request) {

        QueryPipelineResponse retrieval =
                queryPipelineService.retrieve(request);

        List<StitchedContext> contexts = retrieval.stitchedContexts();

        List<String> retrievedContext = contexts.stream()
                .map(StitchedContext::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();

        if (retrievedContext.isEmpty()) {
            return new QueryAnswerResponse(
                    retrieval.originalQuery(),
                    retrieval.normalizedQuery(),
                    "The retrieved documentation does not contain enough information to answer this question.",
                    retrieval.versionId(),
                    retrieval.topK(),
                    contexts
            );
        }

        AnswerGenerationRequest answerRequest =
                new AnswerGenerationRequest(
                        retrieval.originalQuery(),
                        retrievedContext
                );

        AnswerGenerationResponse answerResponse = restClient.post()
                .uri(postRetrievalBaseUrl + "/api/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(answerRequest)
                .retrieve()
                .body(AnswerGenerationResponse.class);

        if (answerResponse == null
                || answerResponse.answer() == null
                || answerResponse.answer().isBlank()) {
            throw new IllegalStateException(
                    "Post-retrieval service returned an empty answer."
            );
        }

        return new QueryAnswerResponse(
                retrieval.originalQuery(),
                retrieval.normalizedQuery(),
                answerResponse.answer(),
                retrieval.versionId(),
                retrieval.topK(),
                contexts
        );
    }
}
