package com.identicum.connectors.indico.http;

import com.identicum.connectors.indico.IndicoAuthenticator;
import com.identicum.connectors.indico.IndicoConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;

/**
 * Thin HTTP client wrapper adding retry and error translation.
 */
public class IndicoHttpClient {

    private static final Log LOG = Log.getLog(IndicoHttpClient.class);

    private final HttpClient httpClient;
    private final URI baseUri;
    private final IndicoAuthenticator authenticator;
    private final IndicoConfiguration configuration;

    public IndicoHttpClient(IndicoConfiguration configuration, IndicoAuthenticator authenticator) {
        this.configuration = configuration;
        this.authenticator = authenticator;
        this.baseUri = authenticator.normalize(configuration.getServiceAddress());
        this.httpClient = buildClient(configuration);
    }

    public String get(String path, Map<String, String> queryParameters) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        Map<String, String> enriched = authenticator.enrichQueryParameters(normalizedPath, queryParameters);
        URI uri = buildUri(normalizedPath, enriched);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .timeout(Duration.ofMillis(configuration.getReadTimeoutMs()))
                .headers(flatten(authenticator.defaultHeaders()));

        int attempts = 0;
        int maxAttempts = Math.max(1, configuration.getRetryMax() + 1);
        long backoffBase = configuration.getRetryBackoffBaseMs();

        while (true) {
            attempts++;
            try {
                LOG.ok("GET {0}", uri);
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (isSuccess(response.statusCode())) {
                    return response.body();
                }
                if (shouldRetry(response.statusCode(), attempts, maxAttempts)) {
                    sleepBackoff(backoffBase, attempts);
                    continue;
                }
                handleErrorStatus(uri, response.statusCode(), response.body());
            } catch (RetryableException e) {
                throw e;
            } catch (UnknownUidException e) {
                throw e;
            } catch (ConnectorSecurityException e) {
                throw e;
            } catch (InvalidAttributeValueException e) {
                throw e;
            } catch (IOException e) {
                if (attempts < maxAttempts) {
                    sleepBackoff(backoffBase, attempts);
                    continue;
                }
                throw new ConnectorIOException("I/O error calling Indico: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectorIOException("Interrupted while calling Indico", e);
            }
        }
    }

    private void handleErrorStatus(URI uri, int statusCode, String body) {
        String message = String.format("HTTP %d calling %s: %s", statusCode, uri, body);
        switch (statusCode) {
            case 400:
            case 422:
                throw new InvalidAttributeValueException(message);
            case 401:
            case 403:
                throw new ConnectorSecurityException(message);
            case 404:
                throw new UnknownUidException(message);
            case 429:
                throw RetryableException.wrap(message, (Throwable) null);
            default:
                if (statusCode >= 500 && statusCode < 600) {
                    throw new ConnectorIOException(message, null);
                }
                throw new ConnectorIOException(message, null);
        }
    }

    private boolean shouldRetry(int statusCode, int attempts, int maxAttempts) {
        if (attempts >= maxAttempts) {
            return false;
        }
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    private boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private void sleepBackoff(long baseMs, int attempts) {
        if (baseMs <= 0L) {
            return;
        }
        long delay = (long) (baseMs * Math.pow(2, attempts - 1));
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorIOException("Interrupted during retry backoff", e);
        }
    }

    private URI buildUri(String path, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        String base = baseUri.toString();
        return URI.create(base + path + (query.isEmpty() ? "" : "?" + query));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String[] flatten(Map<String, String> headers) {
        return headers.entrySet().stream()
                .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }

    private HttpClient buildClient(IndicoConfiguration configuration) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configuration.getConnectTimeoutMs()));
        if (configuration.isTrustAllCertificates()) {
            builder.sslContext(createTrustAllContext());
        }
        return builder.build();
    }

    private SSLContext createTrustAllContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new ConnectorIOException("Cannot create SSL context", e);
        }
    }
}
