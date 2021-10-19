package com.robinhowlett.chartparser.charts.pdf.running_line;

import com.robinhowlett.chartparser.charts.pdf.Chart;
import com.robinhowlett.chartparser.charts.pdf.ChartCharacter;
import com.robinhowlett.chartparser.charts.pdf.Starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The value of the {@link Starter} (expressed within a {@link Double} {@code value} field), a
 * boolean to note if the starter was favorite, and (if applicable) the 1-based index of starter in
 * terms of odds order (e.g. 2nd fav has a "choice" value of 2)
 */
public class Odds {

    private static final Logger LOGGER = LoggerFactory.getLogger(Odds.class);

    private final Double value;
    private final boolean favorite;
    private Integer choice;

    public Odds(final Double value, boolean favorite) {
        this.value = value;
        this.favorite = favorite;
    }

    public static Odds parse(List<ChartCharacter> chartCharacters) {
        String text = Chart.convertToText(chartCharacters);
        boolean isFavorite = false;
        Double odds = null;
        if (!text.isEmpty()) {
            if (text.contains("*")) {
                isFavorite = true;
                text = text.substring(0, text.length() - 1);
            }
            try {
                odds = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                LOGGER.warn(String.format("Unable to parse value: %s, due to %s", text, e
                        .getMessage()));
                odds = null;
            }
        }
        return new Odds(odds, isFavorite);
    }

    public Double getValue() {
        return value;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public Integer getChoice() {
        return choice;
    }

    public void setChoice(Integer choice) {
        this.choice = choice;
    }

    @Override
    public String toString() {
        return "Odds{" +
                "value=" + value +
                ", favorite=" + favorite +
                ", choice=" + choice +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Odds odds = (Odds) o;

        if (favorite != odds.favorite) return false;
        if (value != null ? !value.equals(odds.value) : odds.value != null) return false;
        return choice != null ? choice.equals(odds.choice) : odds.choice == null;
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + (favorite ? 1 : 0);
        result = 31 * result + (choice != null ? choice.hashCode() : 0);
        return result;
    }
}
