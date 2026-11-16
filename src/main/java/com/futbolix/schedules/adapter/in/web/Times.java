package com.futbolix.schedules.adapter.in.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Times as the contract writes them: HH:mm:ss (court-service.yaml, schema Time). */
final class Times {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern CONTRACT = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$");

    private Times() {
    }

    static String format(LocalTime time) {
        return time.format(FORMAT);
    }

    /** Null when the text does not follow the contract pattern. */
    static LocalTime parse(String text) {
        return text != null && CONTRACT.matcher(text).matches() ? LocalTime.parse(text, FORMAT) : null;
    }
}
