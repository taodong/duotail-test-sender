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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BounceEmailControllerTest {

    @Mock
    private BounceEmailService bounceEmailService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new BounceEmailController(bounceEmailService))
                .setControllerAdvice(new PermissionExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void sendBounceDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/bounce")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validRequest())))
                .andExpect(status().isOk());

        verify(bounceEmailService).sendBounce(any());
    }

    @Test
    void sendBounceReturnsForbiddenWhenPermissionDenied() throws Exception {
        doThrow(new PermissionException("Sender is not authorized: MAILER-DAEMON@mail.duotail.test"))
                .when(bounceEmailService)
                .sendBounce(any());

        mockMvc.perform(post("/api/bounce")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sender is not authorized: MAILER-DAEMON@mail.duotail.test"));
    }

    @Test
    void sendBounceRequiresVersionHeader() throws Exception {
        mockMvc.perform(post("/api/bounce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendBounceReturnsBadRequestForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/bounce")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bounceEmailService);
    }

    @Test
    void sendBounceReturnsBadRequestWhenStatusCodeClassMismatchesType() throws Exception {
        var request = validRequest();
        request.setStatusCode("4.2.2"); // soft code with HARD type

        mockMvc.perform(post("/api/bounce")
                        .header("version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bounceEmailService);
    }

    private BounceRequest validRequest() {
        var request = new BounceRequest();
        request.setOriginalFrom("sender@example.com");
        request.setOriginalTo("failed@example.com");
        request.setOriginalSubject("Your order confirmation");
        request.setBounceType(BounceType.HARD);
        return request;
    }
}
