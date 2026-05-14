package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogContent;
import com.duotail.utils.email.mailhog.dto.MailhogMessage;
import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import com.duotail.utils.email.mailhog.dto.MailhogPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MailhogControllerTest {

    @Mock
    private MailhogService mailhogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MailhogController(mailhogService))
                .setControllerAdvice(new MailhogExceptionHandler())
                .build();
    }

    @Test
    void getMessagesReturnsOkWithBody() throws Exception {
        var response = new MailhogPageResponse(2, 2, 0, List.of());
        when(mailhogService.getMessages(0, 50)).thenReturn(response);

        mockMvc.perform(get("/api/email/messages").header("version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.start").value(0));
    }

    @Test
    void getMessagesForwardsCustomPaginationParams() throws Exception {
        when(mailhogService.getMessages(5, 10)).thenReturn(new MailhogPageResponse(0, 0, 5, List.of()));

        mockMvc.perform(get("/api/email/messages")
                        .header("version", "1")
                        .param("start", "5")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(mailhogService).getMessages(5, 10);
    }

    @Test
    void getMessagesRequiresVersionHeader() throws Exception {
        mockMvc.perform(get("/api/email/messages"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessagesReturnsNotFoundWhenMailhogUnavailable() throws Exception {
        when(mailhogService.getMessages(anyInt(), anyInt()))
                .thenThrow(new MailhogUnavailableException("MailHog is unavailable at http://localhost:8025: Connection refused", null));

        mockMvc.perform(get("/api/email/messages").header("version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MailHog is unavailable at http://localhost:8025: Connection refused"));
    }

    @Test
    void searchReturnsOkWithBody() throws Exception {
        var response = new MailhogPageResponse(1, 1, 0, List.of());
        when(mailhogService.search("from", "sender@example.com", 0, 50)).thenReturn(response);

        mockMvc.perform(get("/api/email/search")
                        .header("version", "1")
                        .param("kind", "from")
                        .param("query", "sender@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void searchForwardsAllParamsToService() throws Exception {
        when(mailhogService.search("containing", "hello", 2, 5))
                .thenReturn(new MailhogPageResponse(0, 0, 2, List.of()));

        mockMvc.perform(get("/api/email/search")
                        .header("version", "1")
                        .param("kind", "containing")
                        .param("query", "hello")
                        .param("start", "2")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(mailhogService).search("containing", "hello", 2, 5);
    }

    @Test
    void searchRequiresVersionHeader() throws Exception {
        mockMvc.perform(get("/api/email/search").param("kind", "from").param("query", "test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchReturnsBadRequestWhenKindOrQueryMissing() throws Exception {
        mockMvc.perform(get("/api/email/search").header("version", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchReturnsNotFoundWhenMailhogUnavailable() throws Exception {
        when(mailhogService.search(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new MailhogUnavailableException("MailHog is unavailable at http://localhost:8025: timeout", null));

        mockMvc.perform(get("/api/email/search")
                        .header("version", "1")
                        .param("kind", "to")
                        .param("query", "test@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MailHog is unavailable at http://localhost:8025: timeout"));
    }

    @Test
    void getMessageByIdReturnsOkWithBody() throws Exception {
        var msg = singleMessage("abc123", "Hello");
        when(mailhogService.getMessage("abc123")).thenReturn(msg);

        mockMvc.perform(get("/api/email/messages/abc123").header("version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ID").value("abc123"));
    }

    @Test
    void getMessageByIdRequiresVersionHeader() throws Exception {
        mockMvc.perform(get("/api/email/messages/abc123"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessageByIdReturnsNotFoundWhenMessageMissing() throws Exception {
        when(mailhogService.getMessage("missing"))
                .thenThrow(new MailhogMessageNotFoundException("missing"));

        mockMvc.perform(get("/api/email/messages/missing").header("version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MailHog message not found: missing"));
    }

    @Test
    void getMessageByIdReturnsNotFoundWhenMailhogUnavailable() throws Exception {
        when(mailhogService.getMessage(anyString()))
                .thenThrow(new MailhogUnavailableException("MailHog is unavailable at http://localhost:8025: timeout", null));

        mockMvc.perform(get("/api/email/messages/abc123").header("version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MailHog is unavailable at http://localhost:8025: timeout"));
    }

    @Test
    void deleteMessageByIdReturnsNoContent() throws Exception {
        doNothing().when(mailhogService).deleteMessage("abc123");

        mockMvc.perform(delete("/api/email/messages/abc123").header("version", "1"))
                .andExpect(status().isNoContent());

        verify(mailhogService).deleteMessage("abc123");
    }

    @Test
    void deleteMessageByIdRequiresVersionHeader() throws Exception {
        mockMvc.perform(delete("/api/email/messages/abc123"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMessageByIdReturnsNotFoundWhenMessageMissing() throws Exception {
        doThrow(new MailhogMessageNotFoundException("missing"))
                .when(mailhogService).deleteMessage("missing");

        mockMvc.perform(delete("/api/email/messages/missing").header("version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MailHog message not found: missing"));
    }

    private MailhogMessage singleMessage(String id, String subject) {
        return new MailhogMessage(
                id,
                new MailhogPath("sender", "example.com"),
                List.of(new MailhogPath("recipient", "example.com")),
                new MailhogContent(Map.of("Subject", List.of(subject)), 100),
                "2025-01-01T10:00:00Z"
        );
    }
}
