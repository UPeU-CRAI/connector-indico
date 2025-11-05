package com.identicum.connectors.indico.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.identicum.connectors.indico.IndicoAuthenticator;
import com.identicum.connectors.indico.IndicoConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class IndicoHttpClientTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .configureStaticDsl(true)
            .build();

    private IndicoHttpClient client;

    @BeforeEach
    void setup(WireMockRuntimeInfo runtimeInfo) {
        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress(runtimeInfo.getHttpBaseUrl());
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        configuration.setRetryMax(2);
        configuration.setRetryBackoffBaseMs(1L);
        configuration.setConnectTimeoutMs(1_000);
        configuration.setReadTimeoutMs(1_000);
        configuration.validate();
        IndicoAuthenticator authenticator = new IndicoAuthenticator(configuration);
        client = new IndicoHttpClient(configuration, authenticator);
    }

    @Test
    void getSuccess() {
        stubFor(get(urlEqualTo("/export/categories.json?limit=1"))
                .withHeader("Authorization", equalTo("Bearer token"))
                .willReturn(ok().withBody("{}")));

        String body = client.get("/export/categories.json", java.util.Map.of("limit", "1"));
        assertEquals("{}", body);
    }

    @Test
    void unauthorizedThrowsSecurityException() {
        stubFor(get(urlEqualTo("/export/categories.json?limit=1"))
                .willReturn(aResponse().withStatus(401)));

        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress(server.getRuntimeInfo().getHttpBaseUrl());
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        configuration.setRetryMax(0);
        configuration.setRetryBackoffBaseMs(1L);
        configuration.setConnectTimeoutMs(1_000);
        configuration.setReadTimeoutMs(1_000);
        configuration.validate();
        IndicoHttpClient localClient = new IndicoHttpClient(configuration, new IndicoAuthenticator(configuration));

        assertThrows(ConnectorSecurityException.class,
                () -> localClient.get("/export/categories.json", java.util.Map.of("limit", "1")));
    }

    @Test
    void notFoundThrowsUnknownUid() {
        stubFor(get(urlEqualTo("/export/registrants/1.json?registration_id=missing"))
                .willReturn(aResponse().withStatus(404)));

        assertThrows(UnknownUidException.class,
                () -> client.get("/export/registrants/1.json", java.util.Map.of("registration_id", "missing")));
    }

    @Test
    void retriesOnServerErrorThenSucceeds() {
        stubFor(get(urlEqualTo("/export/registrants/1.json"))
                .inScenario("retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo("second"));
        stubFor(get(urlEqualTo("/export/registrants/1.json"))
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(ok().withBody("{\"registrants\": []}")));

        String response = client.get("/export/registrants/1.json", java.util.Map.of());
        assertEquals("{\"registrants\": []}", response);
    }

    @Test
    void exhaustsRetriesAndThrowsIoException() {
        stubFor(get(urlEqualTo("/export/registrants/1.json"))
                .willReturn(serverError()));

        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress(server.getRuntimeInfo().getHttpBaseUrl());
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        configuration.setRetryMax(0);
        configuration.setRetryBackoffBaseMs(1L);
        configuration.setConnectTimeoutMs(1_000);
        configuration.setReadTimeoutMs(1_000);
        configuration.validate();
        IndicoHttpClient localClient = new IndicoHttpClient(configuration, new IndicoAuthenticator(configuration));

        assertThrows(ConnectorIOException.class,
                () -> localClient.get("/export/registrants/1.json", java.util.Map.of()));
    }
}
