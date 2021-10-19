package com.robinhowlett.chartparser.charts.pdf.wagering;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.robinhowlett.chartparser.charts.pdf.Chart;
import com.robinhowlett.chartparser.charts.pdf.ChartCharacter;
import com.robinhowlett.chartparser.charts.pdf.Horse;
import com.robinhowlett.chartparser.charts.pdf.Starter;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools.WinPlaceShowPayoffPool
        .WinPlaceShowPayoff;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools.WinPlaceShowPayoffPool
        .WinPlaceShowPayoff.Win;
import com.robinhowlett.chartparser.exceptions.ChartParserException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Locale.US;

/**
 * Parses the wagering grid and stores {@link WinPlaceShowPayoffPool} and {@link ExoticPayoffPool}
 * instances
 */
public class WagerPayoffPools {

    static final Pattern TOTAL_WPS_POOL =
            Pattern.compile("Total WPS Pool: \\$([0-9]{1,3}(,[0-9]{3})*)( .+)?");
    static List<String> WAGERING_COLUMN_NAMES = Arrays.asList("Pgm", "Horse", "Win",
            "Place", "Show", "WagerType", "WinningNumbers", "Payoff", "Pool", "Carryover");

    private static final Logger LOGGER = LoggerFactory.getLogger(WagerPayoffPools.class);

    @JsonProperty("winPlaceShow")
    private final WinPlaceShowPayoffPool winPlaceShowPayoffPools;
    @JsonProperty("exotics")
    private final List<ExoticPayoffPool> exoticPayoffPools;

    @JsonCreator
    public WagerPayoffPools(WinPlaceShowPayoffPool winPlaceShowPayoffPools,
            List<ExoticPayoffPool> exoticPayoffPools) {
        this.winPlaceShowPayoffPools = winPlaceShowPayoffPools;
        this.exoticPayoffPools = exoticPayoffPools;
    }

    public static WagerPayoffPools parse(List<List<ChartCharacter>> lines)
            throws ChartParserException {
        for (List<ChartCharacter> line : lines) {
            String text = Chart.convertToText(line);
            Integer totalWinPlaceShowPool = parseTotalWinPlaceShowPool(text);
            if (totalWinPlaceShowPool != null) {
                List<ChartCharacter> wageringLine = getWageringLine(line);
                return parsePayoffs(totalWinPlaceShowPool, wageringLine);
            }
        }
        return null;
    }

    static Integer parseTotalWinPlaceShowPool(String text) throws TotalWPSParseException {
        Matcher matcher = TOTAL_WPS_POOL.matcher(text);
        if (matcher.find()) {
            String totalWinPlaceShowPool = matcher.group(1);
            try {
                return NumberFormat.getNumberInstance(US).parse(totalWinPlaceShowPool).intValue();
            } catch (ParseException e) {
                throw new TotalWPSParseException(String.format("Unable to parse Total WPS Pool: " +
                        "%s", text), e);
            }
        }
        return null;
    }

    /**
     * Extract the characters related to the wagering grid
     */
    static List<ChartCharacter> getWageringLine(List<ChartCharacter> line)
            throws ChartParserException {
        ChartCharacter previous = null;
        int index = -1;
        for (int i = 0; i < line.size(); i++) {
            ChartCharacter chartCharacter = line.get(i);
            if (previous != null) {
                if (chartCharacter.getyDirAdj() != previous.getyDirAdj()) {
                    index = i;
                    break;
                }
            }
            previous = chartCharacter;
        }
        if (index < 0) {
            throw new ChartParserException("No wagering grid found");
        }
        return line.subList(index, line.size());
    }

