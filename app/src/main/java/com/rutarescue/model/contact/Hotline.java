package com.rutarescue.model.contact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One emergency hotline on the Home screen: an agency and the numbers people can call, written
 * the way the source printed them, e.g. "(02) 8527-8385 to 95".
 * Immutable: the list comes from a file bundled with the app and never changes while it runs.
 */
public final class Hotline {

    private final String agency;
    private final List<String> numbers;

    public Hotline(String agency, List<String> numbers) {
        if (agency == null || agency.isBlank()) {
            throw new IllegalArgumentException("A hotline needs the agency name.");
        }
        List<String> cleaned = new ArrayList<>();
        if (numbers != null) {
            for (String number : numbers) {
                if (number != null && !number.isBlank()) cleaned.add(number.strip());
            }
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("A hotline needs at least one number.");
        }
        this.agency = agency.strip();
        this.numbers = Collections.unmodifiableList(cleaned);
    }

    /**
     * What the phone dialer receives for a printed number: only the digits of its first number.
     * "(02) 8527-8385 to 95" dials 0285278385, and "loc. 100" is left for the caller to enter.
     */
    public static String dialDigits(String printed) {
        String first = printed.split(" to | loc")[0];
        StringBuilder digits = new StringBuilder();
        for (char c : first.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
        }
        return digits.toString();
    }

    public String getAgency() { return agency; }
    public List<String> getNumbers() { return numbers; }

    /** The numbers on one line, e.g. "143 • (02) 8527-8385 to 95". */
    public String getNumbersText() {
        return String.join(" • ", numbers);
    }
}
