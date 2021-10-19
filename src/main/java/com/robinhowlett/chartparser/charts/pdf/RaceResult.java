package com.robinhowlett.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.robinhowlett.chartparser.ChartParser;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools.WinPlaceShowPayoffPool;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools.WinPlaceShowPayoffPool
        .WinPlaceShowPayoff;
import com.robinhowlett.chartparser.exceptions.ChartParserException;
import com.robinhowlett.chartparser.fractionals.FractionalPoint;
import com.robinhowlett.chartparser.fractionals.FractionalPoint.Fractional;
import com.robinhowlett.chartparser.fractionals.FractionalPoint.Split;
import com.robinhowlett.chartparser.points_of_call.PointsOfCall.PointOfCall;
import com.robinhowlett.chartparser.points_of_call.PointsOfCall.PointOfCall.RelativePosition;
import com.robinhowlett.chartparser.points_of_call.PointsOfCall.PointOfCall.RelativePosition
        .LengthsAhead;
import com.robinhowlett.chartparser.tracks.Track;

import org.springframework.hateoas.Link;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Represents a single race result on a chart. This will normally be initialized by use of the
 * {@link Builder} configured by the {@link ChartParser}
 */
@JsonPropertyOrder({"links", "cancellation", "raceDate", "track", "raceNumber", "conditions",
        "distanceSurfaceTrackRecord", "weather", "postTimeStartCommentsTimer", "deadHeat",
        "numberOfRunners", "finalTime", "finalMillis", "winningMargin", "starters", "scratches",
        "wagering", "fractionals", "splits", "ratings", "footnotes"})
public class RaceResult {

    @JsonInclude(NON_EMPTY)
    private final List<Link> links;
    @JsonProperty("cancellation") // required for property order but unwrapped
    @JsonUnwrapped
    private final Cancellation cancellation;
    private final LocalDate raceDate;
    private final Track track;
    private final Integer raceNumber;
    @JsonProperty("conditions")
    private final RaceConditions raceConditions;
    @JsonProperty("distanceSurfaceTrackRecord") // required for property order but unwrapped
    @JsonUnwrapped
    private final DistanceSurfaceTrackRecord distanceSurfaceTrackRecord;
    private final Weather weather;
    @JsonProperty("postTimeStartCommentsTimer") // required for property order but unwrapped
    @JsonUnwrapped
    private final PostTimeStartCommentsTimer postTimeStartCommentsTimer;
    private final boolean deadHeat;
    private final List<Starter> starters;
    private final List<Scratch> scratches;
    private final List<Fractional> fractionals;
    private final List<Split> splits;
    @JsonProperty("wagering")
    private final WagerPayoffPools wagerPayoffPools;
    private final String footnotes;
    private List<Rating> ratings;

    private RaceResult(Builder builder) {
        this.cancellation = (builder.cancellation != null ? builder.cancellation :
                Cancellation.notCancelled());
        this.raceDate = builder.raceDate;
        this.track = builder.track;
        this.raceNumber = builder.raceNumber;
        this.raceConditions = builder.raceConditions;
        this.distanceSurfaceTrackRecord = builder.distanceSurfaceTrackRecord;
        this.postTimeStartCommentsTimer = builder.postTimeStartCommentsTimer;
        this.deadHeat = builder.deadHeat;
        this.starters = builder.starters;
        this.fractionals = builder.fractionals;
        this.splits = builder.splits;
        this.scratches = builder.scratches;
        this.wagerPayoffPools = builder.wagerPayoffPools;
        this.footnotes = builder.footnotes;

        if (builder.weatherTrackCondition != null) {
            if (distanceSurfaceTrackRecord != null) {
                distanceSurfaceTrackRecord.setTrackCondition(
                        builder.weatherTrackCondition.getTrackCondition());
            }

            weather = new Weather(builder.weatherTrackCondition.getWeather(),
                    builder.windSpeedDirection);
        } else {
            if (builder.weatherTrackCondition != null) {
                weather = new Weather(null, builder.windSpeedDirection);
            } else {
                weather = null;
            }
        }

        if (distanceSurfaceTrackRecord != null &&
                distanceSurfaceTrackRecord.getRaceDistance() != null &&
                builder.runUpTemporaryRail != null) {
            distanceSurfaceTrackRecord.getRaceDistance()
                    .setRunUp(builder.runUpTemporaryRail.getRunUp());
            distanceSurfaceTrackRecord.getRaceDistance()
                    .setTempRail(builder.runUpTemporaryRail.getTempRail());
        }

        if (raceConditions != null && builder.raceTypeNameBlackTypeBreed != null) {
            raceConditions.setRaceTypeNameBlackTypeBreed(builder.raceTypeNameBlackTypeBreed);
        }

        if (raceConditions != null && builder.purse != null) {
            raceConditions.setPurse(builder.purse);
        }

        ratings = new ArrayList<>();

        links = buildLinks(track, raceDate, raceNumber);
    }

