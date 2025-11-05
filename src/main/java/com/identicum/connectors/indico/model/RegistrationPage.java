package com.identicum.connectors.indico.model;

import java.util.Collections;
import java.util.List;

public class RegistrationPage {

    private final List<RegistrationRecord> records;
    private final String nextPageToken;

    public RegistrationPage(List<RegistrationRecord> records, String nextPageToken) {
        this.records = records == null ? Collections.emptyList() : Collections.unmodifiableList(records);
        this.nextPageToken = nextPageToken;
    }

    public List<RegistrationRecord> getRecords() {
        return records;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public boolean hasNextPage() {
        return nextPageToken != null && !nextPageToken.isEmpty();
    }
}
