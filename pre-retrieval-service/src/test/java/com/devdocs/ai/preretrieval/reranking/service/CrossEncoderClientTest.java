package com.devdocs.ai.preretrieval.reranking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CrossEncoderClientTest {

    private MockRestServiceServer server;
    private CrossEncoderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reranker");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CrossEncoderClient(builder.build());
    }

    @Test
    void mapsTeiScoresBackToInputOrder() {
        server.expect(requestTo("http://reranker/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"query\":\"q\",\"texts\":[\"a\",\"b\"],\"raw_scores\":false}"))
                .andRespond(withSuccess("[{\"index\":1,\"score\":0.9},{\"index\":0,\"score\":0.2}]",
                        MediaType.APPLICATION_JSON));

        assertArrayEquals(new double[]{0.2, 0.9}, client.score("q", List.of("a", "b")));
        server.verify();
    }

    @Test
    void serverErrorBecomesUnavailable() {
        server.expect(requestTo("http://reranker/rerank")).andRespond(withServerError());

        assertThrows(CrossEncoderClient.RerankerUnavailableException.class,
                () -> client.score("q", List.of("a")));
    }

    @Test
    void wrongScoreCountBecomesUnavailable() {
        server.expect(requestTo("http://reranker/rerank"))
                .andRespond(withSuccess("[{\"index\":0,\"score\":0.2}]", MediaType.APPLICATION_JSON));

        assertThrows(CrossEncoderClient.RerankerUnavailableException.class,
                () -> client.score("q", List.of("a", "b")));
    }

    @Test
    void duplicateIndexBecomesUnavailable() {
        server.expect(requestTo("http://reranker/rerank"))
                .andRespond(withSuccess("[{\"index\":0,\"score\":0.2},{\"index\":0,\"score\":0.3}]",
                        MediaType.APPLICATION_JSON));

        assertThrows(CrossEncoderClient.RerankerUnavailableException.class,
                () -> client.score("q", List.of("a", "b")));
    }

    @Test
    void nonJsonBecomesUnavailable() {
        server.expect(requestTo("http://reranker/rerank"))
                .andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        assertThrows(CrossEncoderClient.RerankerUnavailableException.class,
                () -> client.score("q", List.of("a")));
    }
}
