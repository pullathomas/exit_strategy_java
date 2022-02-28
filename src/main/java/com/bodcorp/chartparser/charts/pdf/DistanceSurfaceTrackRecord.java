package com.bodcorp.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.bodcorp.chartparser.exceptions.ChartParserException;
import com.bodcorp.chartparser.fractionals.FractionalPoint;
import com.bodcorp.chartparser.fractionals.FractionalService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.Format.FLAT;
import static com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.Format.JUMPS;
import static com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.Surface.DIRT;
import static com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.Surface.SYNTHETIC;
import static com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.Surface.TURF;
import static com.bodcorp.chartparser.charts.pdf.Purse.FOREIGN_CURRENCY_DISCLAIMER;
import static com.bodcorp.chartparser.charts.pdf.Purse.PURSE_PATTERN;

/**
 * Parses the textual description of the race distance and converts it into a {@link RaceDistance}
 * instance, including calculating the race distance in feet, furlongs, and with compact
 * description. The scheduled and actual surface race on is additionally stored. It also parses and
 * stores, in a {@link TrackRecord} instance, the details of the track record for this
 * distance/surface.
 */
@JsonPropertyOrder({"distance", "surface", "course", "trackCondition", "offTurf",
        "scheduledSurface", "scheduledSurface", "format", "trackRecord"})
public class DistanceSurfaceTrackRecord {

    static final Pattern DIST_SURF_RECORD_PATTERN =
            Pattern.compile("^((About )?(One|Two|Three|Four|Five|Six|Seven|Eight|Nine)[\\w\\s]+) " +
                    "On The ([A-Za-z\\s]+)(\\s?- Originally Scheduled For the " +
                    "([A-Za-z0-9\\-\\s]+))?(\\|Track Record: \\((.+) - ([\\d:\\.]+) - (.+)\\))?");

    private static final List<String> NUMERATORS = Arrays.asList("zero", "one", "two", "three",
            "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
            "fourteen", "fifteen");

    private static final List<String> TENS = Arrays.asList("zero", "ten", "twenty", "thirty",
            "forty", "fifty", "sixty", "seventy", "eighty", "ninety");

    private static final Pattern MILES_ONLY_PATTERN =
            Pattern.compile("^(about)? ?([\\w]+)( and ([\\w ]+))? miles?$");

    private static final Pattern FURLONGS_ONLY_PATTERN =
            Pattern.compile("^(about)? ?([\\w]+)( and ([\\w ]+))? furlongs?$");

    private static final Pattern YARDS_ONLY_PATTERN =
            Pattern.compile("^(about)? ?((\\w+) thousand)? ?(([\\w]+) hundred ?( ?and )?([\\w " +
                    "]+)?)? yards?$");

    private static final Pattern MILES_YARDS_PATTERN =
            Pattern.compile("(about)? ?([\\w]+) miles? and ([\\w ]+) yards?");

    private static final Pattern FURLONGS_YARDS_PATTERN =
            Pattern.compile("(about)? ?([\\w]+) furlongs? and ([\\w ]+) yards?");

    private static final Pattern MISSING_YARDS_PATTERN =
            Pattern.compile("^(about)? ?((\\w+) thousand)? ?(([\\w]+) hundred ?( ?and )?([\\w " +
                    "]+)?)?$");

    @JsonProperty("distance")
    private final RaceDistance raceDistance;
    private final String surface;
    private final String course;
    @JsonInclude(NON_NULL)
    private String scheduledSurface;
    @JsonInclude(NON_NULL)
    private String scheduledCourse;
    private final String format;
    private final TrackRecord trackRecord;
    private String trackCondition;

