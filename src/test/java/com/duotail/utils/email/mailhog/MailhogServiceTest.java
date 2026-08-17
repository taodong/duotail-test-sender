package com.duotail.utils.email.mailhog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MailhogServiceTest {

    private static final String BASE_URL = "http://localhost:8025";

    private MockRestServiceServer mockServer;
    private MailhogService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new MailhogService(builder, BASE_URL);
    }

    @Test
    void getMessagesForwardsParamsAndMapsResponse() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/messages?start=0&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageJson("Test Subject", "sender", "example.com", "recipient", "example.com"),
                        MediaType.APPLICATION_JSON));

        var result = service.getMessages(0, 50);

        assertEquals(1, result.total());
        assertEquals(1, result.count());
        assertEquals(0, result.start());
        assertEquals("abc123", result.items().getFirst().id());
        assertEquals("sender@example.com", result.items().getFirst().from().address());
        assertEquals("Test Subject", result.items().getFirst().content().headers().get("Subject").getFirst());
        mockServer.verify();
    }

    @Test
    void getMessagesForwardsCustomPaginationParams() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/messages?start=10&limit=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(emptyPageJson(), MediaType.APPLICATION_JSON));

        service.getMessages(10, 5);

        mockServer.verify();
    }

    @Test
    void getMessagesMapsTextJsonResponse() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/messages?start=0&limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageJson("Text Json Subject", "sender", "example.com", "recipient", "example.com"),
                        MediaType.valueOf("text/json")));

        var result = service.getMessages(0, 1);

        assertEquals(1, result.total());
        assertEquals("Text Json Subject", result.items().getFirst().content().headers().get("Subject").getFirst());
        mockServer.verify();
    }

    @Test
    void getMessagesThrowsMailhogUnavailableExceptionOnError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/messages?start=0&limit=50"))
                .andRespond(withResourceNotFound());

        var ex = assertThrows(MailhogUnavailableException.class, () -> service.getMessages(0, 50));

        assertTrue(ex.getMessage().contains(BASE_URL));
        mockServer.verify();
    }

    @Test
    void searchForwardsAllParamsAndMapsResponse() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/search?kind=from&query=test&start=0&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageJson("Found It", "sender", "example.com", "recipient", "example.com"),
                        MediaType.APPLICATION_JSON));

        var result = service.search("from", "test", 0, 50);

        assertEquals(1, result.total());
        assertEquals("Found It", result.items().getFirst().content().headers().get("Subject").getFirst());
        mockServer.verify();
    }

    @Test
    void searchThrowsMailhogUnavailableExceptionOnError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v2/search?kind=to&query=test&start=0&limit=50"))
                .andRespond(withResourceNotFound());

        var ex = assertThrows(MailhogUnavailableException.class, () -> service.search("to", "test", 0, 50));

        assertTrue(ex.getMessage().contains(BASE_URL));
        mockServer.verify();
    }

    @Test
    void getMessageFetchesByIdAndMapsResponse() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(messageJson("abc123", "Test Subject", "sender", "example.com", "recipient", "example.com"),
                        MediaType.APPLICATION_JSON));

        var result = service.getMessage("abc123");

        assertEquals("abc123", result.id());
        assertEquals("sender@example.com", result.from().address());
        assertEquals("Test Subject", result.content().headers().get("Subject").getFirst());
        mockServer.verify();
    }

    @Test
    void getMessageThrowsMailhogMessageNotFoundExceptionOn404() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.getMessage("missing"));

        assertTrue(ex.getMessage().contains("missing"));
        mockServer.verify();
    }

    @Test
    void getMessageThrowsMailhogMessageNotFoundExceptionOnNullBody() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.getMessage("abc123"));

        assertTrue(ex.getMessage().contains("abc123"));
        mockServer.verify();
    }

    @Test
    void getMessageThrowsMailhogUnavailableExceptionOnServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        var ex = assertThrows(MailhogUnavailableException.class, () -> service.getMessage("abc123"));

        assertTrue(ex.getMessage().contains(BASE_URL));
        mockServer.verify();
    }

    @Test
    void getMessageEmlDownloadsRawBytes() {
        var eml = "Subject: Raw\r\n\r\nbody";
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(eml, MediaType.valueOf("message/rfc822")));

        var result = service.getMessageEml("abc123");

        assertArrayEquals(eml.getBytes(StandardCharsets.UTF_8), result);
        mockServer.verify();
    }

    @Test
    void getMessageEmlThrowsMailhogMessageNotFoundExceptionOn404() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/missing/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.getMessageEml("missing"));

        assertTrue(ex.getMessage().contains("missing"));
        mockServer.verify();
    }

    @Test
    void getMessageEmlThrowsMailhogMessageNotFoundExceptionOnEmptyBody() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.valueOf("message/rfc822")));

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.getMessageEml("abc123"));

        assertTrue(ex.getMessage().contains("abc123"));
        mockServer.verify();
    }

    @Test
    void getMessageEmlThrowsMailhogUnavailableExceptionWhenMessageExistsButDownloadFails() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        // The probe confirms the message exists, so this is a genuine MailHog problem
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(messageJson("abc123", "Test Subject", "sender", "example.com",
                        "recipient", "example.com"), MediaType.APPLICATION_JSON));

        var ex = assertThrows(MailhogUnavailableException.class, () -> service.getMessageEml("abc123"));

        assertTrue(ex.getMessage().contains(BASE_URL));
        mockServer.verify();
    }

    /**
     * MailHog answers /download for an unknown id by closing the connection with no response at
     * all, which reaches the client as a transport error rather than a 404. The id must still be
     * reported as not found rather than as a MailHog outage.
     */
    @Test
    void getMessageEmlReportsNotFoundWhenDownloadFailsAndMessageDoesNotExist() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/missing/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.getMessageEml("missing"));

        assertTrue(ex.getMessage().contains("missing"));
        mockServer.verify();
    }

    @Test
    void deleteMessageCallsCorrectEndpoint() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> service.deleteMessage("abc123"));
        mockServer.verify();
    }

    @Test
    void deleteMessageThrowsMailhogMessageNotFoundExceptionOn404() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/missing"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withResourceNotFound());

        var ex = assertThrows(MailhogMessageNotFoundException.class, () -> service.deleteMessage("missing"));

        assertTrue(ex.getMessage().contains("missing"));
        mockServer.verify();
    }

    @Test
    void deleteMessageThrowsMailhogUnavailableExceptionOnServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/messages/abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        var ex = assertThrows(MailhogUnavailableException.class, () -> service.deleteMessage("abc123"));

        assertTrue(ex.getMessage().contains(BASE_URL));
        mockServer.verify();
    }

    private String pageJson(String subject, String fromMailbox, String fromDomain,
                            String toMailbox, String toDomain) {
        return """
                {
                  "total": 1,
                  "count": 1,
                  "start": 0,
                  "items": [{
                    "ID": "abc123",
                    "From": { "Mailbox": "%s", "Domain": "%s" },
                    "To":   [{ "Mailbox": "%s", "Domain": "%s" }],
                    "Content": {
                      "Headers": { "Subject": ["%s"] },
                      "Size": 100
                    },
                    "Created": "2025-01-01T10:00:00Z"
                  }]
                }
                """.formatted(fromMailbox, fromDomain, toMailbox, toDomain, subject);
    }

    private String emptyPageJson() {
        return """
                { "total": 0, "count": 0, "start": 10, "items": [] }
                """;
    }

    private String messageJson(String id, String subject, String fromMailbox, String fromDomain,
                               String toMailbox, String toDomain) {
        return """
                {
                  "ID": "%s",
                  "From": { "Mailbox": "%s", "Domain": "%s" },
                  "To":   [{ "Mailbox": "%s", "Domain": "%s" }],
                  "Content": {
                    "Headers": { "Subject": ["%s"] },
                    "Size": 100
                  },
                  "Created": "2025-01-01T10:00:00Z"
                }
                """.formatted(id, fromMailbox, fromDomain, toMailbox, toDomain, subject);
    }
}
