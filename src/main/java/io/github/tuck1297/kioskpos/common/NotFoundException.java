package io.github.tuck1297.kioskpos.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", resource + " " + id + " was not found.");
    }
}