    public DistanceSurfaceTrackRecord(String distanceDescription, String course,
            String scheduledCourse, TrackRecord trackRecord) throws ChartParserException {
        this.raceDistance = (distanceDescription != null ? parseRaceDistance(distanceDescription) : null);
        //TrackRecord localTrackRecord = new TrackRecord(trackRecord.holder, trackRecord.time, trackRecord.millis, trackRecord.raceDate);
        String surf = "";
        String surfaceCourse = "";
        String surfaceFormat = "";
        SurfaceCourseFormat courseSurfaceCourseFormat = SurfaceCourseFormat.fromCourse(course);
        try {
            surf = courseSurfaceCourseFormat.getSurface().getText();
            //this.surface = courseSurfaceCourseFormat.getSurface().getText();
        } catch (Exception ex) {
            surf = "";
        }
        this.surface = surf;

        try {
            surfaceCourse = courseSurfaceCourseFormat.getCourse();
        }
        catch (Exception x){
            surfaceCourse = "";
        }
        this.course = surfaceCourse;

        try{
            surfaceFormat = courseSurfaceCourseFormat.getFormat().getText();
        }
        catch(Exception y){
            surfaceFormat = "";
        }
        this.format = surfaceFormat;

        try {
            if (scheduledCourse != null && !scheduledCourse.trim().isEmpty()) {
                SurfaceCourseFormat scheduledCourseSurfaceCourseFormat =
                        SurfaceCourseFormat.fromCourse(scheduledCourse);
                this.scheduledSurface = scheduledCourseSurfaceCourseFormat.getSurface().getText();
                this.scheduledCourse = scheduledCourseSurfaceCourseFormat.getCourse();
            } else {
                this.scheduledSurface = null;
                this.scheduledCourse = null;
            }
        }
        catch(Exception ex){
            this.scheduledSurface = null;
            this.scheduledCourse = null;
        }

        this.trackRecord = trackRecord;

    }

    @JsonCreator
    public DistanceSurfaceTrackRecord(RaceDistance raceDistance, String surface, String course,
            String scheduledSurface, String scheduledCourse, String format, TrackRecord
            trackRecord, String trackCondition) {
        this.raceDistance = raceDistance;
        this.surface = surface;
        this.course = course;
        this.scheduledSurface = scheduledSurface;
        this.scheduledCourse = scheduledCourse;
        this.format = format;
        this.trackRecord = trackRecord;
        this.trackCondition = trackCondition;
    }

    public static DistanceSurfaceTrackRecord parse(final List<List<ChartCharacter>> lines)
            throws ChartParserException {
        boolean found = false;
        StringBuilder distanceSurfaceTrackRecordBuilder = new StringBuilder();
        String prefix = "";
        for (List<ChartCharacter> line : lines) {
            String text = Chart.convertToText(line);
            if (found) {
                Matcher purseMatcher = PURSE_PATTERN.matcher(text);
                Matcher currencyMatcher = FOREIGN_CURRENCY_DISCLAIMER.matcher(text);
                if (purseMatcher.find() || currencyMatcher.find()) {
                    break;
                } else {
                    distanceSurfaceTrackRecordBuilder.append(prefix).append(text);
                }
            }
            Matcher matcher = DIST_SURF_RECORD_PATTERN.matcher(text);
            if (matcher.find() && isValidDistanceText(text)) {
                found = true;
                // prefix a space at the start of each line (except for the first)
                distanceSurfaceTrackRecordBuilder.append(prefix).append(text);
                prefix = " ";
            }
        }

        String distanceSurfaceTrackRecord = distanceSurfaceTrackRecordBuilder.toString();
        DistanceSurfaceTrackRecord distanceSurface =
                parseDistanceSurface(distanceSurfaceTrackRecord);
        if (distanceSurface != null) {
            return distanceSurface;
        }

        throw new NoRaceDistanceFound(distanceSurfaceTrackRecord);
    }

    public static boolean isValidDistanceText(String text) {
        return !((text.toLowerCase().contains("claiming price") ||
                text.toLowerCase().contains("allowed") ||
                text.toLowerCase().contains("non winners") ||
                text.toLowerCase().contains("other than"))
                && !text.toLowerCase().contains("track record"));
    }

    static DistanceSurfaceTrackRecord parseDistanceSurface(String text)
            throws ChartParserException {
        Matcher matcher = DIST_SURF_RECORD_PATTERN.matcher(text);
        if (matcher.find()) {
            String distanceDescription = matcher.group(1);
            String course = matcher.group(4).trim();
            String scheduledCourse = null;

            // detect off-turf races
            String scheduledCourseFlag = matcher.group(5);
            if (scheduledCourseFlag != null) {
                scheduledCourse = matcher.group(6);
            }

            TrackRecord trackRecord = null;
            if (matcher.group(7) != null) {
                String holder = matcher.group(8);
                String time = matcher.group(9);
                Optional<Long> recordTime =
                        FractionalService.calculateMillisecondsForFraction(time);

                String raceDateText = matcher.group(10);
                LocalDate raceDate = TrackRaceDateRaceNumber.parseRaceDate(raceDateText);

                trackRecord = new TrackRecord(new Horse(holder), (recordTime.isPresent() ?
                        FractionalPoint.convertToTime(recordTime.get()) : null),
                        (recordTime.isPresent() ? recordTime.get() : null), raceDate);
            }

            return new DistanceSurfaceTrackRecord(distanceDescription, course,
                    scheduledCourse, trackRecord);
        }
        return null;
    }

