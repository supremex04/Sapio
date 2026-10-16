package com.sapio.visualization;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * The RCSB Protein Data Bank (public, no key): an entry's title, its structure (mmCIF) and its picture. The backend
 * fetches them once and stores them, so the browser loads everything from Sapio.
 */
@Component
class ProteinBank {

    private static final Logger log = LoggerFactory.getLogger(ProteinBank.class);
    private static final Pattern ID = Pattern.compile("^[1-9][A-Z0-9]{3}$");

    /** {@code structure}: mmCIF text; {@code image}: JPEG, or null when RCSB has none. */
    record Entry(String id, String title, byte[] structure, byte[] image) {
    }

    static class NotFound extends Exception {
        NotFound(String message) {
            super(message);
        }
    }

    private final RestClient client;
    private final VisualizationProperties.ProteinBank config;

    ProteinBank(RestClient.Builder builder, VisualizationProperties properties) {
        this.config = properties.proteinBank();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = builder.clone().requestFactory(factory).build();
    }

    static boolean validId(String id) {
        return id != null && ID.matcher(id.toUpperCase(Locale.ROOT)).matches();
    }

    /** @throws NotFound when there is no such entry, or its structure is too large to show */
    Entry fetch(String pdbId) throws NotFound {
        String id = pdbId.toUpperCase(Locale.ROOT);
        if (!validId(id)) {
            throw new NotFound("'" + pdbId + "' is not a Protein Data Bank ID");
        }
        try {
            JsonNode entry = client.get().uri(config.dataUrl() + "/rest/v1/core/entry/{id}", id).retrieve()
                    .body(JsonNode.class);
            String title = entry == null ? id : entry.path("struct").path("title").asString(id);
            byte[] structure = client.get().uri(config.filesUrl() + "/download/{id}.cif", id).retrieve()
                    .body(byte[].class);
            if (structure == null || structure.length == 0) {
                throw new NotFound("The Protein Data Bank has no structure file for " + id);
            }
            if (structure.length > config.maxStructureBytes()) {
                throw new NotFound(id + " is too large to show here");
            }
            return new Entry(id, title, structure, image(id).orElse(null));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))) {
                throw new NotFound("There is no entry " + id + " in the Protein Data Bank");
            }
            throw new IllegalStateException("The Protein Data Bank answered " + e.getStatusCode(), e);
        }
    }

    private Optional<byte[]> image(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        try {
            return Optional.ofNullable(client.get().uri(config.imagesUrl() + "/{hash}/{id}/{id}_assembly-1.jpeg",
                    lower.substring(1, 3), lower, lower).retrieve().body(byte[].class));
        } catch (RestClientException e) {
            log.info("No picture for {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }
}
