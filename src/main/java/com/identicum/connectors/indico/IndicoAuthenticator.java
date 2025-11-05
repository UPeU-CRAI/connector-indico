package com.identicum.connectors.indico;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * Handles authentication details for the Indico Export API.
 */
public class IndicoAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final IndicoConfiguration configuration;

    public IndicoAuthenticator(IndicoConfiguration configuration) {
        this.configuration = configuration;
    }

    public Map<String, String> enrichQueryParameters(String path, Map<String, String> originalParameters) {
        Map<String, String> parameters = new LinkedHashMap<>(originalParameters);
        if (configuration.getAuthStrategy() == IndicoConfiguration.AuthStrategy.API_KEY) {
            parameters.put("apikey", configuration.getApiKey());
            String secret = read(configuration.getApiSecret());
            if (secret != null && !secret.isEmpty()) {
                parameters.putIfAbsent("timestamp", String.valueOf(Instant.now().getEpochSecond()));
                String signature = sign(path, parameters, secret);
                parameters.put("signature", signature);
            }
        }
        return parameters;
    }

    public Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "connector-indico/0.0.1");
        if (configuration.getAuthStrategy() == IndicoConfiguration.AuthStrategy.TOKEN) {
            String token = read(configuration.getApiToken());
            if (token != null && !token.isEmpty()) {
                headers.put("Authorization", "Bearer " + token);
            }
        }
        return headers;
    }

    private String sign(String path, Map<String, String> params, String secret) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>(params);
            String canonical = sorted.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            String payload = path + "?" + canonical;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw ConnectorException.wrap(e);
        }
    }

    private String read(GuardedString guarded) {
        if (guarded == null) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        guarded.access(chs -> builder.append(chs));
        return builder.toString();
    }

    public URI normalize(String serviceAddress) {
        if (serviceAddress.endsWith("/")) {
            return URI.create(serviceAddress.substring(0, serviceAddress.length() - 1));
        }
        return URI.create(serviceAddress);
    }
}