    static RaceDistance parseRaceDistance(String distanceDescription) throws ChartParserException {
        String lcDistanceDescription = distanceDescription.toLowerCase();
        Matcher milesOnlyMatcher = MILES_ONLY_PATTERN.matcher(lcDistanceDescription);
        if (milesOnlyMatcher.find()) {
            return forMiles(distanceDescription, milesOnlyMatcher);
        }
        Matcher furlongsOnlyMatcher = FURLONGS_ONLY_PATTERN.matcher(lcDistanceDescription);
        if (furlongsOnlyMatcher.find()) {
            return forFurlongs(distanceDescription, furlongsOnlyMatcher);
        }
        Matcher yardsOnlyMatcher = YARDS_ONLY_PATTERN.matcher(lcDistanceDescription);
        if (yardsOnlyMatcher.find()) {
            return forYards(distanceDescription, yardsOnlyMatcher);
        }
        Matcher milesAndYardsMatcher = MILES_YARDS_PATTERN.matcher(lcDistanceDescription);
        if (milesAndYardsMatcher.find()) {
            return forMilesAndYards(distanceDescription, milesAndYardsMatcher);
        }
        Matcher furlongsAndYardsMatcher = FURLONGS_YARDS_PATTERN.matcher(lcDistanceDescription);
        if (furlongsAndYardsMatcher.find()) {
            return forFurlongsAndYards(distanceDescription, furlongsAndYardsMatcher);
        }
        // sometimes the "Yards" part is missing
        Matcher missingYardsMatcher = MISSING_YARDS_PATTERN.matcher(lcDistanceDescription);
        if (missingYardsMatcher.find()) {
            return forYards(distanceDescription, missingYardsMatcher);
        }

        throw new ChartParserException(String.format("Unable to parse race distance from text: " +
                "%s", distanceDescription));
    }

    private static RaceDistance forMiles(String distanceDescription, Matcher matcher)
            throws ChartParserException {
        String compact = "m";
        int feet = 0;
        boolean isExact = (matcher.group(1) == null);

        String fractionalMiles = matcher.group(4);
        if (fractionalMiles != null && !fractionalMiles.isEmpty()) {
            String[] fraction = fractionalMiles.split(" ");
            String denominator = fraction[1];
            switch (denominator) {
                case "sixteenth":
                case "sixteenths":
                    feet = 330; // 5280 divided by 16
                    compact = "/16m";
                    break;
                case "eighth":
                case "eighths":
                    feet = 660;
                    compact = "/8m";
                    break;
                case "fourth":
                case "fourths":
                    feet = 1320;
                    compact = "/4m";
                    break;
                case "half":
                    feet = 2640;
                    compact = "/2m";
                    break;
                default:
                    throw new ChartParserException(String.format("Unable to parse a fractional " +
                            "mile denominator from text: %s", denominator));
            }
            String numerator = fraction[0];
            int num = NUMERATORS.indexOf(numerator);
            feet = num * feet;
            compact = String.format(" %s%s", num, compact);
        }

        String wholeMiles = matcher.group(2);
        int mileNumerator = NUMERATORS.indexOf(wholeMiles);
        feet += (mileNumerator * 5280);

        compact = (isExact ? "" : "Abt ").concat(String.format("%d%s", mileNumerator, compact));

        return new RaceDistance(distanceDescription, compact, isExact, feet);
    }

    private static RaceDistance forFurlongs(String distanceDescription, Matcher matcher)
            throws ChartParserException {
        String compact = "f";
        int feet = 0;
        boolean isExact = (matcher.group(1) == null);

        String fractionalFurlongs = matcher.group(4);
        if (fractionalFurlongs != null && !fractionalFurlongs.isEmpty()) {
            String[] fraction = fractionalFurlongs.split(" ");
            String denominator = fraction[1];
            switch (denominator) {
                case "fourth":
                case "fourths":
                    feet = 165;
                    compact = "/4f";
                    break;
                case "half":
                    feet = 330;
                    compact = "/2f";
                    break;
                default:
                    throw new ChartParserException(String.format("Unable to parse a fractional " +
                            "furlong denominator from text: %s", denominator));
            }
            String numerator = fraction[0];
            int num = NUMERATORS.indexOf(numerator);
            feet = num * feet;
            compact = String.format(" %s%s", num, compact);
        }

        String wholeFurlongs = matcher.group(2);
        int furlongNumerator = NUMERATORS.indexOf(wholeFurlongs);
        feet += (furlongNumerator * 660);
        compact = (isExact ? "" : "Abt ").concat(String.format("%d%s", furlongNumerator, compact));

        return new RaceDistance(distanceDescription, compact, isExact, feet);
    }

