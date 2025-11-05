package com.identicum.connectors.indico;

/**
 * Represents a translated search filter for Indico registrants.
 */
public class IndicoFilter {

    private String registrationId;
    private String email;
    private Long eventId;

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}