    public RaceResult(Cancellation cancellation, LocalDate raceDate, Track track,
            Integer raceNumber) {
        this(cancellation, raceDate, track, raceNumber, null, null, null, null, false, null,
                null, null, null, null, null, null);
    }

    @JsonCreator
    public RaceResult(Cancellation cancellation, LocalDate raceDate, Track track,
            Integer raceNumber, RaceConditions raceConditions,
            DistanceSurfaceTrackRecord distanceSurfaceTrackRecord, Weather weather,
            PostTimeStartCommentsTimer postTimeStartCommentsTimer, boolean deadHeat,
            List<Starter> starters, List<Scratch> scratches, List<Fractional> fractionals,
            List<Split> splits, WagerPayoffPools wagerPayoffPools, String footnotes,
            List<Rating> ratings) {
        this.cancellation = cancellation;
        this.raceDate = raceDate;
        this.track = track;
        this.raceNumber = raceNumber;
        this.raceConditions = raceConditions;
        this.distanceSurfaceTrackRecord = distanceSurfaceTrackRecord;
        this.weather = weather;
        this.postTimeStartCommentsTimer = postTimeStartCommentsTimer;
        this.deadHeat = deadHeat;
        this.starters = starters;
        this.scratches = scratches;
        this.fractionals = fractionals;
        this.splits = splits;
        this.wagerPayoffPools = wagerPayoffPools;
        this.footnotes = footnotes;
        this.ratings = ratings;
        this.links = buildLinks(track, raceDate, raceNumber);
    }

    public static List<Link> buildLinks(Track track, LocalDate raceDate, Integer raceNumber) {
        List<Link> links = new ArrayList<>();

        if (track != null && raceDate != null) {
            String raceDateMDY = ChartParser.convertToMonthDayYear(raceDate);
            String singleChartEmbedded =
                    String.format("https://www.equibase.com/premium/chartEmb.cfm?" +
                                    "track=%s&raceDate=%s&cy=%s&rn=%s", track.getCode(),
                            raceDateMDY,
                            track.getCountry(), raceNumber);
            Link embeddedChart = new Link(singleChartEmbedded, "web");

            String singeChartDirect =
                    String.format("https://www.equibase.com/premium/eqbPDFChartPlus.cfm?" +
                                    "RACE=%d&BorP=P&TID=%s&CTRY=%s&DT=%s&DAY=D&STYLE=EQB",
                            raceNumber, track.getCode(), track.getCountry(), raceDateMDY);
            Link directChart = new Link(singeChartDirect, "pdf");

            String raceDayEmbedded =
                    String.format("https://www.equibase.com/premium/chartEmb.cfm?" +
                                    "track=%s&raceDate=%s&cy=%s", track.getCode(), raceDateMDY,
                            track.getCountry());
            Link embeddedRaceDay = new Link(raceDayEmbedded, "allWeb");

            String raceDayDirect =
                    String.format("https://www.equibase.com/premium/eqbPDFChartPlus.cfm?" +
                                    "RACE=A&BorP=P&TID=%s&CTRY=%s&DT=%s&DAY=D&STYLE=EQB",
                            track.getCode(), track.getCountry(), raceDateMDY);
            Link directRaceDay = new Link(raceDayDirect, "allPdf");

            links.add(embeddedChart);
            links.add(directChart);
            links.add(embeddedRaceDay);
            links.add(directRaceDay);
        }

        return links;
    }

