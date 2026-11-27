
package com.devdocs.ai.ingestdynamic.controller;

import com.devdocs.ai.ingestdynamic.exception.GlobalExceptionHandler;
import com.devdocs.ai.ingestdynamic.service.DynamicIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DynamicIngestionControllerTest {

    @Mock
    private DynamicIngestionService ingestionService;

    private MockMvc mockMvc;

    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        DynamicIngestionController controller =
                new DynamicIngestionController(ingestionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn200WhenIngestionSucceeds() throws Exception {

        DynamicIngestionService.IngestionResult result =
                new DynamicIngestionService.IngestionResult(
                        1L,
                        2L,
                        "3.5",
                        "abc123",
                        10,
                        120,
                        false
                );

        when(ingestionService.ingest(
                "https://docs.spring.io/spring-boot/",
                "3.5"
        )).thenReturn(result);

        mockMvc.perform(post("/api/ingestion/dynamic")
                        .contentType("application/json")
                        .content("""
                                {
                                    "websiteUrl": "https://docs.spring.io/spring-boot/",
                                    "explicitVersion": "3.5"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.versionId").value(2))
                .andExpect(jsonPath("$.versionLabel").value("3.5"))
                .andExpect(jsonPath("$.checksum").value("abc123"))
                .andExpect(jsonPath("$.pagesCrawled").value(10))
                .andExpect(jsonPath("$.chunksStored").value(120))
                .andExpect(jsonPath("$.skipped").value(false));

        verify(ingestionService).ingest(
                "https://docs.spring.io/spring-boot/",
                "3.5"
        );
    }

    @Test
    void shouldReturn400WhenWebsiteUrlIsBlank() throws Exception {

        mockMvc.perform(post("/api/ingestion/dynamic")
                        .contentType("application/json")
                        .content("""
                                {
                                    "websiteUrl": " ",
                                    "explicitVersion": "3.5"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {

        mockMvc.perform(post("/api/ingestion/dynamic")
                        .contentType("application/json")
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void shouldReturn502WhenWebsiteIngestionFails()
            throws Exception {

        when(ingestionService.ingest(
                "https://docs.spring.io/spring-boot/",
                "3.5"
        )).thenThrow(new IOException("Connection failed"));

        mockMvc.perform(post("/api/ingestion/dynamic")
                        .contentType("application/json")
                        .content("""
                                {
                                    "websiteUrl": "https://docs.spring.io/spring-boot/",
                                    "explicitVersion": "3.5"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));

    }

    @Test
    void shouldReturn422WhenIngestionCannotBeCompleted()
            throws Exception {

        when(ingestionService.ingest(
                "https://docs.spring.io/spring-boot/",
                "3.5"
        )).thenThrow(
                new IllegalStateException("Website crawler returned no pages.")
        );

        mockMvc.perform(post("/api/ingestion/dynamic")
                        .contentType("application/json")
                        .content("""
                                {
                                    "websiteUrl": "https://docs.spring.io/spring-boot/",
                                    "explicitVersion": "3.5"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message")
                        .value("Website crawler returned no pages."));
    }

    @Test
    void shouldReturnPerSourceBatchOutcomes() throws Exception {
        DynamicIngestionService.BatchIngestionResult result =
                new DynamicIngestionService.BatchIngestionResult(
                        2,
                        1,
                        1,
                        List.of(
                                new DynamicIngestionService.SourceIngestionResult(
                                        "https://docs.example/page",
                                        true,
                                        null,
                                        null
                                ),
                                new DynamicIngestionService.SourceIngestionResult(
                                        "https://files.example/broken.pdf",
                                        false,
                                        null,
                                        "HTTP 503"
                                )
                        )
                );
        when(ingestionService.ingestBatch(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(result);

        mockMvc.perform(post("/api/ingestion/dynamic/batch")
                        .contentType("application/json")
                        .content("""
                                {"sources":[
                                  {"websiteUrl":"https://docs.example/page"},
                                  {"websiteUrl":"https://files.example/broken.pdf"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[1].succeeded").value(false))
                .andExpect(jsonPath("$.results[1].error").value("HTTP 503"));

        verify(ingestionService).ingestBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldRejectAnEmptyBatchBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/ingestion/dynamic/batch")
                        .contentType("application/json")
                        .content("{\"sources\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ingestionService);
    }
}
