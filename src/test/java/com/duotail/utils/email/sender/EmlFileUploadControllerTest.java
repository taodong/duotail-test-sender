package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmlFileUploadControllerTest {

    @Mock
    private EmailSendService emailSendService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EmlFileUploadController(emailSendService))
                .setControllerAdvice(new PermissionExceptionHandler())
                .build();
    }

    @Test
    void uploadEmlReturnsForbiddenWhenPermissionDenied() throws Exception {
        doThrow(new PermissionException("Recipient is not authorized in to: denied@example.com"))
                .when(emailSendService)
                .sendEmailInFile(any(), isNull(), isNull(), isNull(), isNull());

        mockMvc.perform(multipart("/api/eml")
                        .file(testEmlFile())
                        .header("version", "1")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"message\":\"Recipient is not authorized in to: denied@example.com\"}"));
    }

    @Test
    void uploadEmlPassesOptionalAddressOverrides() throws Exception {
        mockMvc.perform(multipart("/api/eml")
                        .file(testEmlFile())
                        .param("from", "Sender <sender@example.com>")
                        .param("to", "to1@example.com", "to2@example.com")
                        .param("cc", "cc@example.com")
                        .param("bcc", "bcc@example.com")
                        .header("version", "1")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(content().string("Email Sent."));

        verify(emailSendService).sendEmailInFile(
                any(),
                eq("Sender <sender@example.com>"),
                eq(java.util.List.of("to1@example.com", "to2@example.com")),
                eq(java.util.List.of("cc@example.com")),
                eq(java.util.List.of("bcc@example.com"))
        );
    }

    @Test
    void uploadEmlRequiresVersionHeader() throws Exception {
        mockMvc.perform(multipart("/api/eml")
                        .file(testEmlFile())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isNotFound());
    }

    private MockMultipartFile testEmlFile() {
        var eml = """
                From: sender@example.com\r
                To: receiver@example.com\r
                Subject: Test\r
                \r
                Body""";
        return new MockMultipartFile("file", "test.eml", "message/rfc822", eml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

