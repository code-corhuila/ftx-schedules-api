package com.futbolix.schedules.adapter.in.web;

/** A time field missing or not in HH:mm:ss. */
class InvalidTimeFormatException extends RuntimeException {

    private final String field;

    InvalidTimeFormatException(String field) {
        super("Use the format HH:mm:ss");
        this.field = field;
    }

    String field() {
        return field;
    }
}
