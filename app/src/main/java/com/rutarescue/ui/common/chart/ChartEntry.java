package com.rutarescue.ui.common.chart;

import androidx.annotation.ColorRes;

/**
 * One value in a chart: a label ("Oct 14", "Advisories", "Approved"), the number, the text shown
 * next to it ("3", "190 of 200 · 95%") and its colour. Immutable, so a chart can keep the list.
 */
public final class ChartEntry {

    private final String label;
    private final double value;
    private final String valueText;
    @ColorRes
    private final int color;

    public ChartEntry(String label, double value, String valueText, @ColorRes int color) {
        this.label = label == null ? "" : label;
        this.value = Math.max(0, value);
        this.valueText = valueText == null ? "" : valueText;
        this.color = color;
    }

    public String getLabel() { return label; }
    public double getValue() { return value; }
    public String getValueText() { return valueText; }
    @ColorRes
    public int getColor() { return color; }
}
