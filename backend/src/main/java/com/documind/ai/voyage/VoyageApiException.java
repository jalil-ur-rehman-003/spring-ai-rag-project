package com.documind.ai.voyage;

/** Thrown when the Voyage AI embeddings API returns an error response or the call otherwise fails. */
public class VoyageApiException extends RuntimeException {

    public VoyageApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
