package com.devdocs.ai.document;

import com.devdocs.ai.document.dto.CreateDocumentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocumentController(documentService))
                .build();
    }

    @Test
    void findsWebDocumentBySourceUri() throws Exception {
        String uri = "https://docs.spring.io/spring-framework/reference/core.html";
        when(documentService.findWebBySourceUri(uri)).thenReturn(java.util.List.of(new Document(
                11L, "Core", "WEB", uri, OffsetDateTime.now(), OffsetDateTime.now())));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/documents/web").queryParam("sourceUri", uri))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].id").value(11));
    }

    @Test
    void unknownWebSourceReturnsEmptyList() throws Exception {
        when(documentService.findWebBySourceUri("https://none.example/"))
                .thenReturn(java.util.List.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/documents/web").queryParam("sourceUri", "https://none.example/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(0));
    }

    @Test
    void createRejectingExistingUsesCreateOnlyServicePath()
            throws Exception {
        Document document = new Document(
                10L,
                "Spring Framework",
                "WEB",
                "https://docs.spring.io/spring-framework/reference/core.html",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Spring Framework",
                "WEB",
                document.sourceUri()
        );
        when(documentService.createRejectingExisting(request))
                .thenReturn(document);

        mockMvc.perform(post("/api/documents")
                        .queryParam("rejectExisting", "true")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"Spring Framework",
                                  "sourceType":"WEB",
                                  "sourceUri":"https://docs.spring.io/spring-framework/reference/core.html"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(documentService).createRejectingExisting(request);
    }
}
