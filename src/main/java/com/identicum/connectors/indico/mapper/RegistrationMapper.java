package com.identicum.connectors.indico.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identicum.connectors.indico.model.RegistrationPage;
import com.identicum.connectors.indico.model.RegistrationRecord;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.identityconnectors.common.logging.Log;

/**
 * Maps Indico JSON payloads to connector domain objects.
 */
public class RegistrationMapper {

    private static final Log LOG = Log.getLog(RegistrationMapper.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RegistrationPage mapPage(String body, long eventId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode arrayNode = extractRegistrantsArray(root).orElse(root);
            List<RegistrationRecord> records = new ArrayList<>();
            if (arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    mapRecord(node, eventId).ifPresent(records::add);
                }
            } else {
                mapRecord(arrayNode, eventId).ifPresent(records::add);
            }
            String nextPageToken = extractNextToken(root).orElse(null);
            return new RegistrationPage(records, nextPageToken);
        } catch (JsonProcessingException e) {
            LOG.error(e, "Cannot parse response from Indico");
            throw new IllegalStateException("Unable to parse Indico response", e);
        }
    }

    public ConnectorObject toConnectorObject(RegistrationRecord record) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.ACCOUNT);
        if (record.getRegistrationId() != null) {
            builder.setUid(record.getRegistrationId());
            builder.setName(record.getRegistrationId());
        }
        if (record.getEventId() != null) {
            builder.addAttribute(AttributeBuilder.build("eventId", record.getEventId()));
        }
        if (record.getEmail() != null) {
            builder.addAttribute(AttributeBuilder.build("email", record.getEmail()));
        }
        if (record.getFirstName() != null) {
            builder.addAttribute(AttributeBuilder.build("firstName", record.getFirstName()));
        }
        if (record.getLastName() != null) {
            builder.addAttribute(AttributeBuilder.build("lastName", record.getLastName()));
        }
        if (record.getFullName() != null) {
            builder.addAttribute(AttributeBuilder.build("fullName", record.getFullName()));
        }
        if (record.getState() != null) {
            builder.addAttribute(AttributeBuilder.build("state", record.getState()));
        }
        if (record.getCheckedIn() != null) {
            builder.addAttribute(AttributeBuilder.build("checkedIn", record.getCheckedIn()));
        }
        if (record.getPaid() != null) {
            builder.addAttribute(AttributeBuilder.build("paid", record.getPaid()));
        }
        if (record.getCreatedDate() != null) {
            builder.addAttribute(AttributeBuilder.build("createdDt", record.getCreatedDate()));
        }
        if (record.getModifiedDate() != null) {
            builder.addAttribute(AttributeBuilder.build("modifiedDt", record.getModifiedDate()));
        }
        if (record.getCategoryPath() != null) {
            builder.addAttribute(AttributeBuilder.build("categoryPath", record.getCategoryPath()));
        }
        return builder.build();
    }

    private Optional<JsonNode> extractRegistrantsArray(JsonNode root) {
        if (root == null) {
            return Optional.empty();
        }
        if (root.isArray()) {
            return Optional.of(root);
        }
        for (String field : new String[]{"registrants", "results", "data", "rows"}) {
            if (root.has(field)) {
                return Optional.of(root.get(field));
            }
        }
        return Optional.empty();
    }

    private Optional<RegistrationRecord> mapRecord(JsonNode node, long eventId) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        try {
            RegistrationRecord record = objectMapper.treeToValue(node, RegistrationRecord.class);
            if (record.getRegistrationId() == null && node.has("registration_id")) {
                record.setRegistrationId(node.get("registration_id").asText());
            }
            if (record.getEventId() == null) {
                if (node.has("event_id")) {
                    record.setEventId(node.get("event_id").asLong());
                } else {
                    record.setEventId(eventId);
                }
            }
            if (node.has("category_path") && record.getCategoryPath() == null) {
                record.setCategoryPath(node.get("category_path").asText());
            }
            if (record.getFullName() == null && node.has("person")) {
                JsonNode person = node.get("person");
                if (person.has("full_name")) {
                    record.setFullName(person.get("full_name").asText());
                }
                if (person.has("first_name") && record.getFirstName() == null) {
                    record.setFirstName(person.get("first_name").asText());
                }
                if (person.has("last_name") && record.getLastName() == null) {
                    record.setLastName(person.get("last_name").asText());
                }
                if (record.getEmail() == null && person.has("email")) {
                    record.setEmail(person.get("email").asText());
                }
            }
            return Optional.of(record);
        } catch (JsonProcessingException e) {
            LOG.warn(e, "Skipping invalid registrant payload: {0}", node.toString());
            return Optional.empty();
        }
    }

    private Optional<String> extractNextToken(JsonNode root) {
        if (root == null) {
            return Optional.empty();
        }
        if (root.has("next")) {
            return Optional.ofNullable(asText(root.get("next")));
        }
        if (root.has("links") && root.get("links").has("next")) {
            return Optional.ofNullable(asText(root.get("links").get("next")));
        }
        if (root.has("paging")) {
            JsonNode paging = root.get("paging");
            if (paging.has("next")) {
                return Optional.ofNullable(asText(paging.get("next")));
            }
            if (paging.has("next_page_token")) {
                return Optional.ofNullable(asText(paging.get("next_page_token")));
            }
        }
        return Optional.empty();
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            if (fieldNames.hasNext()) {
                String firstField = fieldNames.next();
                return asText(node.get(firstField));
            }
        }
        return node.toString();
    }
}