    /**
     * Create a wagering grid and parse the
     */
    static WagerPayoffPools parsePayoffs(Integer totalWinPlaceShowPool,
            List<ChartCharacter> wageringLine) throws ChartParserException {
        List<WinPlaceShowPayoff> winPlaceShowPayoffs = new ArrayList<>();
        List<ExoticPayoffPool> exoticPayoffPools = new ArrayList<>();

        double headerYDirAdj;
        if (wageringLine != null && !wageringLine.isEmpty()) {
            headerYDirAdj = wageringLine.get(0).getyDirAdj();
        } else {
            throw new ChartParserException("Unable to parse payoffs as wagering line is invalid");
        }

        // group by line, to establish the rows of the grid
        Map<Double, List<ChartCharacter>> wageringLinesByLine =
                organizeWageringLinesByLine(wageringLine);

        // the header column names
        List<ChartCharacter> wageringHeaderLine = wageringLinesByLine.remove(headerYDirAdj);

        // establishes the boundary horizontal positions of the columns e.g. "Pgm" goes from X to
        // Y, so all characters within those bounds will be assigned to "Pgm"
        Map<String, ColumnRange> wageringHeaderColumns =
                populateHeaderColumns(wageringHeaderLine, WAGERING_COLUMN_NAMES);

        // the floors are used to calculate the appropriate column to associate a character with
        WageringTreeSet wageringFloors =
                WageringColumn.calculateColumnFloors(wageringHeaderColumns);

        // now that the rows and column dimensions have been established, grouped the characters
        // into the appropriate cell within the grid
        Map<Double, Map<String, List<ChartCharacter>>> wageringGrid =
                createWageringGrid(wageringLinesByLine, wageringFloors);

        // combine rows that, due to the length of the column's text, have been wrapped and
        // continue on the next line
        wageringGrid = mergeColumnsSplitBetweenLines(wageringGrid);

        for (Double lineKey : wageringGrid.keySet()) {
            Map<String, List<ChartCharacter>> wageringGridRow = wageringGrid.get(lineKey);
            WinPlaceShowPayoff winPlaceShowPayoff = WinPlaceShowPayoff.parse(wageringGridRow);
            if (isWinPlaceOrShowPayoffPresent(winPlaceShowPayoff)) {
                winPlaceShowPayoffs.add(winPlaceShowPayoff);
            }

            ExoticPayoffPool exoticPayoffPool = ExoticPayoffPool.parse(wageringGridRow);
            if (isExoticPayoffPresent(exoticPayoffPool)) {
                exoticPayoffPools.add(exoticPayoffPool);
            }
        }

        WinPlaceShowPayoffPool winPlaceShowPayoffPool =
                new WinPlaceShowPayoffPool(totalWinPlaceShowPool, winPlaceShowPayoffs);

        return new WagerPayoffPools(winPlaceShowPayoffPool, exoticPayoffPools);
    }

    /**
     * Splits the list of {@link ChartCharacter}s that comprise the wagering grid into a Map, where
     * the characters are grouped by what line (vertical position) they are present on
     */
    static Map<Double, List<ChartCharacter>> organizeWageringLinesByLine(
            List<ChartCharacter> wageringLine) {
        Map<Double, List<ChartCharacter>> wageringGridByLine = new LinkedHashMap<>();
        for (ChartCharacter chartCharacter : wageringLine) {
            double yDirAdj = chartCharacter.getyDirAdj();

            // exclude lines that are not part of the wagering grid by identifying the rows that
            // have too much white space above them to suggest they are not part of the grid
            // e.g. 2015 KY Derby, Preakness, Belmont charts' Taylor Made sponsorships
            Optional<Double> max = wageringGridByLine.keySet().stream().max(Double::compareTo);
            if (max.isPresent() && (yDirAdj > (max.get() + 10d))) {
                continue;
            }

            if (!wageringGridByLine.containsKey(yDirAdj)) {
                List<ChartCharacter> chartCharacters = new ArrayList<>();
                chartCharacters.add(chartCharacter);
                wageringGridByLine.put(yDirAdj, chartCharacters);
            } else {
                wageringGridByLine.get(yDirAdj).add(chartCharacter);
            }
        }
        return wageringGridByLine;
    }

    private static boolean isExoticPayoffPresent(ExoticPayoffPool exoticPayoffPool) {
        return (exoticPayoffPool.getUnit() != null &&
                (exoticPayoffPool.getWinningNumbers() != null &&
                        !exoticPayoffPool.getWinningNumbers().isEmpty()));
    }

    private static boolean isWinPlaceOrShowPayoffPresent(WinPlaceShowPayoff winPlaceShowPayoff) {
        return (winPlaceShowPayoff.getWin() != null || winPlaceShowPayoff.getPlace() != null
                || winPlaceShowPayoff.getShow() != null);
    }