    private static RaceDistance forYards(String distanceDescription, Matcher matcher) {
        int feet = 0;
        boolean isExact = (matcher.group(1) == null);

        String yards = matcher.group(7);
        if (yards != null && !yards.isEmpty()) {
            String[] splitYards = yards.split(" ");
            if (splitYards.length == 2) {
                feet = (TENS.indexOf(splitYards[0]) * 10 * 3) +
                        (NUMERATORS.indexOf(splitYards[1]) * 3);
            } else {
                int inTensYards = TENS.indexOf(yards);
                if (inTensYards < 0) {
                    feet = (NUMERATORS.indexOf(yards) * 3);
                } else {
                    feet = inTensYards * 10 * 3;
                }
            }
        }

        String thousandYards = matcher.group(3);
        if (thousandYards != null) {
            int yardsInThousands = NUMERATORS.indexOf(thousandYards);
            feet += (yardsInThousands * 3000);
        }

        String hundredYards = matcher.group(5);
        if (hundredYards != null) {
            int yardsInHundreds = NUMERATORS.indexOf(hundredYards);
            feet += (yardsInHundreds * 300);
        }

        String compact = (isExact ? "" : "Abt ").concat(String.format("%dy", (feet / 3)));

        return new RaceDistance(distanceDescription, compact, isExact, feet);
    }

    private static RaceDistance forMilesAndYards(String distanceDescription, Matcher matcher) {
        String compact = null;
        int feet = 0;
        boolean isExact = (matcher.group(1) == null);

        String yards = matcher.group(3);
        if (yards != null && !yards.isEmpty()) {
            feet = TENS.indexOf(yards) * 10 * 3;
            compact = String.format("%dy", feet / 3);
        }

        String wholeMiles = matcher.group(2);
        int mileNumerator = NUMERATORS.indexOf(wholeMiles);
        feet += (mileNumerator * 5280);
        compact = (isExact ? "" : "Abt ").concat(String.format("%dm %s", mileNumerator, compact));

        return new RaceDistance(distanceDescription, compact, isExact, feet);
    }

    private static RaceDistance forFurlongsAndYards(String distanceDescription, Matcher matcher) {
        String compact = null;
        int feet = 0;
        boolean isExact = (matcher.group(1) == null);

        String yards = matcher.group(3);
        if (yards != null && !yards.isEmpty()) {
            feet = TENS.indexOf(yards) * 10 * 3;
            compact = String.format("%dy", feet / 3);
        }

        String wholeFurlongs = matcher.group(2);
        int furlongNumerator = NUMERATORS.indexOf(wholeFurlongs);
        feet += (furlongNumerator * 660);
        compact = (isExact ? "" : "Abt ").concat(
                String.format("%df %s", furlongNumerator, compact));

        return new RaceDistance(distanceDescription, compact, isExact, feet);
    }

    public String getSurface() {
        return surface;
    }

    public String getCourse() {
        return course;
    }

    public String getScheduledSurface() {
        return scheduledSurface;
    }

    public String getScheduledCourse() {
        return scheduledCourse;
    }

    public boolean isOffTurf() {
        return ((scheduledSurface != null) && (!surface.equals(scheduledSurface)));
    }

    public String getFormat() {
        return format;
    }

    public TrackRecord getTrackRecord() {
        return trackRecord;
    }

    public RaceDistance getRaceDistance() {
        return raceDistance;
    }

    public String getTrackCondition() {
        return trackCondition;
    }

