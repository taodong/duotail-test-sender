package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
public class MailhogService {

    private final RestClient restClient;
    private final String mailhogUrl;

    public MailhogService(RestClient.Builder restClientBuilder,
                          @Value("${app.mailhog.url}") String mailhogUrl) {
        this.mailhogUrl = mailhogUrl;
        // MailHog returns Content-Type: text/json, which Jackson doesn't handle by default
        var textJsonConverter = new JacksonJsonHttpMessageConverter();
        textJsonConverter.setSupportedMediaTypes(List.of(MediaType.valueOf("text/json")));
        this.restClient = restClientBuilder
                .baseUrl(mailhogUrl)
                .configureMessageConverters(converters -> converters
                        .registerDefaults()
                        .addCustomConverter(textJsonConverter))
                .build();
    }

    public MailhogPageResponse getMessages(int start, int limit) {
        try {
            LOG.info("Fetching messages from MailHog with start={}, limit={}", start, limit);
            return restClient.get()
                    .uri("/api/v2/messages?start={start}&limit={limit}", start, limit)
                    .retrieve()
                    .body(MailhogPageResponse.class);
        } catch (RestClientException e) {
            logMailhogError(mailhogUrl, e);
            throw new MailhogUnavailableException(
                    "MailHog is unavailable at " + mailhogUrl + ": " + e.getMessage(), e);
        }
    }

    public MailhogPageResponse search(String kind, String query, int start, int limit) {
        try {
            LOG.info("Searching MailHog with kind='{}', query='{}', start={}, limit={}", kind, query, start, limit);
            return restClient.get()
                    .uri("/api/v2/search?kind={kind}&query={query}&start={start}&limit={limit}",
                            kind, query, start, limit)
                    .retrieve()
                    .body(MailhogPageResponse.class);
        } catch (RestClientException e) {
            logMailhogError(mailhogUrl, e);
            throw new MailhogUnavailableException(
                    "MailHog is unavailable at " + mailhogUrl + ": " + e.getMessage(), e);
        }
    }

    private void logMailhogError(String message, Throwable e) {
        LOG.warn("MailHog error at {}: {}", mailhogUrl, message, e);
    }
}