    /**
     * Sometimes the length of the Wager Type or the Winning Numbers text wraps to the next line.
     * This method attempts to detect these "false rows" and merge the affected columns with those
     * in the row above
     */
    static Map<Double, Map<String, List<ChartCharacter>>> mergeColumnsSplitBetweenLines(
            Map<Double, Map<String, List<ChartCharacter>>> wageringGrid) {
        Map<String, List<ChartCharacter>> previousWageringGridRow = null;
        for (Double lineKey : wageringGrid.keySet()) {
            Map<String, List<ChartCharacter>> wageringGridRow = wageringGrid.get(lineKey);
            if (previousWageringGridRow != null) {
                List<ChartCharacter> winningNumbersPayoff =
                        wageringGridRow.get("WinningNumbersPayoff");

                List<ChartCharacter> wagerType = wageringGridRow.get("WagerType");

                if (wagerType == null && winningNumbersPayoff != null) {
                    List<ChartCharacter> previousWinningNumbersPayoff =
                            previousWageringGridRow.get("WinningNumbersPayoff");

                    if (previousWinningNumbersPayoff != null) {
                        previousWinningNumbersPayoff.addAll(winningNumbersPayoff);
                        wageringGridRow.put("WinningNumbersPayoff", null);
                    }
                } else if (wagerType != null && winningNumbersPayoff == null) {
                    List<ChartCharacter> previousWagerType =
                            previousWageringGridRow.get("WagerType");
                    if (previousWagerType != null) {
                        previousWagerType.addAll(wagerType);
                        wageringGridRow.put("WagerType", null);
                    }
                } else {
                    if (wagerType != null && !wagerType.isEmpty() && wagerType.get(0).getUnicode
                            () != '$' &&
                            winningNumbersPayoff != null) {
                        List<ChartCharacter> previousWagerType =
                                previousWageringGridRow.get("WagerType");
                        if (previousWagerType != null) {
                            previousWagerType.addAll(wagerType);
                            wageringGridRow.put("WagerType", null);
                        }

                        List<ChartCharacter> previousWinningNumbersPayoff =
                                previousWageringGridRow.get("WinningNumbersPayoff");

                        if (previousWinningNumbersPayoff != null) {
                            previousWinningNumbersPayoff.addAll(winningNumbersPayoff);
                            wageringGridRow.put("WinningNumbersPayoff", null);
                        }
                    } else {
                        previousWageringGridRow = wageringGridRow;
                    }
                }
            } else {
                previousWageringGridRow = wageringGridRow;
            }
        }
        return wageringGrid;
    }

    /**
     * Builds a Map representing rows and columns of the wagering grid, with each {@link
     * ChartCharacter} within the grid assigned to the appropriate cell within that grid
     */
    static Map<Double, Map<String, List<ChartCharacter>>> createWageringGrid(
            Map<Double, List<ChartCharacter>> wageringGridByLine, WageringTreeSet wageringFloors) {
        Map<Double, Map<String, List<ChartCharacter>>> wageringGrid = new LinkedHashMap<>();
        for (Double lineYDirAdj : wageringGridByLine.keySet()) {
            Map<String, List<ChartCharacter>> charsByColumn = new LinkedHashMap<>();
            List<ChartCharacter> charactersForLine = wageringGridByLine.get(lineYDirAdj);
            for (ChartCharacter chartCharacter : charactersForLine) {
                WageringColumn forFloor = new WageringColumn(chartCharacter.getxDirAdj());
                WageringColumn wageringColumn = wageringFloors.floor(forFloor);

                if (charsByColumn.containsKey(wageringColumn.getColumnName())) {
                    charsByColumn.get(wageringColumn.getColumnName()).add(chartCharacter);
                } else {
                    List<ChartCharacter> charactersForColumn = new ArrayList<>();
                    charactersForColumn.add(chartCharacter);
                    charsByColumn.put(wageringColumn.getColumnName(), charactersForColumn);
                }
            }

            wageringGrid.put(lineYDirAdj, charsByColumn);
        }
        return wageringGrid;
    }

    /**
     * For each wagering grid header, create the location bounds for characters that are to be
     * associated with that column
     */
    static Map<String, ColumnRange> populateHeaderColumns(List<ChartCharacter> wagerPoolsHeader,
            List<String> headerColumnNames) {
        boolean winAlreadyFound = false;
        StringBuffer sb = null;
        List<ChartCharacter> progress = null;
        List<ChartCharacter> covered = new ArrayList<>();

        Map<String, ColumnRange> headerColumns = new LinkedHashMap<>();

        for (ChartCharacter pdfCharacter : wagerPoolsHeader) {
            if (sb == null) {
                sb = new StringBuffer();
                progress = new ArrayList<>();
            }

            progress.add(pdfCharacter);
            sb.append(pdfCharacter.getUnicode());

            String text = sb.toString();

            // account for the fact Win is a substring of WinningNumbers
            if (text.equals("Win")) {
                if (winAlreadyFound) {
                    continue;
                } else {
                    winAlreadyFound = true;
                }
            }

            for (String headerColumnName : headerColumnNames) {
                if (text.equals(headerColumnName)) {
                    double left = Chart.round(progress.get(0).getxDirAdj()).doubleValue();
                    ChartCharacter rightChartCharacter = progress.get(progress.size() - 1);
                    double right = Chart.round(rightChartCharacter.getxDirAdj() +
                            rightChartCharacter.getWidthDirAdj()).doubleValue();
                    ColumnRange columnRange = new ColumnRange(left, right);
                    headerColumns.put(headerColumnName, columnRange);
                    covered.addAll(progress);
                    sb = null;
                    break;
                }
            }
        }

        wagerPoolsHeader.removeAll(covered);

        return headerColumns;
    }

