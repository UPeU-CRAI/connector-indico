package com.identicum.connectors.indico;

import java.util.Objects;
import java.util.Optional;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Configuration bean for the Indico connector.
 */
public class IndicoConfiguration implements Configuration {

    public enum AuthStrategy {
        TOKEN,
        API_KEY
    }

    private String serviceAddress;
    private AuthStrategy authStrategy = AuthStrategy.TOKEN;
    private GuardedString apiToken;
    private String apiKey;
    private GuardedString apiSecret;
    private Integer connectTimeoutMs = 10_000;
    private Integer readTimeoutMs = 30_000;
    private boolean trustAllCertificates;
    private Integer retryMax = 3;
    private Long retryBackoffBaseMs = 1_000L;
    private Long defaultEventId;
    private Integer pageSize = 200;
    private ConnectorMessages connectorMessages;

    @ConfigurationProperty(order = 10,
            displayMessageKey = "indico.config.serviceAddress.display",
            helpMessageKey = "indico.config.serviceAddress.help",
            required = true)
    public String getServiceAddress() {
        return serviceAddress;
    }

    public void setServiceAddress(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    @ConfigurationProperty(order = 20,
            displayMessageKey = "indico.config.authStrategy.display",
            helpMessageKey = "indico.config.authStrategy.help",
            required = true)
    public AuthStrategy getAuthStrategy() {
        return authStrategy;
    }

    public void setAuthStrategy(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    @ConfigurationProperty(order = 30,
            confidential = true,
            displayMessageKey = "indico.config.apiToken.display",
            helpMessageKey = "indico.config.apiToken.help")
    public GuardedString getApiToken() {
        return apiToken;
    }

    public void setApiToken(GuardedString apiToken) {
        this.apiToken = apiToken;
    }

    @ConfigurationProperty(order = 40,
            displayMessageKey = "indico.config.apiKey.display",
            helpMessageKey = "indico.config.apiKey.help")
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @ConfigurationProperty(order = 50,
            confidential = true,
            displayMessageKey = "indico.config.apiSecret.display",
            helpMessageKey = "indico.config.apiSecret.help")
    public GuardedString getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(GuardedString apiSecret) {
        this.apiSecret = apiSecret;
    }

    @ConfigurationProperty(order = 60,
            displayMessageKey = "indico.config.connectTimeout.display",
            helpMessageKey = "indico.config.connectTimeout.help")
    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    @ConfigurationProperty(order = 70,
            displayMessageKey = "indico.config.readTimeout.display",
            helpMessageKey = "indico.config.readTimeout.help")
    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    @ConfigurationProperty(order = 80,
            displayMessageKey = "indico.config.trustAll.display",
            helpMessageKey = "indico.config.trustAll.help")
    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    @ConfigurationProperty(order = 90,
            displayMessageKey = "indico.config.retryMax.display",
            helpMessageKey = "indico.config.retryMax.help")
    public Integer getRetryMax() {
        return retryMax;
    }

    public void setRetryMax(Integer retryMax) {
        this.retryMax = retryMax;
    }

    @ConfigurationProperty(order = 100,
            displayMessageKey = "indico.config.retryBackoff.display",
            helpMessageKey = "indico.config.retryBackoff.help")
    public Long getRetryBackoffBaseMs() {
        return retryBackoffBaseMs;
    }

    public void setRetryBackoffBaseMs(Long retryBackoffBaseMs) {
        this.retryBackoffBaseMs = retryBackoffBaseMs;
    }

    @ConfigurationProperty(order = 110,
            displayMessageKey = "indico.config.defaultEvent.display",
            helpMessageKey = "indico.config.defaultEvent.help")
    public Long getDefaultEventId() {
        return defaultEventId;
    }

    public void setDefaultEventId(Long defaultEventId) {
        this.defaultEventId = defaultEventId;
    }

    @ConfigurationProperty(order = 120,
            displayMessageKey = "indico.config.pageSize.display",
            helpMessageKey = "indico.config.pageSize.help")
    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public void validate() {
        Objects.requireNonNull(serviceAddress, "serviceAddress must not be null");
        if (serviceAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("serviceAddress must not be blank");
        }
        Objects.requireNonNull(authStrategy, "authStrategy must not be null");

        switch (authStrategy) {
            case TOKEN:
                if (apiToken == null) {
                    throw new IllegalArgumentException("apiToken is required when authStrategy is TOKEN");
                }
                break;
            case API_KEY:
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("apiKey is required when authStrategy is API_KEY");
                }
                if (apiSecret == null) {
                    throw new IllegalArgumentException("apiSecret is required when authStrategy is API_KEY");
                }
                break;
            default:
                throw new IllegalStateException("Unsupported auth strategy: " + authStrategy);
        }

        if (pageSize != null && pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be a positive integer");
        }
        if (retryMax != null && retryMax < 0) {
            throw new IllegalArgumentException("retryMax must be zero or positive");
        }
        if (retryBackoffBaseMs != null && retryBackoffBaseMs < 0) {
            throw new IllegalArgumentException("retryBackoffBaseMs must be zero or positive");
        }
    }

    @Override
    public void setConnectorMessages(ConnectorMessages connectorMessages) {
        this.connectorMessages = connectorMessages;
    }

    public ConnectorMessages getConnectorMessages() {
        return connectorMessages;
    }

    public Optional<Long> optionalDefaultEventId() {
        return Optional.ofNullable(defaultEventId);
    }
}
