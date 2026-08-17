package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogMessage;
import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

    public MailhogMessage getMessage(String id) {
        try {
            LOG.info("Fetching message from MailHog with id={}", id);
            var msg = restClient.get()
                    .uri("/api/v1/messages/{id}", id)
                    .retrieve()
                    .body(MailhogMessage.class);
            if (msg == null) {
                throw new MailhogMessageNotFoundException(id);
            }
            return msg;
        } catch (HttpClientErrorException.NotFound e) {
            throw new MailhogMessageNotFoundException(id);
        } catch (RestClientException e) {
            logMailhogError(mailhogUrl, e);
            throw new MailhogUnavailableException(
                    "MailHog is unavailable at " + mailhogUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the raw RFC-822 message as MailHog serves it from
     * {@code /api/v1/messages/{id}/download} (Content-Type {@code message/rfc822}).
     * Returned as bytes so the charset is decided by the MIME parser rather than the HTTP client.
     */
    public byte[] getMessageEml(String id) {
        try {
            LOG.info("Downloading raw eml from MailHog with id={}", id);
            var eml = restClient.get()
                    .uri("/api/v1/messages/{id}/download", id)
                    .retrieve()
                    .body(byte[].class);
            if (eml == null || eml.length == 0) {
                throw new MailhogMessageNotFoundException(id);
            }
            return eml;
        } catch (HttpClientErrorException.NotFound e) {
            throw new MailhogMessageNotFoundException(id);
        } catch (RestClientException e) {
            // For an unknown id MailHog's /download closes the connection without writing any
            // response, so a bad id arrives here as a transport error rather than a 404. Probe the
            // metadata endpoint to tell "unknown id" apart from "MailHog is down" — reporting an
            // outage for what is really a mistyped id is the confusion this feature exists to end.
            getMessage(id);
            logMailhogError(mailhogUrl, e);
            throw new MailhogUnavailableException(
                    "MailHog is unavailable at " + mailhogUrl + ": " + e.getMessage(), e);
        }
    }

    public void deleteMessage(String id) {
        try {
            LOG.info("Deleting message from MailHog with id={}", id);
            restClient.delete()
                    .uri("/api/v1/messages/{id}", id)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new MailhogMessageNotFoundException(id);
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
