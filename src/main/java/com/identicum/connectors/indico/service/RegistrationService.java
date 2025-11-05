package com.identicum.connectors.indico.service;

import com.identicum.connectors.indico.http.IndicoHttpClient;
import com.identicum.connectors.indico.mapper.RegistrationMapper;
import com.identicum.connectors.indico.model.RegistrationPage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service abstraction for Indico registrant exports.
 */
public class RegistrationService {

    private final IndicoHttpClient httpClient;
    private final RegistrationMapper mapper;

    public RegistrationService(IndicoHttpClient httpClient, RegistrationMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public RegistrationPage fetchRegistrations(RegistrationQuery query) {
        String path = "/export/registrants/" + query.getEventId() + ".json";
        Map<String, String> params = new LinkedHashMap<>();
        if (query.getLimit() != null) {
            params.put("limit", String.valueOf(query.getLimit()));
        }
        if (query.getPageToken() != null && !query.getPageToken().isEmpty()) {
            params.put("page", query.getPageToken());
        }
        if (query.getRegistrationId() != null) {
            params.put("registration_id", query.getRegistrationId());
        }
        if (query.getEmail() != null) {
            params.put("email", query.getEmail());
        }
        String responseBody = httpClient.get(path, params);
        return mapper.mapPage(responseBody, query.getEventId());
    }
}