    public void setTrackCondition(String trackCondition) {
        this.trackCondition = trackCondition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DistanceSurfaceTrackRecord that = (DistanceSurfaceTrackRecord) o;

        if (raceDistance != null ? !raceDistance.equals(that.raceDistance) : that.raceDistance !=
                null)
            return false;
        if (surface != null ? !surface.equals(that.surface) : that.surface != null) return false;
        if (course != null ? !course.equals(that.course) : that.course != null) return false;
        if (scheduledSurface != null ? !scheduledSurface.equals(that.scheduledSurface) : that
                .scheduledSurface != null)
            return false;
        if (scheduledCourse != null ? !scheduledCourse.equals(that.scheduledCourse) : that
                .scheduledCourse != null)
            return false;
        if (format != null ? !format.equals(that.format) : that.format != null) return false;
        if (trackRecord != null ? !trackRecord.equals(that.trackRecord) : that.trackRecord != null)
            return false;
        return trackCondition != null ? trackCondition.equals(that.trackCondition) : that
                .trackCondition == null;
    }

    @Override
    public int hashCode() {
        int result = raceDistance != null ? raceDistance.hashCode() : 0;
        result = 31 * result + (surface != null ? surface.hashCode() : 0);
        result = 31 * result + (course != null ? course.hashCode() : 0);
        result = 31 * result + (scheduledSurface != null ? scheduledSurface.hashCode() : 0);
        result = 31 * result + (scheduledCourse != null ? scheduledCourse.hashCode() : 0);
        result = 31 * result + (format != null ? format.hashCode() : 0);
        result = 31 * result + (trackRecord != null ? trackRecord.hashCode() : 0);
        result = 31 * result + (trackCondition != null ? trackCondition.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DistanceSurfaceTrackRecord{" +
                "raceDistance=" + raceDistance +
                ", surface='" + surface + '\'' +
                ", course='" + course + '\'' +
                ", scheduledSurface='" + scheduledSurface + '\'' +
                ", scheduledCourse='" + scheduledCourse + '\'' +
                ", format='" + format + '\'' +
                ", trackRecord=" + trackRecord +
                ", trackCondition='" + trackCondition + '\'' +
                '}';
    }

    enum Surface {
        DIRT("Dirt"),
        TURF("Turf"),
        SYNTHETIC("Synthetic");

        private String text;

        Surface(String text) {
            this.text = text;
        }

        public static Surface forText(String text) {
            for (Surface surface : values()) {
                if (surface.getText().equals(text)) {
                    return surface;
                }
            }
            return null;
        }

        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return "Surface{" +
                    "text='" + text + '\'' +
                    '}';
        }
    }

    enum Format {
        FLAT("Flat"),
        JUMPS("Jumps");

        private String text;

        Format(String text) {
            this.text = text;
        }

        public static Format forText(String text) {
            for (Format surface : values()) {
                if (surface.getText().equals(text)) {
                    return surface;
                }
            }
            return null;
        }

        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return "Format{" +
                    "text='" + text + '\'' +
                    '}';
        }
    }

    /**
     * Stores the textual description of the race distance, the distance expressed in feet and
     * furlongs, a compact description of the race distance and whether the distance is exact or
     * estimated ("About")
     */
    @JsonPropertyOrder({"text", "compact", "feet", "furlongs", "exact", "runUp"})
    public static class RaceDistance {
        private static final Map<Integer, String> COMPACTS;

        static {
            Map<Integer, String> compacts = new LinkedHashMap<>();
            compacts.put(450, "150y");
            compacts.put(660, "1f");
            compacts.put(1320, "2f");
            compacts.put(1650, "2 1/2f");
            compacts.put(1980, "3f");
            compacts.put(2145, "3 1/4f");
            compacts.put(2310, "3 1/2f");
            compacts.put(2475, "3 3/4f");
            compacts.put(2640, "4f");
            compacts.put(2970, "4 1/2f");
            compacts.put(3000, "1000y");
            compacts.put(3300, "5f");
            compacts.put(3465, "5 1/4f");
            compacts.put(3630, "5 1/2f");
            compacts.put(3960, "6f");
            compacts.put(4290, "6 1/2f");
            compacts.put(4620, "7f");
            compacts.put(4950, "7 1/2f");
            compacts.put(5280, "1m");
            compacts.put(5370, "1m 30y");
            compacts.put(5400, "1m 40y");
            compacts.put(5490, "1m 70y");
            compacts.put(5610, "1 1/16m");
            compacts.put(5940, "1 1/8m");
            compacts.put(6270, "1 3/16m");
            compacts.put(6600, "1 1/4m");
            compacts.put(6930, "1 5/16m");
            compacts.put(7260, "1 3/8m");
            compacts.put(7590, "1 7/16m");
            compacts.put(7920, "1 1/2m");
            compacts.put(8250, "1 9/16m");
            compacts.put(8580, "1 5/8m");
            compacts.put(8910, "1 11/16m");
            compacts.put(9240, "1 3/4m");
            compacts.put(9570, "1 13/16m");
            compacts.put(9900, "1 7/8m");
            compacts.put(10230, "1 15/16m");
            compacts.put(10560, "2m");
            compacts.put(10680, "2m 40y");
            compacts.put(10770, "2m 70y");
            compacts.put(10890, "2 1/16m");
            compacts.put(11220, "2 1/8m");
            compacts.put(11550, "2 3/16m");
            compacts.put(11880, "2 1/4m");
            compacts.put(12210, "2 5/16m");
            compacts.put(15840, "3m");
            compacts.put(17160, "3 1/4f");
            compacts.put(18480, "3 1/2f");
            compacts.put(21120, "4m");
            COMPACTS = Collections.unmodifiableMap(compacts);
        }

