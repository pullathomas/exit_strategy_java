package com.robinhowlett.chartparser.charts.pdf;

import com.robinhowlett.chartparser.exceptions.ChartParserException;

/**
 * Enum for Thoroughbred (TB), Quarter Horse (QH), Arabian (AR), and Mixed (MX; usually a race
 * containing both TB and QH)
 */
public enum Breed {
    THOROUGHBRED("Thoroughbred", "TB"),
    QUARTER_HORSE("Quarter Horse", "QH"),
    ARABIAN("Arabian", "ARAB"),
    MIXED("Mixed", "MIX");

    private final String chartValue;
    private final String code;

    Breed(String chartValue, String code) {
        this.chartValue = chartValue;
        this.code = code;
    }

    // forChartValue("Thoroughbred") returns Breed.THOROUGHBRED
    public static Breed forChartValue(String text) throws NoMatchingBreedException {
        for (Breed breed : values()) {
            if (breed.getChartValue().equals(text)) {
                return breed;
            }
        }
        throw new NoMatchingBreedException(text);
    }

    // forCode("TB") returns Breed.THOROUGHBRED
    public static Breed forCode(String text) throws NoMatchingBreedException {
        for (Breed breed : values()) {
            if (breed.getCode().equals(text)) {
                return breed;
            }
        }
        throw new NoMatchingBreedException(text);
    }

    public static boolean isBreed(String text) {
        for (Breed breed : values()) {
            if (breed.getChartValue().equals(text)) {
                return true;
            }
        }
        return false;
    }

    public String getChartValue() {
        return chartValue;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "Breed{" +
                "chartValue='" + chartValue + '\'' +
                ", code='" + code + '\'' +
                '}';
    }

    public static class NoMatchingBreedException extends ChartParserException {
        public NoMatchingBreedException(String message) {
            super(String.format("Did not match a breed for %s", message));
        }
    }
}
