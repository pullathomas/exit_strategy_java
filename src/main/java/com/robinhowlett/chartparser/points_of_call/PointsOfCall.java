package com.robinhowlett.chartparser.points_of_call;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.robinhowlett.chartparser.charts.pdf.Chart;
import com.robinhowlett.chartparser.charts.pdf.Starter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Stores the {@link PointOfCall} instances for a particular race distance
 */
public class PointsOfCall {

    private final String distance;
    private final int floor;
    private final List<PointOfCall> calls;

    @JsonCreator
    public PointsOfCall(
            @JsonProperty("distance") String distance,
            @JsonProperty("floor") int floor,
            @JsonProperty("calls") List<PointOfCall> calls) {
        this.distance = distance;
        this.floor = floor;
        this.calls = calls;
    }

    public String getDistance() {
        return distance;
    }

    public int getFloor() {
        return floor;
    }

    public List<PointOfCall> getCalls() {
        return calls;
    }

    public Optional<PointOfCall> getStretchPointOfCall() {
        return getPointOfCall(5);
    }

    public Optional<PointOfCall> getFinishPointOfCall() {
        return getPointOfCall(6);
    }

    private Optional<PointOfCall> getPointOfCall(int index) {
        for (PointOfCall pointOfCall : getCalls()) {
            if (pointOfCall.getPoint() == index) {
                return Optional.of(pointOfCall);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "PointsOfCall{" +
                "distance='" + distance + '\'' +
                ", floor=" + floor +
                ", calls=" + calls +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PointsOfCall that = (PointsOfCall) o;

        if (floor != that.floor) return false;
        if (distance != null ? !distance.equals(that.distance) : that.distance != null)
            return false;
        return calls != null ? calls.equals(that.calls) : that.calls == null;

    }

    @Override
    public int hashCode() {
        int result = distance != null ? distance.hashCode() : 0;
        result = 31 * result + floor;
        result = 31 * result + (calls != null ? calls.hashCode() : 0);
        return result;
    }

    /**
     * A specific point of call for the specified {@link PointsOfCall} for the race distance in
     * question
     */
    @JsonPropertyOrder({"point", "text", "compact", "feet", "furlongs", "relativePosition"})
    public static class PointOfCall {
        private final int point;
        private final String text;
        private String compact;
        private Integer feet;
        private Double furlongs;
        private RelativePosition relativePosition;

        public PointOfCall(int point, String text, String compact, Integer feet) {
            this(point, text, compact, feet, null);
        }

        @JsonCreator
        public PointOfCall(
                @JsonProperty("point") int point,
                @JsonProperty("text") String text,
                @JsonProperty("compact") String compact,
                @JsonProperty("feet") Integer feet,
                @JsonProperty("relativePosition") RelativePosition relativePosition) {
            this.point = point;
            this.text = text;
            this.compact = compact;
            this.feet = feet;
            this.furlongs = (feet != null ?
                    Chart.round((double) feet / 660, 2).doubleValue() : null);
            this.relativePosition = relativePosition;
        }

        public boolean hasKnownDistance() {
            return (feet != null);
        }

        public boolean hasRelativePosition() {
            return getRelativePosition() != null;
        }

        public boolean hasLengths() {
            return getRelativePosition() != null && getRelativePosition().getLengthsAhead() != null;
        }

        public int getPoint() {
            return point;
        }

        public String getText() {
            return text;
        }

        public String getCompact() {
            return compact;
        }

        public void setCompact(String compact) {
            this.compact = compact;
        }

        public Integer getFeet() {
            return feet;
        }

        public void setFeet(Integer feet) {
            this.feet = feet;
            this.furlongs = (feet != null ?
                    Chart.round((double) feet / 660, 2).doubleValue() : null);
        }

        public Double getFurlongs() {
            return furlongs;
        }

        public RelativePosition getRelativePosition() {
            return relativePosition;
        }

        public void setRelativePosition(RelativePosition relativePosition) {
            this.relativePosition = relativePosition;
        }

        @Override
        public String toString() {
            return "PointOfCall{" +
                    "point=" + point +
                    ", text='" + text + '\'' +
                    ", compact='" + compact + '\'' +
                    ", feet=" + feet +
                    ", furlongs=" + furlongs +
                    ", relativePosition=" + relativePosition +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PointOfCall that = (PointOfCall) o;

            if (point != that.point) return false;
            if (text != null ? !text.equals(that.text) : that.text != null) return false;
            if (compact != null ? !compact.equals(that.compact) : that.compact != null)
                return false;
            if (feet != null ? !feet.equals(that.feet) : that.feet != null) return false;
            if (furlongs != null ? !furlongs.equals(that.furlongs) : that.furlongs != null)
                return false;
            return relativePosition != null ? relativePosition.equals(that.relativePosition) : that
                    .relativePosition == null;
        }

        @Override
        public int hashCode() {
            int result = point;
            result = 31 * result + (text != null ? text.hashCode() : 0);
            result = 31 * result + (compact != null ? compact.hashCode() : 0);
            result = 31 * result + (feet != null ? feet.hashCode() : 0);
            result = 31 * result + (furlongs != null ? furlongs.hashCode() : 0);
            result = 31 * result + (relativePosition != null ? relativePosition.hashCode() : 0);
            return result;
        }

        /**
         * Stores the position of the {@link Starter} at this point of call, and, if applicable, the
         * details about the number of lengths ahead of the next starter, and the total number of
         * lengths behind the leader at this point. "Wide" tracks the position of the horse versus
         * the rail e.g. "5-wide" would be five horse widths from the inside rail.
         */
        @JsonPropertyOrder({"position", "lengthsAhead", "totalLengthsBehind", "wide"})
        public static class RelativePosition {
            private final Integer position;
            private final LengthsAhead lengthsAhead;
            private TotalLengthsBehind totalLengthsBehind;
            @JsonInclude(NON_NULL)
            private Integer wide; // reserved for custom use

            public RelativePosition(Integer position, LengthsAhead lengthsAhead) {
                this(position, lengthsAhead, null);
            }

            public RelativePosition(Integer position, LengthsAhead lengthsAhead,
                    TotalLengthsBehind totalLengthsBehind) {
                this(position, lengthsAhead, totalLengthsBehind, null);
            }

            @JsonCreator
            public RelativePosition(
                    @JsonProperty("position") Integer position,
                    @JsonProperty("lengthsAhead") LengthsAhead lengthsAhead,
                    @JsonProperty("totalLengthsBehind") TotalLengthsBehind totalLengthsBehind,
                    @JsonProperty("wide") Integer wide) {
                this.position = position;
                this.lengthsAhead = lengthsAhead;
                this.totalLengthsBehind = totalLengthsBehind;
                this.wide = wide;
            }

            public Integer getPosition() {
                return position;
            }

            public LengthsAhead getLengthsAhead() {
                return lengthsAhead;
            }

            public TotalLengthsBehind getTotalLengthsBehind() {
                return totalLengthsBehind;
            }

            public void setTotalLengthsBehind(TotalLengthsBehind totalLengthsBehind) {
                this.totalLengthsBehind = totalLengthsBehind;
            }

            public Integer getWide() {
                return wide;
            }

            public void setWide(Integer wide) {
                this.wide = wide;
            }

            @Override
            public String toString() {
                return "RelativePosition{" +
                        "position=" + position +
                        ", lengthsAhead=" + lengthsAhead +
                        ", totalLengthsBehind=" + totalLengthsBehind +
                        ", wide=" + wide +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                RelativePosition that = (RelativePosition) o;
                return Objects.equals(position, that.position) &&
                        Objects.equals(lengthsAhead, that.lengthsAhead) &&
                        Objects.equals(totalLengthsBehind, that.totalLengthsBehind) &&
                        Objects.equals(wide, that.wide);
            }

            @Override
            public int hashCode() {
                return Objects.hash(position, lengthsAhead, totalLengthsBehind, wide);
            }

            /**
             * Tracks lengths as the chart's textual description and as a Double
             */
            @JsonPropertyOrder({"text", "lengths"})
            abstract static class Lengths {
                protected final String text;
                protected final Double lengths;

                public Lengths(String text, Double lengths) {
                    this.text = text;
                    this.lengths = lengths;
                }

                public String getText() {
                    return text;
                }

                public Double getLengths() {
                    return lengths;
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;

                    Lengths lengths1 = (Lengths) o;

                    if (text != null ? !text.equals(lengths1.text) : lengths1.text != null)
                        return false;
                    return lengths != null ? lengths.equals(lengths1.lengths) : lengths1.lengths
                            == null;
                }

                @Override
                public int hashCode() {
                    int result = text != null ? text.hashCode() : 0;
                    result = 31 * result + (lengths != null ? lengths.hashCode() : 0);
                    return result;
                }
            }

            /**
             * The number of lengths ahead of the next starter
             */
            public static class LengthsAhead extends Lengths {

                public LengthsAhead(String chart, Double lengths) {
                    super(chart, lengths);
                }

                @Override
                public String toString() {
                    return "ChartLengthsAhead{" +
                            "text='" + text + '\'' +
                            ", lengthsAhead=" + lengths +
                            '}';
                }
            }

            /**
             * The total number of lengths behind the leader at the particular point of call
             */
            public static class TotalLengthsBehind extends Lengths {

                public TotalLengthsBehind(String chart, Double lengths) {
                    super(chart, lengths);
                }

                @Override
                public String toString() {
                    return "TotalLengthsBehind{" +
                            "text='" + text + '\'' +
                            ", lengthsAhead=" + lengths +
                            '}';
                }
            }
        }
    }
}
