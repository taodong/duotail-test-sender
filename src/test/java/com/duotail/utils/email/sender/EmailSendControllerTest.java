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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmailSendControllerTest {

    @Mock
    private EmailSendService emailSendService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EmailSendController(emailSendService))
                .setControllerAdvice(new PermissionExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void sendEmailReturnsForbiddenWhenPermissionDenied() throws Exception {
        doThrow(new PermissionException("Sender is not authorized: denied@example.com"))
                .when(emailSendService)
                .sendEmail(any());

        mockMvc.perform(post("/api/email")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sender is not authorized: denied@example.com"));
    }

    @Test
    void sendEmailRequiresVersionHeader() throws Exception {
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendEmailReturnsBadRequestForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/email")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailSendService);
    }

    private EmailRequest validRequest() {
        var request = new EmailRequest();
        request.setFrom("sender@example.com");
        request.setTo(java.util.Set.of("receiver@example.com"));
        request.setSubject("Subject");
        request.setContent("<p>Hello</p>");
        return request;
    }
}

