package com.robinhowlett.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Stores a rating related to a {@link RaceResult} or a {@link Starter} (e.g. speed figure,
 * class/power rating etc.)
 */
public class Rating {

    protected final String name;
    protected final String text;
    protected final Double value;
    @JsonInclude(NON_NULL)
    protected final String extra;

    public Rating(String name, String text, Double value, String extra) {
        this.name = name;
        this.text = text;
        this.value = value;
        this.extra = extra;
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }

    public Double getValue() {
        return value;
    }

    public String getExtra() {
        return extra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Rating rating = (Rating) o;

        if (name != null ? !name.equals(rating.name) : rating.name != null) return false;
        if (text != null ? !text.equals(rating.text) : rating.text != null) return false;
        if (value != null ? !value.equals(rating.value) : rating.value != null) return false;
        return extra != null ? extra.equals(rating.extra) : rating.extra == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (extra != null ? extra.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Rating{" +
                "name='" + name + '\'' +
                ", text='" + text + '\'' +
                ", value=" + value +
                ", extra='" + extra + '\'' +
                '}';
    }

    /**
     * An AQHA (American Quarter Horse Association) Speed Index rating.
     */
    public static class AqhaSpeedIndex extends Rating {

        private final Long millis;

        public AqhaSpeedIndex(Integer value, Long millis) {
            super("AQHA Speed Index", String.valueOf(value), value.doubleValue(), null);
            this.millis = millis;
        }

        @JsonIgnore
        public Long getMillis() {
            return millis;
        }

        @Override
        public String toString() {
            return "AqhaSpeedIndex{" +
                    "name='" + name + '\'' +
                    ", text='" + text + '\'' +
                    ", value=" + value +
                    ", extra='" + extra + '\'' +
                    ", millis=" + millis +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            AqhaSpeedIndex that = (AqhaSpeedIndex) o;

            return millis != null ? millis.equals(that.millis) : that.millis == null;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (millis != null ? millis.hashCode() : 0);
            return result;
        }
    }
}
