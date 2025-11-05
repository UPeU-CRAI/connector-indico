package com.identicum.connectors.indico.service;

public class RegistrationQuery {

    private final long eventId;
    private final String registrationId;
    private final String email;
    private final Integer limit;
    private final String pageToken;

    public RegistrationQuery(long eventId, String registrationId, String email, Integer limit, String pageToken) {
        this.eventId = eventId;
        this.registrationId = registrationId;
        this.email = email;
        this.limit = limit;
        this.pageToken = pageToken;
    }

    public long getEventId() {
        return eventId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getEmail() {
        return email;
    }

    public Integer getLimit() {
        return limit;
    }

    public String getPageToken() {
        return pageToken;
    }

    public RegistrationQuery nextPage(String nextPageToken) {
        return new RegistrationQuery(eventId, registrationId, email, limit, nextPageToken);
    }
}
