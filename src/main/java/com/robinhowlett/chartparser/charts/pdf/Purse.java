package com.robinhowlett.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.robinhowlett.chartparser.exceptions.ChartParserException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.robinhowlett.chartparser.charts.pdf.Purse.EnhancementType.INCLUDES;
import static com.robinhowlett.chartparser.charts.pdf.Purse.EnhancementType.PLUS;

import static java.util.Locale.US;

/**
 * Parses and stores the value amount, textual description, and any other additional information
 * specific to or enhancing the value
 */
@JsonPropertyOrder({"value", "text", "availableMoney", "enhancements", "valueOfRace"})
public class Purse {

    static final Pattern PURSE_PATTERN =
            Pattern.compile("Purse: (\\$([0-9]{1,3}(,[0-9]{3})*)( .+)?)");
    static final Pattern FOREIGN_CURRENCY_DISCLAIMER =
            Pattern.compile("^All money values represented in [a-zA-Z0-9_ ]* currency unless noted" +
                    " otherwise$");
    private static final Pattern AVAILABLE_MONEY_PATTERN =
            Pattern.compile("Available Money: (\\$.+)");
    private static final Pattern INCLUDES_PATTERN =
            Pattern.compile("Includes: (\\$.+)");
    private static final Pattern PLUS_PATTERN =
            Pattern.compile("Plus: (\\$.+)");
    private static final Pattern VALUE_OF_RACE_PATTERN =
            Pattern.compile("Value of Race: (\\$[\\s\\S]+)");

    private static final Logger LOGGER = LoggerFactory.getLogger(Purse.class);

    private Integer value;
    private String text;
    private String availableMoney;
    private String valueOfRace;

    @JsonIgnore
    private List<PurseEnhancement> enhancementsList = new ArrayList<>();

    public Purse() { }

    @JsonCreator
    public Purse(Integer value, String text, String availableMoney, String valueOfRace,
            List<PurseEnhancement> enhancementsList) {
        this.value = value;
        this.text = text;
        this.availableMoney = availableMoney;
        this.valueOfRace = valueOfRace;
        this.enhancementsList = enhancementsList;
    }

    public static Purse parse(final List<List<ChartCharacter>> lines) throws PurseParseException {
        Purse purse = new Purse();
        for (List<ChartCharacter> line : lines) {
            String text = Chart.convertToText(line);
            purse = parsePurseText(text, purse);
        }

        return purse;
    }

    static Purse parsePurseText(String text, Purse purse) throws PurseParseException {
        Matcher matcher = PURSE_PATTERN.matcher(text);
        if (matcher.find()) {
            String purseText = matcher.group(1);

            String purseNumber = matcher.group(2);
            Integer purseAmount = null;
            try {
                purseAmount = NumberFormat.getNumberInstance(US).parse(purseNumber).intValue();
                purse.setValue(purseAmount);
                purse.setText(purseText);
            } catch (ParseException e) {
                throw new PurseParseException(String.format("Failed to parse purse value text: %s",
                        text), e);
            }
        }

        matcher = AVAILABLE_MONEY_PATTERN.matcher(text);
        if (matcher.find()) {
            String availableMoneyText = matcher.group(1);
            purse.setAvailableMoney(availableMoneyText);
        }

        matcher = INCLUDES_PATTERN.matcher(text);
        if (matcher.find()) {
            String includesText = matcher.group(1);
            purse.enhancementsList.add(new PurseEnhancement(INCLUDES, includesText));
        }

        matcher = PLUS_PATTERN.matcher(text);
        if (matcher.find()) {
            String plusText = matcher.group(1);
            purse.enhancementsList.add(new PurseEnhancement(PLUS, plusText));
        }

        matcher = VALUE_OF_RACE_PATTERN.matcher(text);
        if (matcher.find()) {
            String valueOfRaceText = matcher.group(1);
            valueOfRaceText = valueOfRaceText.replaceAll(System.lineSeparator(), " ");
            purse.setValueOfRace(valueOfRaceText);
        }

        return purse;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAvailableMoney() {
        return availableMoney;
    }

    public void setAvailableMoney(String availableMoney) {
        this.availableMoney = availableMoney;
    }

    @JsonProperty("enhancements")
    public String getEnhancements() {
        return (!enhancementsList.isEmpty() ? enhancementsList.stream()
                .map(purseEnhancement -> String.format("%s: %s",
                        purseEnhancement.getType().getChartValue(), purseEnhancement.getText()))
                .collect(Collectors.joining(", ")) : null);
    }

    public List<PurseEnhancement> getEnhancements(EnhancementType enhancementType) {
        return enhancementsList.stream()
                .filter(enhancement -> enhancement.getType().equals(enhancementType))
                .collect(Collectors.toList());
    }

    public String getValueOfRace() {
        return valueOfRace;
    }

    public void setValueOfRace(String valueOfRace) {
        this.valueOfRace = valueOfRace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Purse purse = (Purse) o;

        if (this.value != null ? !this.value.equals(purse.value) : purse.value !=
                null)
            return false;
        if (text != null ? !text.equals(purse.text) : purse.text != null)
            return false;
        if (availableMoney != null ? !availableMoney.equals(purse.availableMoney) :
                purse.availableMoney != null)
            return false;
        if (enhancementsList != null ? !enhancementsList.equals(purse.enhancementsList) : purse
                .enhancementsList
                != null)
            return false;
        return valueOfRace != null ? valueOfRace.equals(purse.valueOfRace) : purse
                .valueOfRace == null;
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (availableMoney != null ? availableMoney.hashCode() : 0);
        result = 31 * result + (enhancementsList != null ? enhancementsList.hashCode() : 0);
        result = 31 * result + (valueOfRace != null ? valueOfRace.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Purse{" +
                "value=" + value +
                ", text='" + text + '\'' +
                ", availableMoney='" + availableMoney + '\'' +
                ", enhancementsList=" + enhancementsList +
                ", valueOfRace='" + valueOfRace + '\'' +
                '}';
    }

    /**
     * Stores the value {@link EnhancementType} and its textual description
     */
    public static class PurseEnhancement {
        private final EnhancementType type;
        private final String text;

        public PurseEnhancement(EnhancementType type, String text) {
            this.type = type;
            this.text = text;
        }

        public EnhancementType getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PurseEnhancement that = (PurseEnhancement) o;

            if (type != that.type) return false;
            return text != null ? text.equals(that.text) : that.text == null;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (text != null ? text.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "PurseEnhancement{" +
                    "type=" + type +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    /**
     * Enum for types of value enhancementsList e.g. Plus or Includes
     */
    public enum EnhancementType {
        PLUS("Plus"),
        INCLUDES("Includes");

        private final String chartValue;

        EnhancementType(String chartValue) {
            this.chartValue = chartValue;
        }

        public String getChartValue() {
            return chartValue;
        }

        public static EnhancementType forChartValue(String chartValue) {
            for (EnhancementType enhancementType : values()) {
                if (enhancementType.getChartValue().equals(chartValue)) {
                    return enhancementType;
                }
            }
            return null; // throw an exception perhaps...
        }

        @Override
        public String toString() {
            return "EnhancementType{" +
                    "chartValue='" + chartValue + '\'' +
                    '}';
        }
    }

    public static class PurseParseException extends ChartParserException {
        public PurseParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
