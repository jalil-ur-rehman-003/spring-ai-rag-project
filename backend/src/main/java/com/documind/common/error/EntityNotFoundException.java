package com.documind.common.error;

/** Thrown when a lookup by id (or another unique key) finds nothing. Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public static EntityNotFoundException forEntity(String entityName, Object identifier) {
        return new EntityNotFoundException(entityName + " not found for id: " + identifier);
    }
}