    /**
     * A helper class to store the location of where a column starts and ends ("end" meaning the
     * rightmost-edge of the last character)
     */
    public static class ColumnRange {
        private final double left;
        private final double right;

        public ColumnRange(double left, double right) {
            this.left = left;
            this.right = right;
        }

        public double getLeft() {
            return left;
        }

        public double getRight() {
            return right;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ColumnRange that = (ColumnRange) o;

            if (Double.compare(that.left, left) != 0) return false;
            return Double.compare(that.right, right) == 0;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(left);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(right);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "ColumnRange{" +
                    "left=" + left +
                    ", right=" + right +
                    '}';
        }
    }

    abstract static class Wager {
        protected final Double unit;
        protected final Double payoff;
        protected Double odds; // for JSON

        public Wager(Double unit, Double payoff) {
            this.unit = unit;
            this.payoff = payoff;
        }

        public Double getUnit() {
            return unit;
        }

        public Double getPayoff() {
            return payoff;
        }

        public Double getOdds() {
            if (unit != null && payoff != null && unit > 0 && payoff > 0) {
                double calc = ((payoff - unit) / unit);
                if (!Double.isInfinite(calc)) {
                    return Chart.round(calc).doubleValue();
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Wager wager = (Wager) o;

            if (unit != null ? !unit.equals(wager.unit) : wager.unit != null)
                return false;
            return payoff != null ? payoff.equals(wager.payoff) : wager.payoff == null;
        }

        @Override
        public int hashCode() {
            int result = unit != null ? unit.hashCode() : 0;
            result = 31 * result + (payoff != null ? payoff.hashCode() : 0);
            return result;
        }
    }

    /**
     * Stores the total Win-Place-Show (WPS) pool amount, and a list of the various {@link
     * WinPlaceShowPayoff} wager details
     */
    public static class WinPlaceShowPayoffPool {

        @JsonProperty("totalWPSPool")
        private final Integer totalWinPlaceShowPool;
        @JsonProperty("payoffs")
        private final List<WinPlaceShowPayoff> winPlaceShowPayoffs;

        @JsonCreator
        public WinPlaceShowPayoffPool(Integer totalWinPlaceShowPool,
                List<WinPlaceShowPayoff> winPlaceShowPayoffs) {
            this.totalWinPlaceShowPool = totalWinPlaceShowPool;
            this.winPlaceShowPayoffs = winPlaceShowPayoffs;
        }

        public Integer getTotalWinPlaceShowPool() {
            return totalWinPlaceShowPool;
        }

        public List<WinPlaceShowPayoff> getWinPlaceShowPayoffs() {
            return winPlaceShowPayoffs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WinPlaceShowPayoffPool that = (WinPlaceShowPayoffPool) o;

            if (totalWinPlaceShowPool != null ? !totalWinPlaceShowPool.equals(that
                    .totalWinPlaceShowPool) : that.totalWinPlaceShowPool != null)
                return false;
            return winPlaceShowPayoffs != null ? winPlaceShowPayoffs.equals(that
                    .winPlaceShowPayoffs) : that.winPlaceShowPayoffs == null;
        }

        @Override
        public int hashCode() {
            int result = totalWinPlaceShowPool != null ? totalWinPlaceShowPool.hashCode() : 0;
            result = 31 * result + (winPlaceShowPayoffs != null ? winPlaceShowPayoffs.hashCode()
                    : 0);
            return result;
        }


        /**
         * For Win-Place-Show (WPS) wager payoffs, this stores the program number of the {@link
         * Starter}, the horse name, and, where applicable, the win, place, and tshow payoff
         * amounts. All payoffs amounts are based on a $2 unit.
         */
        public static class WinPlaceShowPayoff {
            private final String program;
            @JsonIgnoreProperties({"color", "sex", "sire", "dam", "damSire", "foalingDate",
                    "foalingLocation", "breeder"})
            private final Horse horse;
            private final Win win;
            private final Place place;
            private final Show show;
            @JsonIgnore
            private final List<WinPlaceShow> winPlaceShows;

            @JsonCreator
            public WinPlaceShowPayoff(String program, Horse horse, Win win, Place place,
                    Show show) {
                this.program = program;
                this.horse = horse;
                this.win = win;
                this.place = place;
                this.show = show;

                winPlaceShows = new ArrayList<>();
                if (win != null) {
                    winPlaceShows.add(win);
                }
                if (place != null) {
                    winPlaceShows.add(place);
                }
                if (show != null) {
                    winPlaceShows.add(show);
                }
            }

            WinPlaceShowPayoff(String program, HorseNameWin horseNameWin, Double placePayoff,
                    Double showPayoff) {
                this(
                        (program != null ? program.toUpperCase() : null),
                        (horseNameWin != null ? horseNameWin.getHorse() : null),
                        (horseNameWin != null ? horseNameWin.getWin() : null),
                        (placePayoff != null ? new Place(placePayoff) : null),
                        (showPayoff != null ? new Show(showPayoff) : null)
                );
            }

            static WinPlaceShowPayoff parse(Map<String, List<ChartCharacter>> wageringGridRow) {
                String program = Chart.convertToText(wageringGridRow.get("Pgm"));

                String horseWinText = Chart.convertToText(wageringGridRow.get("HorseWin"));
                HorseNameWin horseNameWin = parseHorseNameAndWinPayoff(horseWinText);

                Double place = null;

                String placeText = Chart.convertToText(wageringGridRow.get("Place"));
                if (!placeText.isEmpty()) {
                    try {
                        place = NumberFormat.getNumberInstance(US).parse(placeText).doubleValue();
                    } catch (ParseException e) {
                        LOGGER.warn(String.format("Unable to parse place payoff %s", placeText), e);
                    }
                }

                String showText = Chart.convertToText(wageringGridRow.get("Show"));
                Double show = null;
                if (!showText.isEmpty()) {
                    try {
                        show = NumberFormat.getNumberInstance(US).parse(showText).doubleValue();
                    } catch (ParseException e) {
                        LOGGER.warn(String.format("Unable to parse show payoff %s", showText), e);
                    }

                }

                return new WinPlaceShowPayoff(program, horseNameWin, place, show);
            }

            static HorseNameWin parseHorseNameAndWinPayoff(String horseWinText) {
                String horseName = null;
                Double win = null;
                String[] horseWin = horseWinText.split("\\|");
                if (horseWin.length > 0) {
                    horseName = horseWin[0];
                    if (horseWin.length == 2) {
                        try {
                            win = NumberFormat.getNumberInstance(US).parse(horseWin[1])
                                    .doubleValue();
                        } catch (ParseException e) {
                            LOGGER.warn(String.format("Unable to parse win payoff %s",
                                    horseWin[1]), e);
                        }
                    }
                }
                return new HorseNameWin(new Horse(horseName), win);
            }

            public String getProgram() {
                return program;
            }

            @JsonIgnore
            public String getEntryProgram() {
                return Chart.getEntryProgram(program);
            }

            public Horse getHorse() {
                return horse;
            }

            public Win getWin() {
                return win;
            }

            public Place getPlace() {
                return place;
            }

            public Show getShow() {
                return show;
            }

            public List<WinPlaceShow> getWinPlaceShows() {
                return winPlaceShows;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                WinPlaceShowPayoff that = (WinPlaceShowPayoff) o;
                return Objects.equals(program, that.program) &&
                        Objects.equals(horse, that.horse) &&
                        Objects.equals(win, that.win) &&
                        Objects.equals(place, that.place) &&
                        Objects.equals(show, that.show) &&
                        Objects.equals(winPlaceShows, that.winPlaceShows);
            }

            @Override
            public int hashCode() {
                return Objects.hash(program, horse, win, place, show, winPlaceShows);
            }

            @Override
            public String toString() {
                return "WinPlaceShowPayoff{" +
                        "program='" + program + '\'' +
                        ", horse=" + horse +
                        ", win=" + win +
                        ", place=" + place +
                        ", show=" + show +
                        ", winPlaceShows=" + winPlaceShows +
                        '}';
            }

            public abstract static class WinPlaceShow extends Wager {
                private final String type;

                // $2 default for WPS
                public WinPlaceShow(Double payoff, WPSType type) {
                    super(2.0, payoff);
                    this.type = type.getName();
                }

                public String getType() {
                    return type;
                }

                @Override
                public String toString() {
                    return "WinPlaceShow{" +
                            "unit=" + unit +
                            ", payoff=" + payoff +
                            ", odds=" + odds +
                            ", type='" + type + '\'' +
                            '}';
                }
            }

            enum WPSType {
                WIN("Win"),
                PLACE("Place"),
                SHOW("Show");

                private final String name;

                WPSType(String name) {
                    this.name = name;
                }

                public String getName() {
                    return name;
                }

                @Override
                public String toString() {
                    return "WPSType{" +
                            "type='" + name + '\'' +
                            '}';
                }
            }

            public static class Win extends WinPlaceShow {
                public Win(Double payoff) {
                    super(payoff, WPSType.WIN);
                }

                @Override
                public String toString() {
                    return "Win{" +
                            "unit=" + unit +
                            ", payoff=" + payoff +
                            '}';
                }
            }

            public static class Place extends WinPlaceShow {
                public Place(Double payoff) {
                    super(payoff, WPSType.PLACE);
                }

                @Override
                public String toString() {
                    return "Place{" +
                            "unit=" + unit +
                            ", payoff=" + payoff +
                            '}';
                }
            }

            public static class Show extends WinPlaceShow {
                public Show(Double payoff) {
                    super(payoff, WPSType.SHOW);
                }

                @Override
                public String toString() {
                    return "Show{" +
                            "unit=" + unit +
                            ", payoff=" + payoff +
                            '}';
                }
            }
        }

    }

    /**
     * For a particular exotic wager, parses and stories, where applicable, the name of the exotic
     * bet, the wager unit the bet is based on, a textual description of the winning numbers, the
     * number of correct selections required, the payoff amount, and the pool and carryover size
     */
    @JsonPropertyOrder({"unit", "name", "winningNumbers", "numberCorrect", "payoff", "odds",
            "pool", "carryover"})
    public static class ExoticPayoffPool extends Wager {
        private final static Pattern WAGER_UNIT = Pattern.compile("^\\$(\\d\\.\\d\\d) (.+)$");
        private final static Pattern WINNING_NUMBERS =
                Pattern.compile("^([^\\(]+)(\\((\\d+) correct\\))?");

        private final String name;
        private final String winningNumbers;
        private final Integer numberCorrect;
        private final Double pool;
        private final Double carryover;

        public ExoticPayoffPool(WagerNameUnit wagerNameUnit,
                WinningNumbersPayoff winningNumbersPayoff, Double pool, Double carryover) {
            super((wagerNameUnit != null ? wagerNameUnit.getWagerUnit() : null),
                    (winningNumbersPayoff != null ? winningNumbersPayoff.getPayoff() : null));
            this.name = (wagerNameUnit != null ? wagerNameUnit.getName() : null);
            this.winningNumbers = (winningNumbersPayoff != null ?
                    winningNumbersPayoff.getWinningNumbers() : null);
            this.numberCorrect = (winningNumbersPayoff != null ?
                    winningNumbersPayoff.getNumberCorrect() : null);
            this.pool = pool;
            this.carryover = carryover;
        }

        @JsonCreator
        public ExoticPayoffPool(Double unit, Double payoff, String name, String winningNumbers,
                Integer numberCorrect, Double pool, Double carryover) {
            super(unit, payoff);
            this.name = name;
            this.winningNumbers = winningNumbers;
            this.numberCorrect = numberCorrect;
            this.pool = pool;
            this.carryover = carryover;
        }

        static ExoticPayoffPool parse(Map<String, List<ChartCharacter>> payoffGrid)
                throws ChartParserException {
            List<ChartCharacter> chartCharacters = payoffGrid.get("WagerType");
            String wagerType = Chart.convertToText(chartCharacters);
            WagerNameUnit wagerNameUnit = parseWagerType(wagerType);

            String winningNumbersPayoffText = Chart.convertToText(
                    payoffGrid.get("WinningNumbersPayoff"));
            WinningNumbersPayoff winningNumbersPayoff =
                    parseWinningNumbersAndPayoff(winningNumbersPayoffText);

            String poolText = Chart.convertToText(payoffGrid.get("Pool"));
            Double pool = parsePool(poolText);

            String carryoverText = Chart.convertToText(payoffGrid.get("Carryover"));
            Double carryover = parseCarryover(carryoverText);

            return new ExoticPayoffPool(wagerNameUnit, winningNumbersPayoff, pool, carryover);
        }

        static WagerNameUnit parseWagerType(String wagerType) {
            Double wagerAmount = null;
            String name = null;

            // handle multi-line wager type descriptions
            if (wagerType.contains(System.lineSeparator())) {
                int newLineIndex = wagerType.indexOf(System.lineSeparator());
                String wagerTypeOnNewLine =
                        wagerType.substring(newLineIndex + System.lineSeparator().length());
                wagerType = wagerType.substring(0, newLineIndex).concat(" ")
                        .concat(wagerTypeOnNewLine);
            }

            Matcher matcher = WAGER_UNIT.matcher(wagerType);
            if (matcher.find()) {
                String wagerAmountText = matcher.group(1);
                wagerAmount = Double.parseDouble(wagerAmountText);
                name = matcher.group(2);
            }

            return new WagerNameUnit(wagerAmount, name);
        }

        static WinningNumbersPayoff parseWinningNumbersAndPayoff(String winningNumbersPayoffText)
                throws ChartParserException {
            String winningNumbers = null;
            Integer numberCorrect = null;
            Double payoff = null;

            // handle multi-line "winning numbers" text and payoffs
            String wnpOnNewLine = null;
            if (winningNumbersPayoffText.contains(System.lineSeparator())) {
                int newLineIndex = winningNumbersPayoffText.indexOf(System.lineSeparator());
                wnpOnNewLine = winningNumbersPayoffText.substring(newLineIndex +
                        System.lineSeparator().length());
                winningNumbersPayoffText = winningNumbersPayoffText.substring(0, newLineIndex);
            }

            String[] wnp = winningNumbersPayoffText.split("\\|");
            if (wnp.length > 0) {
                winningNumbers = wnp[0];
                if (wnpOnNewLine != null) {
                    winningNumbers = winningNumbers.concat(" ").concat(wnpOnNewLine);
                }

                Matcher matcher = WINNING_NUMBERS.matcher(winningNumbers);
                if (matcher.find()) {
                    winningNumbers = matcher.group(1).trim();
                    if (matcher.group(3) != null) {
                        numberCorrect = Integer.parseInt(matcher.group(3));
                    }
                }

                if (wnp.length == 2) {
                    try {
                        payoff = NumberFormat.getNumberInstance(US).parse(wnp[1]).doubleValue();
                    } catch (ParseException e) {
                        throw new ChartParserException(String.format("Failed to parse payoff " +
                                "text: %s", wnp[1]), e);
                    }
                }
            }

            return new WinningNumbersPayoff(winningNumbers, numberCorrect, payoff);
        }

        private static Double parsePool(String poolText) throws ChartParserException {
            if (poolText != null && !poolText.isEmpty()) {
                try {
                    return NumberFormat.getNumberInstance(US).parse(poolText).doubleValue();
                } catch (ParseException e) {
                    throw new ChartParserException(String.format("Failed to parse pool " +
                            "text: %s", poolText), e);
                }
            }
            return null;
        }

        private static Double parseCarryover(String carryoverText) throws ChartParserException {
            if (carryoverText != null && !carryoverText.isEmpty()) {
                try {
                    return NumberFormat.getNumberInstance(US).parse(carryoverText).doubleValue();
                } catch (ParseException e) {
                    throw new ChartParserException(String.format("Failed to parse carryover " +
                            "text: %s", carryoverText), e);
                }
            }
            return null;
        }

        public String getName() {
            return name;
        }

        public String getWinningNumbers() {
            return winningNumbers;
        }

        public Integer getNumberCorrect() {
            return numberCorrect;
        }

        public Double getPool() {
            return pool;
        }

        public Double getCarryover() {
            return carryover;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExoticPayoffPool that = (ExoticPayoffPool) o;

            if (unit != null ? !unit.equals(that.unit) : that.unit !=
                    null)
                return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (winningNumbers != null ? !winningNumbers.equals(that.winningNumbers) : that
                    .winningNumbers != null)
                return false;
            if (numberCorrect != null ? !numberCorrect.equals(that.numberCorrect) : that
                    .numberCorrect != null)
                return false;
            if (payoff != null ? !payoff.equals(that.payoff) : that.payoff != null) return false;
            if (pool != null ? !pool.equals(that.pool) : that.pool != null) return false;
            return carryover != null ? carryover.equals(that.carryover) : that.carryover == null;
        }

        @Override
        public int hashCode() {
            int result = unit != null ? unit.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (winningNumbers != null ? winningNumbers.hashCode() : 0);
            result = 31 * result + (numberCorrect != null ? numberCorrect.hashCode() : 0);
            result = 31 * result + (payoff != null ? payoff.hashCode() : 0);
            result = 31 * result + (pool != null ? pool.hashCode() : 0);
            result = 31 * result + (carryover != null ? carryover.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ExoticPayoffPool{" +
                    "unit=" + unit +
                    ", name='" + name + '\'' +
                    ", winningNumbers='" + winningNumbers + '\'' +
                    ", numberCorrect=" + numberCorrect +
                    ", payoff=" + payoff +
                    ", pool=" + pool +
                    ", carryover=" + carryover +
                    '}';
        }
    }

    /**
     * Due to the way the wagering grid needs to be parses, the horse name and win payoff are both
     * stored in this object
     */
    static class HorseNameWin {
        private final Horse horse;
        private final Win win;

        HorseNameWin(Horse horse, Double winPayoff) {
            this.horse = horse;
            this.win = (winPayoff != null ? new Win(winPayoff) : null);
        }

        public Horse getHorse() {
            return horse;
        }

        public Win getWin() {
            return win;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HorseNameWin that = (HorseNameWin) o;

            if (horse != null ? !horse.equals(that.horse) : that.horse != null) return false;
            return win != null ? win.equals(that.win) : that.win == null;
        }

        @Override
        public int hashCode() {
            int result = horse != null ? horse.hashCode() : 0;
            result = 31 * result + (win != null ? win.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "HorseNameWin{" +
                    "horse='" + horse + '\'' +
                    ", win=" + win +
                    '}';
        }
    }

    /**
     * For exotic bets, stores the name of the wager and the unit amount the payoff is based on
     */
    public static class WagerNameUnit {
        private final Double wagerUnit;
        private final String name;

        public WagerNameUnit(Double wagerUnit, String name) {
            this.wagerUnit = wagerUnit;
            this.name = name;
        }

        public Double getWagerUnit() {
            return wagerUnit;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WagerNameUnit that = (WagerNameUnit) o;

            if (wagerUnit != null ? !wagerUnit.equals(that.wagerUnit) : that.wagerUnit !=
                    null)
                return false;
            return name != null ? name.equals(that.name) : that.name == null;
        }

        @Override
        public int hashCode() {
            int result = wagerUnit != null ? wagerUnit.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "WagerNameUnit{" +
                    "unit=" + wagerUnit +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    /**
     * For exotics, stories the winning number sequence, how many correct selections were needed for
     * the bet to win (e.g. 6 in a Pick 6), and the payoff
     */
    public static class WinningNumbersPayoff {
        private final String winningNumbers;
        private final Integer numberCorrect;
        private final Double payoff;

        public WinningNumbersPayoff(String winningNumbers, Integer numberCorrect, Double payoff) {
            this.winningNumbers = winningNumbers;
            this.numberCorrect = numberCorrect;
            this.payoff = payoff;
        }

        public String getWinningNumbers() {
            return winningNumbers;
        }

        public Integer getNumberCorrect() {
            return numberCorrect;
        }

        public Double getPayoff() {
            return payoff;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WinningNumbersPayoff that = (WinningNumbersPayoff) o;

            if (winningNumbers != null ? !winningNumbers.equals(that.winningNumbers) : that
                    .winningNumbers != null)
                return false;
            if (numberCorrect != null ? !numberCorrect.equals(that.numberCorrect) : that
                    .numberCorrect != null)
                return false;
            return payoff != null ? payoff.equals(that.payoff) : that.payoff == null;
        }

        @Override
        public int hashCode() {
            int result = winningNumbers != null ? winningNumbers.hashCode() : 0;
            result = 31 * result + (numberCorrect != null ? numberCorrect.hashCode() : 0);
            result = 31 * result + (payoff != null ? payoff.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "WinningNumbersPayoff{" +
                    "winningNumbers='" + winningNumbers + '\'' +
                    ", numberCorrect=" + numberCorrect +
                    ", payoff=" + payoff +
                    '}';
        }
    }

    public WinPlaceShowPayoffPool getWinPlaceShowPayoffPools() {
        return winPlaceShowPayoffPools;
    }

    public List<ExoticPayoffPool> getExoticPayoffPools() {
        return exoticPayoffPools;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WagerPayoffPools that = (WagerPayoffPools) o;

        if (winPlaceShowPayoffPools != null ? !winPlaceShowPayoffPools.equals(that
                .winPlaceShowPayoffPools) : that.winPlaceShowPayoffPools !=
                null)
            return false;
        return exoticPayoffPools != null ? exoticPayoffPools.equals(that.exoticPayoffPools) : that
                .exoticPayoffPools == null;
    }

    @Override
    public int hashCode() {
        int result = winPlaceShowPayoffPools != null ? winPlaceShowPayoffPools.hashCode() : 0;
        result = 31 * result + (exoticPayoffPools != null ? exoticPayoffPools.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WagerPayoffPools{" +
                "winPlaceShowPayoffPools=" + winPlaceShowPayoffPools +
                ", exoticPayoffPools=" + exoticPayoffPools +
                '}';
    }

    public static class TotalWPSParseException extends ChartParserException {
        public TotalWPSParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
