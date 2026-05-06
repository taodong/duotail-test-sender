package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class MailhogService {

    private final RestClient restClient;
    private final String mailhogUrl;

    public MailhogService(RestClient.Builder restClientBuilder,
                          @Value("${app.mailhog.url}") String mailhogUrl) {
        this.mailhogUrl = mailhogUrl;
        this.restClient = restClientBuilder.baseUrl(mailhogUrl).build();
    }

    public MailhogPageResponse getMessages(int start, int limit) {
        try {
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