    public List<Link> getLinks() {
        return links;
    }

    @JsonIgnore
    public Optional<Link> getLink(String rel) {
        return getLinks().stream()
                .filter(link -> link.getRel() != null &&
                        link.getRel().equalsIgnoreCase(rel)
                ).findAny();
    }

    public Cancellation getCancellation() {
        return cancellation;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public Track getTrack() {
        return track;
    }

    public Integer getRaceNumber() {
        return raceNumber;
    }

    public RaceConditions getRaceConditions() {
        return raceConditions;
    }

    public DistanceSurfaceTrackRecord getDistanceSurfaceTrackRecord() {
        return distanceSurfaceTrackRecord;
    }

    public Weather getWeather() {
        return weather;
    }

    public PostTimeStartCommentsTimer getPostTimeStartCommentsTimer() {
        return postTimeStartCommentsTimer;
    }

    public List<Fractional> getFractionals() {
        return fractionals;
    }

    public List<Split> getSplits() {
        return splits;
    }

    public List<Scratch> getScratches() {
        return scratches;
    }

    public boolean isDeadHeat() {
        return deadHeat;
    }

    public WagerPayoffPools getWagerPayoffPools() {
        return wagerPayoffPools;
    }

    public List<Starter> getStarters() {
        return starters;
    }

    public String getFootnotes() {
        return footnotes;
    }

    public int getNumberOfRunners() {
        return (starters != null ? starters.size() : 0);
    }

    public List<Rating> getRatings() {
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.ratings = ratings;
    }

    @JsonIgnore
    public List<Starter> getWinners() {
        if (starters != null) {
            return starters.stream().filter(Starter::isWinner).collect(toList());
        }
        return new ArrayList<>();
    }

    @JsonIgnore
    public List<Starter> firstFinishers() {
        if (starters != null) {
            // may have passed the post first but been disqualified
            return starters.stream().filter(Starter::finishedFirst).collect(toList());
        }
        return new ArrayList<>();
    }

    @JsonProperty("finalTime")
    public String getFinalTime() {
        List<Starter> winners = firstFinishers();
        if (winners != null && !winners.isEmpty()) {
            Fractional finishFractional = winners.get(0).getFinishFractional();
            return (finishFractional != null ? finishFractional.getTime() : null);
        }
        return null;
    }

    @JsonProperty("finalMillis")
    public Long getFinalMillis() {
        List<Starter> winners = firstFinishers();
        if (winners != null && !winners.isEmpty()) {
            Fractional finishFractional = winners.get(0).getFinishFractional();
            return (finishFractional != null ? finishFractional.getMillis() : null);
        }
        return null;
    }

    @JsonIgnore
    public String simpleSummary() {
        return String.format("%s %s R%d", getTrack().getCode(), getRaceDate(), getRaceNumber());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RaceResult that = (RaceResult) o;

        if (deadHeat != that.deadHeat) return false;
        if (cancellation != null ? !cancellation.equals(that.cancellation) : that
                .cancellation !=
                null)
            return false;
        if (raceDate != null ? !raceDate.equals(that.raceDate) : that.raceDate != null)
            return false;
        if (track != null ? !track.equals(that.track) : that.track != null) return false;
        if (raceNumber != null ? !raceNumber.equals(that.raceNumber) : that.raceNumber != null)
            return false;
        if (raceConditions != null ? !raceConditions.equals(that.raceConditions) : that
                .raceConditions != null)
            return false;
        if (distanceSurfaceTrackRecord != null ? !distanceSurfaceTrackRecord.equals(that
                .distanceSurfaceTrackRecord) : that.distanceSurfaceTrackRecord != null)
            return false;
        if (weather != null ? !weather.equals(that.weather) : that.weather != null)
            return false;
        if (postTimeStartCommentsTimer != null ? !postTimeStartCommentsTimer.equals(that
                .postTimeStartCommentsTimer) : that.postTimeStartCommentsTimer != null)
            return false;
        if (starters != null ? !starters.equals(that.starters) : that.starters != null)
            return false;
        if (scratches != null ? !scratches.equals(that.scratches) : that.scratches != null)
            return false;
        if (fractionals != null ? !fractionals.equals(that.fractionals) : that.fractionals !=
                null)
            return false;
        if (splits != null ? !splits.equals(that.splits) : that.splits != null) return false;
        if (wagerPayoffPools != null ? !wagerPayoffPools.equals(that.wagerPayoffPools) : that
                .wagerPayoffPools != null)
            return false;
        if (footnotes != null ? !footnotes.equals(that.footnotes) : that.footnotes != null)
            return false;
        return ratings != null ? ratings.equals(that.ratings) : that.ratings == null;
    }

    @Override
    public int hashCode() {
        int result = cancellation != null ? cancellation.hashCode() : 0;
        result = 31 * result + (raceDate != null ? raceDate.hashCode() : 0);
        result = 31 * result + (track != null ? track.hashCode() : 0);
        result = 31 * result + (raceNumber != null ? raceNumber.hashCode() : 0);
        result = 31 * result + (raceConditions != null ? raceConditions.hashCode() : 0);
        result = 31 * result + (distanceSurfaceTrackRecord != null ? distanceSurfaceTrackRecord
                .hashCode() : 0);
        result = 31 * result + (weather != null ? weather.hashCode() : 0);
        result = 31 * result + (postTimeStartCommentsTimer != null ? postTimeStartCommentsTimer
                .hashCode() : 0);
        result = 31 * result + (deadHeat ? 1 : 0);
        result = 31 * result + (starters != null ? starters.hashCode() : 0);
        result = 31 * result + (scratches != null ? scratches.hashCode() : 0);
        result = 31 * result + (fractionals != null ? fractionals.hashCode() : 0);
        result = 31 * result + (splits != null ? splits.hashCode() : 0);
        result = 31 * result + (wagerPayoffPools != null ? wagerPayoffPools.hashCode() : 0);
        result = 31 * result + (footnotes != null ? footnotes.hashCode() : 0);
        result = 31 * result + (ratings != null ? ratings.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RaceResult{" +
                "cancellation=" + cancellation +
                ", raceDate=" + raceDate +
                ", track=" + track +
                ", raceNumber=" + raceNumber +
                ", raceConditions=" + raceConditions +
                ", distanceSurfaceTrackRecord=" + distanceSurfaceTrackRecord +
                ", weather=" + weather +
                ", postTimeStartCommentsTimer=" + postTimeStartCommentsTimer +
                ", deadHeat=" + deadHeat +
                ", starters=" + starters +
                ", scratches=" + scratches +
                ", fractionals=" + fractionals +
                ", splits=" + splits +
                ", wagerPayoffPools=" + wagerPayoffPools +
                ", footnotes='" + footnotes + '\'' +
                ", ratings=" + ratings +
                '}';
    }

    /**
     * Builder pattern used to construct the {@link RaceResult}
     */
    public static class Builder {
        private Cancellation cancellation;
        private LocalDate raceDate;
        private Track track;
        private Integer raceNumber;
        private RaceTypeNameBlackTypeBreed raceTypeNameBlackTypeBreed;
        private RaceConditions raceConditions;
        private Purse purse;
        private DistanceSurfaceTrackRecord distanceSurfaceTrackRecord;
        private RunUpTemporaryRail runUpTemporaryRail;
        private WeatherTrackCondition weatherTrackCondition;
        private PostTimeStartCommentsTimer postTimeStartCommentsTimer;
        private WindSpeedDirection windSpeedDirection;
        private List<Fractional> fractionals;
        private List<Split> splits;
        private List<Scratch> scratches;
        private boolean deadHeat;
        private WagerPayoffPools wagerPayoffPools;
        private List<Starter> starters;
        private String footnotes;

        public Builder cancellation(final Cancellation cancellation) {
            this.cancellation = cancellation;
            return this;
        }

        public Builder track(final Track track) {
            this.track = track;
            return this;
        }

        public Builder raceDate(final LocalDate raceDate) {
            this.raceDate = raceDate;
            return this;
        }

        public Builder raceNumber(final Integer raceNumber) {
            this.raceNumber = raceNumber;
            return this;
        }

        public Builder raceTypeAndRaceNameAndBlackTypeAndBreed(
                final RaceTypeNameBlackTypeBreed raceTypeNameBlackTypeBreed) {
            this.raceTypeNameBlackTypeBreed = raceTypeNameBlackTypeBreed;
            return this;
        }

        public Builder runUpTemporaryRail(final RunUpTemporaryRail runUpTemporaryRail) {
            this.runUpTemporaryRail = runUpTemporaryRail;
            return this;
        }

        public Builder fractionals(final List<Fractional> fractionals) throws
                ChartParserException {
            this.splits = createSplitsFromFractionals(fractionals);
            this.fractionals = fractionals;
            return this;
        }

        public Builder distanceAndSurfaceAndTrackRecord(
                final DistanceSurfaceTrackRecord distanceSurfaceTrackRecord) {
            this.distanceSurfaceTrackRecord = distanceSurfaceTrackRecord;
            return this;
        }

        public Builder starters(List<Starter> starters) {
            this.starters = starters;
            return this;
        }

        public Builder raceConditionsAndClaimingPricesRange(
                final RaceConditions raceConditions) {
            this.raceConditions = raceConditions;
            return this;
        }

        public Builder purse(final Purse purse) {
            this.purse = purse;
            return this;
        }

        public Builder windSpeedAndDirection(final WindSpeedDirection windSpeedDirection) {
            this.windSpeedDirection = windSpeedDirection;
            return this;
        }

        public Builder weatherAndTrackCondition(final WeatherTrackCondition
                weatherTrackCondition) {
            this.weatherTrackCondition = weatherTrackCondition;
            return this;
        }

        public Builder postTimeAndStartCommentsAndTimer(
                final PostTimeStartCommentsTimer postTimeStartCommentsTimer) {
            this.postTimeStartCommentsTimer = postTimeStartCommentsTimer;
            return this;
        }

        public Builder scratches(final List<Scratch> scratches) {
            this.scratches = scratches;
            return this;
        }

        public Builder wagerPoolsAndPayoffs(final WagerPayoffPools wagerPayoffPools) {
            this.wagerPayoffPools = wagerPayoffPools;
            return this;
        }

        public Builder footnotes(final String footnotes) {
            this.footnotes = footnotes;
            return this;
        }

        // for looking up suitable point of calls when building a Starter
        public DistanceSurfaceTrackRecord getDistanceSurfaceTrackRecord() {
            return distanceSurfaceTrackRecord;
        }

        // useful for looking up daysSince for a Starter being built
        public LocalDate getRaceDate() {
            return raceDate;
        }

        // for looking up suitable point of calls when building a Starter
        public RaceTypeNameBlackTypeBreed getRaceTypeNameBlackTypeBreed() {
            return raceTypeNameBlackTypeBreed;
        }

        public RaceResult build() throws ChartParserException {
            markCoupledAndFieldEntries(starters);

            updateStartersWithWinPlaceShowPayoffs(starters, wagerPayoffPools);

            calculateIndividualFractionalsAndSplits(starters, fractionals,
                    raceTypeNameBlackTypeBreed, distanceSurfaceTrackRecord);

            updateStartersWithOddsChoiceIndicies(starters);

            markPositionDeadHeats(starters);

            // whether the race resulted in a dead heat
            // ignore the idiotic 2016 Parx Oaks co-winner decision
            if (!ChartParser.is2016ParxOaksDebacle(track, raceDate, raceNumber)) {
                deadHeat = detectDeadHeat(starters);
            }

            return new RaceResult(this);
        }

        List<Starter> markPositionDeadHeats(List<Starter> starters) {
            if (starters != null && !starters.isEmpty()) {
                starters.stream()
                        // did finish the race
                        .filter(starter -> starter.getFinishPosition() != null)
                        .collect(groupingBy(Starter::getFinishPosition))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .flatMap(entry -> entry.getValue().stream())
                        .forEach(starter -> starter.setPositionDeadHeat(true));
            }

            return starters;
        }

        List<Starter> markCoupledAndFieldEntries(List<Starter> starters) {
            if (starters != null) {
                starters.stream()
                        .collect(groupingBy(Starter::getEntryProgram))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .flatMap(entry -> entry.getValue().stream())
                        .filter(starter -> !starter.isEntry())
                        .forEach(starter -> starter.setEntry(true));
            }
            return starters;
        }

        // adds the win, show, and place payoffs to the applicable Starters for easier lookups,
        // also handling coupled/field entries
        List<Starter> updateStartersWithWinPlaceShowPayoffs(List<Starter> starters,
                WagerPayoffPools wagerPayoffPools) {
            if (wagerPayoffPools != null && starters != null) {
                WinPlaceShowPayoffPool payoffPools = wagerPayoffPools
                        .getWinPlaceShowPayoffPools();
                if (payoffPools != null) {
                    List<WinPlaceShowPayoff> winPlaceShowPayoffs =
                            payoffPools.getWinPlaceShowPayoffs();

                    // group Win-Place-Show payoffs by their entry program number
                    Map<Optional<String>, List<WinPlaceShowPayoff>> wpsPayoffsByEntry =
                            winPlaceShowPayoffs.stream()
                                    .collect(groupingBy(
                                            wpsPayoff -> ofNullable(wpsPayoff.getEntryProgram())));

                    // for each unique coupled program number
                    for (Optional<String> entryProgram : wpsPayoffsByEntry.keySet()) {
                        List<WinPlaceShowPayoff> wpsPayoffsForEntry =
                                wpsPayoffsByEntry.get(entryProgram);
                        if (wpsPayoffsForEntry != null) {
                            Optional<WinPlaceShowPayoff> payoff =
                                    wpsPayoffsForEntry.stream().findFirst();

                            // group starters by their entry program number
                            Map<Optional<String>, List<Starter>> startersByEntryProgram =
                                    starters.stream()
                                            .collect(groupingBy(starter ->
                                                    ofNullable(starter.getEntryProgram())));

                            // set the same WPS payoffs for all starters of a coupled/field
                            // entry
                            if (entryProgram.isPresent() && payoff.isPresent() &&
                                    startersByEntryProgram.containsKey(entryProgram)) {
                                startersByEntryProgram.get(entryProgram).stream().forEach(
                                        starter -> starter.setWinPlaceShowPayoff(payoff.get()));
                            } else {
                                // or set the WPS payoffs for the matching starter
                                for (Starter starter : starters) {
                                    if (payoff.isPresent() &&
                                            matchesEntryProgramOrHorseName(payoff.get(), starter)) {
                                        starter.setWinPlaceShowPayoff(payoff.get());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return starters;
        }

        boolean matchesEntryProgramOrHorseName(WinPlaceShowPayoff payoff, Starter starter) {
            return (payoff.getHorse() != null &&
                    payoff.getHorse().getName().equals(starter.getHorse().getName()));
        }

        /**
         * For Thoroughbred and Arabian races, combines the times of the leader at each fractional
         * point and the lengths ahead/behind at each point of call to calculate the (estimated)
         * individual fractional and splits for each {@link Starter}
         *
         * For Quarter Horse and Mixed races, each {@link Starter}'s fractionals and splits are
         * taken from the individual final time. The race's fractional and split times are taken
         * from the winner's individual final time.
         */
        List<Starter> calculateIndividualFractionalsAndSplits(
                List<Starter> starters,
                List<Fractional> fractionals,
                RaceTypeNameBlackTypeBreed raceTypeNameBlackTypeBreed,
                DistanceSurfaceTrackRecord distanceSurfaceTrackRecord) throws
                ChartParserException {
            if (starters != null) {
                if (fractionals != null && !fractionals.isEmpty()) {
                    starters = calculateForTBredsAndArabians(starters, fractionals);
                } else if (raceTypeNameBlackTypeBreed != null && distanceSurfaceTrackRecord
                        != null
                        && (raceTypeNameBlackTypeBreed.getBreed().equals(Breed.QUARTER_HORSE) ||
                        raceTypeNameBlackTypeBreed.getBreed().equals(Breed.MIXED))) {
                    List<Fractional> winningFractionals = new ArrayList<>();
                    starters = calculateForQHAndMixed(starters, distanceSurfaceTrackRecord,
                            winningFractionals);
                    this.fractionals(winningFractionals);
                }
            }

            return starters;
        }

        List<Starter> calculateForTBredsAndArabians(List<Starter> starters,
                List<Fractional> fractionals) throws ChartParserException {
            for (Starter starter : starters) {
                List<Fractional> individualFractionals = new ArrayList<>();
                for (Fractional fractional : fractionals) {
                    Optional<PointOfCall> pointOfCallOptional =
                            starter.getPointOfCall(fractional.getFeet());
                    if (pointOfCallOptional.isPresent()) {
                        PointOfCall pointOfCall = pointOfCallOptional.get();
                        Fractional individualFractional =
                                calculateIndividualFractionals(fractional, pointOfCall);

                        individualFractionals.add(individualFractional);
                    }
                }

                starter.setFractionals(individualFractionals);

                List<Split> splits = createSplitsFromFractionals(individualFractionals);
                starter.setSplits(splits);
            }

            return starters;
        }

        Fractional calculateIndividualFractionals(Fractional fractional, PointOfCall
                pointOfCall) {
            RelativePosition relativePosition = pointOfCall.getRelativePosition();
            RelativePosition.TotalLengthsBehind totalLengthsBehind =
                    relativePosition.getTotalLengthsBehind();
            LengthsAhead lengthsAhead = relativePosition.getLengthsAhead();

            Double lengths;
            if (totalLengthsBehind != null) {
                lengths = totalLengthsBehind.getLengths();
            } else if (lengthsAhead != null) {
                lengths = 0.0;
            } else {
                lengths = null;
            }

            Long individualMillis = null;
            String time = null;
            if (lengths != null) {
                Long fractionalMillis = fractional.getMillis();
                if (fractionalMillis != null) {
                    double feetPerMillisecond =
                            ((double) fractional.getFeet() / fractionalMillis);
                    double feetBehind = (lengths * 8.75); // 8.75 feet (estimation for a
                    // "length")
                    double additionalMillis = (feetBehind / feetPerMillisecond);
                    individualMillis = (long) (fractionalMillis + additionalMillis);
                    time = FractionalPoint.convertToTime(individualMillis);
                }
            }

            return new Fractional(fractional.getPoint(), fractional.getText(),
                    fractional.getCompact(), fractional.getFeet(), time, individualMillis);
        }

        // calculates the Split - the time taken between fractionals e.g. if a Starter
        // recorded a first quarter time of 22 seconds, and a first half-mile time of 45 seconds,
        // that would be a 2 furlong split of 23 seconds
        List<Split> createSplitsFromFractionals(List<Fractional> fractionals)
                throws ChartParserException {
            List<Split> splits = new ArrayList<>();
            for (int i = 0; i < fractionals.size(); i++) {
                Fractional fractional = fractionals.get(i);
                if (i == 0) {
                    Split split = Split.calculate(null, fractional);
                    splits.add(split);
                    continue;
                }

                Fractional next = fractionals.get(i - 1);
                Split split = Split.calculate(next, fractional);
                splits.add(split);
            }
            return splits;
        }

        List<Starter> calculateForQHAndMixed(List<Starter> starters,
                DistanceSurfaceTrackRecord distanceSurfaceTrackRecord,
                List<Fractional> winningFractionals) throws ChartParserException {
            for (Starter starter : starters) {
                List<Fractional> individualFractionals = new ArrayList<>();
                List<Rating> ratings = starter.getRatings();
                if (starter.getFinishPosition() != null && ratings != null &&
                        distanceSurfaceTrackRecord.getRaceDistance() != null) {
                    int distance = distanceSurfaceTrackRecord.getRaceDistance().getFeet();
                    String compact = distanceSurfaceTrackRecord.getRaceDistance().getCompact();
                    for (Rating rating : ratings) {
                        if (rating instanceof Rating.AqhaSpeedIndex) {
                            Long millis = ((Rating.AqhaSpeedIndex) rating).getMillis();
                            Fractional finishFractional = new Fractional(6, "Fin", compact,
                                    distance, (millis != null ?
                                    FractionalPoint.convertToTime(millis) : null), millis);

                            individualFractionals.add(finishFractional);

                            if (starter.getFinishPosition() == 1 && winningFractionals
                                    .isEmpty()) {
                                winningFractionals.add(finishFractional);
                            }

                            break;
                        }
                    }
                }

                starter.setFractionals(individualFractionals);

                List<Split> splits = createSplitsFromFractionals(individualFractionals);
                starter.setSplits(splits);
            }

            return starters;
        }

        List<Starter> updateStartersWithOddsChoiceIndicies(List<Starter> starters) {
            if (starters != null) {
                List<Double> odds = new ArrayList<>();

                // for each Starter that has an Odds value, add it to the odds List
                starters.stream()
                        .filter(starter -> (starter.getOdds() != null))
                        .forEach(starter -> odds.add(starter.getOdds()));

                // sort the odds (ascending)
                odds.sort(Comparator.comparingDouble(Double::doubleValue));

                // remove duplicates from the odds list by replacing them with nulls
                List<Double> truncatedOdds = new ArrayList<>();
                for (Double choice : odds) {
                    if (truncatedOdds.contains(choice)) {
                        truncatedOdds.add(null);
                    } else {
                        truncatedOdds.add(choice);
                    }
                }

                // update each starter that has an Odds value with the 1-based choice index
                // e.g. the favorite is 1, the third favorite is 3, the tenth favorite is 10
                starters.stream()
                        .filter(starter -> (starter.getOdds() != null))
                        .forEach(starter -> {
                            int choiceIndex = odds.indexOf(starter.getOdds());
                            if (choiceIndex > -1) {
                                starter.setChoice(choiceIndex + 1); // 1-based
                            }
                        });
            }
            return starters;
        }

        boolean detectDeadHeat(List<Starter> starters) {
            long count = 0;
            if (starters != null) {
                count = starters.stream()
                        .filter(starter -> {
                            Integer officialPosition = starter.getOfficialPosition();
                            return (officialPosition != null ? officialPosition == 1 : false);
                        }).count();
            }

            return count > 1;
        }

        public String summaryText() {
            return String.format("%s (%s), %s, Race %d (%s)", track.getCode(), track.getName(),
                    raceDate, raceNumber, (raceTypeNameBlackTypeBreed != null ?
                            raceTypeNameBlackTypeBreed.getBreed().getCode() : "Failed to parse"));
        }
    }

    public static class Weather {
        private final String text;
        @JsonProperty("wind")
        @JsonInclude(NON_NULL)
        private final WindSpeedDirection windSpeedDirection;

        public Weather(String text, WindSpeedDirection windSpeedDirection) {
            this.text = text;
            this.windSpeedDirection = windSpeedDirection;
        }

        public String getText() {
            return text;
        }

        public WindSpeedDirection getWindSpeedDirection() {
            return windSpeedDirection;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Weather weather = (Weather) o;

            if (text != null ? !text.equals(weather.text) : weather.text != null) return false;
            return windSpeedDirection != null ? windSpeedDirection.equals(weather
                    .windSpeedDirection) : weather.windSpeedDirection == null;
        }

        @Override
        public int hashCode() {
            int result = text != null ? text.hashCode() : 0;
            result = 31 * result + (windSpeedDirection != null ? windSpeedDirection.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Weather{" +
                    "text='" + text + '\'' +
                    ", windSpeedDirection=" + windSpeedDirection +
                    '}';
        }
    }
}
