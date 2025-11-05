package com.identicum.connectors.indico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.identicum.connectors.indico.mapper.RegistrationMapper;
import com.identicum.connectors.indico.model.RegistrationPage;
import com.identicum.connectors.indico.model.RegistrationRecord;
import com.identicum.connectors.indico.service.RegistrationQuery;
import com.identicum.connectors.indico.service.RegistrationService;
import java.util.ArrayList;
import java.util.List;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class IndicoConnectorTest {

    private IndicoConnector connector;
    private RegistrationService registrationService;
    private RegistrationMapper registrationMapper;

    @BeforeEach
    void setup() throws Exception {
        IndicoConfiguration configuration = new IndicoConfiguration();
        configuration.setServiceAddress("https://indico.test");
        configuration.setAuthStrategy(IndicoConfiguration.AuthStrategy.TOKEN);
        configuration.setApiToken(new GuardedString("token".toCharArray()));
        configuration.setDefaultEventId(1L);
        configuration.validate();

        connector = new IndicoConnector();
        connector.init(configuration);

        registrationService = mock(RegistrationService.class);
        registrationMapper = new RegistrationMapper();

        java.lang.reflect.Field serviceField = IndicoConnector.class.getDeclaredField("registrationService");
        serviceField.setAccessible(true);
        serviceField.set(connector, registrationService);

        java.lang.reflect.Field mapperField = IndicoConnector.class.getDeclaredField("registrationMapper");
        mapperField.setAccessible(true);
        mapperField.set(connector, registrationMapper);
    }

    @Test
    void executeQueryStreamsAllPages() {
        when(registrationService.fetchRegistrations(any(RegistrationQuery.class)))
                .thenReturn(firstPage())
                .thenReturn(secondPage());

        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(ObjectClass.ACCOUNT, null, results::add, new OperationOptionsBuilder().build());

        assertEquals(3, results.size());
        assertEquals("R1", results.get(0).getUid().getUidValue());
        assertEquals("R3", results.get(2).getUid().getUidValue());

        ArgumentCaptor<RegistrationQuery> captor = ArgumentCaptor.forClass(RegistrationQuery.class);
        verify(registrationService, Mockito.times(2)).fetchRegistrations(captor.capture());
        assertEquals(1L, captor.getAllValues().get(0).getEventId());
    }

    @Test
    void executeQueryByUidThrowsUnknownWhenMissing() {
        when(registrationService.fetchRegistrations(any(RegistrationQuery.class)))
                .thenReturn(new RegistrationPage(List.of(), null));

        IndicoFilter filter = new IndicoFilter();
        filter.setRegistrationId("missing");

        assertThrows(UnknownUidException.class,
                () -> connector.executeQuery(ObjectClass.ACCOUNT, filter, obj -> true, new OperationOptionsBuilder().build()));
    }

    private RegistrationPage firstPage() {
        RegistrationRecord r1 = new RegistrationRecord();
        r1.setRegistrationId("R1");
        r1.setEmail("alice@example.org");
        r1.setEventId(1L);

        RegistrationRecord r2 = new RegistrationRecord();
        r2.setRegistrationId("R2");
        r2.setEmail("bob@example.org");
        r2.setEventId(1L);

        return new RegistrationPage(List.of(r1, r2), "token-2");
    }

    private RegistrationPage secondPage() {
        RegistrationRecord r3 = new RegistrationRecord();
        r3.setRegistrationId("R3");
        r3.setEmail("carol@example.org");
        r3.setEventId(1L);
        return new RegistrationPage(List.of(r3), null);
    }
}