        private final String text;
        private final String compact;
        private final boolean exact;
        private final int feet;
        private final double furlongs;
        private Integer runUp;
        private Integer tempRail;

        RaceDistance(String text, String compact, boolean exact, int feet) {
            this(text, compact, exact, feet, null, null);
        }

        @JsonCreator
        public RaceDistance(String text, String compact, boolean exact, int feet, Integer runUp,
                Integer tempRail) {
            this.text = text;
            this.compact = compact;
            this.exact = exact;
            this.feet = feet;
            this.furlongs = Chart.round((double) feet / 660, 2).doubleValue();
            this.runUp = runUp;
            this.tempRail = tempRail;
        }

        public static String lookupCompact(int feet) {


            if (COMPACTS.containsKey(feet)) {
                return COMPACTS.get(feet);
            }

            return null;
        }

        public String getText() {
            return text;
        }

        public String getCompact() {
            return compact;
        }

        public boolean isExact() {
            return exact;
        }

        public int getFeet() {
            return feet;
        }

        public double getFurlongs() {
            return furlongs;
        }

        public Integer getRunUp() {
            return runUp;
        }

        public void setRunUp(Integer runUp) {
            this.runUp = runUp;
        }

        public Integer getTempRail() {
            return tempRail;
        }

        public void setTempRail(Integer tempRail) {
            this.tempRail = tempRail;
        }

        @Override
        public String
        toString() {
            return "RaceDistance{" +
                    "text='" + text + '\'' +
                    ", compact='" + compact + '\'' +
                    ", exact=" + exact +
                    ", feet=" + feet +
                    ", furlongs=" + furlongs +
                    ", runUp=" + runUp +
                    ", tempRail=" + tempRail +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RaceDistance that = (RaceDistance) o;

            if (exact != that.exact) return false;
            if (feet != that.feet) return false;
            if (Double.compare(that.furlongs, furlongs) != 0) return false;
            if (text != null ? !text.equals(that.text) : that.text != null) return false;
            if (compact != null ? !compact.equals(that.compact) : that.compact != null)
                return false;
            if (runUp != null ? !runUp.equals(that.runUp) : that.runUp != null) return false;
            return tempRail != null ? tempRail.equals(that.tempRail) : that.tempRail == null;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = text != null ? text.hashCode() : 0;
            result = 31 * result + (compact != null ? compact.hashCode() : 0);
            result = 31 * result + (exact ? 1 : 0);
            result = 31 * result + feet;
            temp = Double.doubleToLongBits(furlongs);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (runUp != null ? runUp.hashCode() : 0);
            result = 31 * result + (tempRail != null ? tempRail.hashCode() : 0);
            return result;
        }
    }

    /**
     * Stories the name of the track record holder, the track record time (as both a String
     * and in
     * milliseconds) and the date when the record was set
     */
    public static class TrackRecord {
        @JsonIgnoreProperties({"color", "sex", "sire", "dam", "damSire", "foalingDate",
                "foalingLocation", "breeder"})
        private final Horse holder;
        private final String time;
        private final Long millis;
        private final LocalDate raceDate;

        public TrackRecord(Horse holder, String time, Long millis, LocalDate raceDate) {
            this.holder = holder;
            this.time = time;
            this.millis = millis;
            this.raceDate = raceDate;
        }

        public Horse getHolder() {
            return holder;
        }

