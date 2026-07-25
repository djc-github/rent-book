package com.djc.rentbook.mutation;

import java.time.OffsetDateTime;

public class IdempotencyRecord {
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private String responsePayload;
    private OffsetDateTime expiresAt;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getStatus() {
        return status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}

