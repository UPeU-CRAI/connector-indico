package com.identicum.connectors.indico;

import com.identicum.connectors.indico.http.IndicoHttpClient;
import com.identicum.connectors.indico.mapper.RegistrationMapper;
import com.identicum.connectors.indico.model.RegistrationPage;
import com.identicum.connectors.indico.model.RegistrationRecord;
import com.identicum.connectors.indico.service.RegistrationQuery;
import com.identicum.connectors.indico.service.RegistrationService;
import java.util.Map;
import java.util.Objects;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;

/**
 * ConnId connector implementation for the Indico HTTP Export API.
 */
@ConnectorClass(displayNameKey = "connector.identicum.indico.display", configurationClass = IndicoConfiguration.class)
public class IndicoConnector implements Connector, SearchOp<IndicoFilter>, TestOp, SchemaOp {

    private static final Log LOG = Log.getLog(IndicoConnector.class);

    private IndicoConfiguration configuration;
    private RegistrationMapper registrationMapper;
    private RegistrationService registrationService;
    private IndicoHttpClient httpClient;
    private Schema schema;

    @Override
    public void init(Configuration configuration) {
        this.configuration = (IndicoConfiguration) configuration;
        this.configuration.validate();
        this.registrationMapper = new RegistrationMapper();
        IndicoAuthenticator authenticator = new IndicoAuthenticator(this.configuration);
        this.httpClient = new IndicoHttpClient(this.configuration, authenticator);
        this.registrationService = new RegistrationService(httpClient, registrationMapper);
    }

    @Override
    public IndicoConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void dispose() {
        // Nothing to dispose; HttpClient is managed by JVM.
    }

    @Override
    public void test() {
        ensureInitialized();
        try {
            httpClient.get("/export/categories.json", Map.of("limit", "1"));
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorIOException("Failed to execute test call: " + e.getMessage(), e);
        }
    }

    @Override
    public FilterTranslator<IndicoFilter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        if (!ObjectClass.ACCOUNT.equals(objectClass)) {
            throw new UnsupportedOperationException("Unsupported object class: " + objectClass.getObjectClassValue());
        }
        return new IndicoFilterTranslator();
    }

    @Override
    public void executeQuery(ObjectClass objectClass, IndicoFilter filter, ResultsHandler handler, OperationOptions options) {
        ensureInitialized();
        if (!ObjectClass.ACCOUNT.equals(objectClass)) {
            throw new UnsupportedOperationException("Unsupported object class: " + objectClass.getObjectClassValue());
        }
        Objects.requireNonNull(handler, "ResultsHandler must not be null");

        long eventId = resolveEventId(filter, options);
        String registrationId = filter != null ? filter.getRegistrationId() : null;
        String email = filter != null ? filter.getEmail() : null;
        Integer limit = resolveLimit(options);
        String pageToken = options != null ? options.getPagedResultsCookie() : null;
        RegistrationQuery query = new RegistrationQuery(eventId, registrationId, email, limit, pageToken);
        boolean found = false;
        RegistrationQuery current = query;
        while (true) {
            RegistrationPage page = registrationService.fetchRegistrations(current);
            for (RegistrationRecord record : page.getRecords()) {
                if (registrationId != null && !registrationId.equals(record.getRegistrationId())) {
                    continue;
                }
                if (email != null && (record.getEmail() == null || !email.equalsIgnoreCase(record.getEmail()))) {
                    continue;
                }
                if (!handler.handle(registrationMapper.toConnectorObject(record))) {
                    return;
                }
                found = true;
            }
            if (registrationId != null && found) {
                return;
            }
            if (!page.hasNextPage()) {
                break;
            }
            current = current.nextPage(page.getNextPageToken());
        }
        if (registrationId != null && !found) {
            throw new UnknownUidException("Registration not found: " + registrationId);
        }
    }

    @Override
    public Schema schema() {
        if (schema != null) {
            return schema;
        }
        SchemaBuilder builder = new SchemaBuilder(IndicoConnector.class);
        builder.defineObjectClass(buildAccountSchema());
        schema = builder.build();
        return schema;
    }

    private org.identityconnectors.framework.common.objects.ObjectClassInfo buildAccountSchema() {
        org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder ociBuilder =
                new org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder();
        ociBuilder.setType(ObjectClass.ACCOUNT_NAME);
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("eventId", Long.class)
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("email")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("firstName")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("lastName")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("fullName")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("state")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("checkedIn", Boolean.class)
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("paid", Boolean.class)
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("createdDt")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("modifiedDt")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        ociBuilder.addAttributeInfo(new org.identityconnectors.framework.common.objects.AttributeInfoBuilder("categoryPath")
                .setReadable(true)
                .setCreateable(false)
                .setUpdateable(false)
                .build());
        return ociBuilder.build();
    }

    private long resolveEventId(IndicoFilter filter, OperationOptions options) {
        if (filter != null && filter.getEventId() != null) {
            return filter.getEventId();
        }
        if (options != null && options.getOptions() != null) {
            Object eventId = options.getOptions().get("eventId");
            if (eventId instanceof Number) {
                return ((Number) eventId).longValue();
            }
            if (eventId instanceof String) {
                try {
                    return Long.parseLong((String) eventId);
                } catch (NumberFormatException e) {
                    throw new InvalidAttributeValueException("eventId option must be a number");
                }
            }
        }
        return configuration.optionalDefaultEventId()
                .orElseThrow(() -> new InvalidAttributeValueException("eventId is required for registrant searches"));
    }

    private Integer resolveLimit(OperationOptions options) {
        if (options != null && options.getPageSize() != null) {
            return options.getPageSize();
        }
        return configuration.getPageSize();
    }

    private void ensureInitialized() {
        if (configuration == null) {
            throw new ConnectorException("Connector not initialized");
        }
    }

    // Unsupported operations (Create/Update/Delete/Sync) are intentionally absent for v0.0.1.
}