        public String getTime() {
            return time;
        }

        public Long getMillis() {
            return millis;
        }

        public LocalDate getRaceDate() {
            return raceDate;
        }

        @Override
        public String toString() {
            return "TrackRecord{" +
                    "holder='" + holder + '\'' +
                    ", time='" + time + '\'' +
                    ", millis=" + millis +
                    ", raceDate=" + raceDate +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TrackRecord that = (TrackRecord) o;

            if (holder != null ? !holder.equals(that.holder) : that.holder != null)
                return false;
            if (time != null ? !time.equals(that.time) : that.time != null) return false;
            if (millis != null ? !millis.equals(that.millis) : that.millis != null)
                return false;
            return raceDate != null ? raceDate.equals(that.raceDate) : that.raceDate == null;
        }

        @Override
        public int hashCode() {
            int result = holder != null ? holder.hashCode() : 0;
            result = 31 * result + (time != null ? time.hashCode() : 0);
            result = 31 * result + (millis != null ? millis.hashCode() : 0);
            result = 31 * result + (raceDate != null ? raceDate.hashCode() : 0);
            return result;
        }
    }

    private static class SurfaceCourseFormat {
        private static final Map<String, SurfaceCourseFormat> SURFACE_COURSE_TYPES;

        static {
            Map<String, SurfaceCourseFormat> surfaceCourseTypes = new LinkedHashMap<>();
            surfaceCourseTypes.put("Dirt", new SurfaceCourseFormat(DIRT, "Dirt", FLAT));
            surfaceCourseTypes.put("Turf", new SurfaceCourseFormat(TURF, "Turf", FLAT));
            surfaceCourseTypes.put("All Weather Track",
                    new SurfaceCourseFormat(SYNTHETIC, "All Weather Track", FLAT));
            surfaceCourseTypes.put("Inner track",
                    new SurfaceCourseFormat(DIRT, "Inner Track", FLAT));
            surfaceCourseTypes.put("Inner turf", new SurfaceCourseFormat(TURF, "Inner Turf",
                    FLAT));
            surfaceCourseTypes.put("Hurdle", new SurfaceCourseFormat(TURF, "Hurdle", JUMPS));
            surfaceCourseTypes.put("Downhill turf",
                    new SurfaceCourseFormat(TURF, "Downhill Turf", FLAT));
            surfaceCourseTypes.put("Outer turf", new SurfaceCourseFormat(TURF, "Outer Turf",
                    FLAT));
            surfaceCourseTypes.put("Timber", new SurfaceCourseFormat(TURF, "Timber", JUMPS));
            surfaceCourseTypes.put("Steeplechase",
                    new SurfaceCourseFormat(TURF, "Steeplechase", JUMPS));
            surfaceCourseTypes.put("Hunt on turf",
                    new SurfaceCourseFormat(TURF, "Hunt On Turf", JUMPS));
            SURFACE_COURSE_TYPES = Collections.unmodifiableMap(surfaceCourseTypes);
        }

        private final Surface surface;
        private final String course;
        private final Format format;

        SurfaceCourseFormat(Surface surface, String course, Format format) {
            this.surface = surface;
            this.course = course;
            this.format = format;
        }

        static SurfaceCourseFormat fromCourse(String course) {
            return SURFACE_COURSE_TYPES.get(course);
        }

        public Surface getSurface() {
            return surface;
        }

        public String getCourse() {
            return course;
        }

        public Format getFormat() {
            return format;
        }

        @Override
        public String toString() {
            return "SurfaceCourseFormat{" +
                    "surface=" + surface +
                    ", course='" + course + '\'' +
                    ", format=" + format +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SurfaceCourseFormat that = (SurfaceCourseFormat) o;

            if (surface != that.surface) return false;
            if (course != null ? !course.equals(that.course) : that.course != null)
                return false;
            return format == that.format;
        }

        @Override
        public int hashCode() {
            int result = surface != null ? surface.hashCode() : 0;
            result = 31 * result + (course != null ? course.hashCode() : 0);
            result = 31 * result + (format != null ? format.hashCode() : 0);
            return result;
        }
    }

    public static class NoRaceDistanceFound extends ChartParserException {
        public NoRaceDistanceFound(String distanceSurfaceTrackRecord) {
            super(String.format("Unable to identify a valid race distance, surface, and/or track " +
                    "record: %s", distanceSurfaceTrackRecord));
        }
    }
}
