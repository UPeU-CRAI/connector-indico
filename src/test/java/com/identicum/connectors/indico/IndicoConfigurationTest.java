package com.identicum.connectors.indico;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.identityconnectors.common.security.GuardedString;
import org.junit.jupiter.api.Test;

class IndicoConfigurationTest {

    @Test
    void validateTokenStrategyRequiresToken() {
        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress("https://example.test");
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        assertThrows(IllegalArgumentException.class, configuration::validate);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        assertDoesNotThrow(configuration::validate);
    }

    @Test
    void validateApiKeyStrategyRequiresSecret() {
        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress("https://example.test");
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.API_KEY);
        configuration.setApiKey("key");
        assertThrows(IllegalArgumentException.class, configuration::validate);
        configuration.setApiSecret(new GuardedString("secret".toCharArray()));
        assertDoesNotThrow(configuration::validate);
    }
}
