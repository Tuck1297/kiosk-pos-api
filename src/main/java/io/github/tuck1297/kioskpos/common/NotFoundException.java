package io.github.tuck1297.kioskpos.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found.");
    }
}
