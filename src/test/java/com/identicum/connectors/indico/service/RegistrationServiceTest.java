package com.identicum.connectors.indico.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.identicum.connectors.indico.IndicoAuthenticator;
import com.identicum.connectors.indico.IndicoConfiguration;
import com.identicum.connectors.indico.http.IndicoHttpClient;
import com.identicum.connectors.indico.mapper.RegistrationMapper;
import com.identicum.connectors.indico.model.RegistrationPage;
import com.identicum.connectors.indico.model.RegistrationRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.identityconnectors.common.security.GuardedString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RegistrationServiceTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().configureStaticDsl(true).build();

    private RegistrationService registrationService;

    @BeforeEach
    void setup(WireMockRuntimeInfo runtimeInfo) {
        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress(runtimeInfo.getHttpBaseUrl());
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        configuration.setRetryMax(1);
        configuration.setRetryBackoffBaseMs(1L);
        configuration.setConnectTimeoutMs(1_000);
        configuration.setReadTimeoutMs(1_000);
        configuration.validate();
        IndicoHttpClient client = new IndicoHttpClient(configuration, new IndicoAuthenticator(configuration));
        registrationService = new RegistrationService(client, new RegistrationMapper());
    }

    @Test
    void fetchRegistrationsMapsRecords() throws IOException {
        String body = Files.readString(Path.of("src/test/resources/fixtures/registrations_page1.json"));
        stubFor(get(urlPathEqualTo("/export/registrants/1.json"))
                .withQueryParam("limit", WireMock.equalTo("2"))
                .willReturn(ok().withBody(body)));

        RegistrationPage page = registrationService.fetchRegistrations(new RegistrationQuery(1L, null, null, 2, null));
        assertEquals(1, page.getRecords().size());
        RegistrationRecord record = page.getRecords().get(0);
        assertEquals("R1", record.getRegistrationId());
        assertEquals("alice@example.org", record.getEmail());
        assertEquals(1L, record.getEventId());
        assertTrue(page.hasNextPage());
        assertEquals("2", page.getNextPageToken());
    }

    @Test
    void fetchRegistrationsPassesEmailFilter() throws IOException {
        String body = Files.readString(Path.of("src/test/resources/fixtures/registrations_page2.json"));
        stubFor(get(urlPathEqualTo("/export/registrants/1.json"))
                .withQueryParam("email", WireMock.equalTo("alice@example.org"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        RegistrationPage page = registrationService.fetchRegistrations(new RegistrationQuery(1L, null, "alice@example.org", null, null));
        assertEquals(2, page.getRecords().size());
    }
}
