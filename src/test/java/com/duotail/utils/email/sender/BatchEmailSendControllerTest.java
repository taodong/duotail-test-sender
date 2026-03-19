package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BatchEmailSendControllerTest {

    @Mock
    private EmailSendService emailSendService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchEmailSendController(emailSendService))
                .setControllerAdvice(new PermissionExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void sendBatchReturnsForbiddenWhenBatchSizeExceedsPermissionLimit() throws Exception {
        doThrow(new PermissionException("Batch size 3 exceeds allowed limit of 2 emails."))
                .when(emailSendService)
                .sendEmails(any());

        mockMvc.perform(post("/api/mails")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validBatchRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Batch size 3 exceeds allowed limit of 2 emails."));
    }

    @Test
    void sendBatchRequiresVersionHeader() throws Exception {
        mockMvc.perform(post("/api/mails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validBatchRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendBatchReturnsBadRequestForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/emails")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailSendService);
    }

    private BatchEmailRequest validBatchRequest() {
        var emailRequest = new EmailRequest();
        emailRequest.setFrom("sender@example.com");
        emailRequest.setTo(java.util.Set.of("receiver@example.com"));
        emailRequest.setSubject("Batch");
        emailRequest.setContent("<p>Hello</p>");

        var batchRequest = new BatchEmailRequest();
        batchRequest.setEmails(List.of(emailRequest));
        return batchRequest;
    }
}

