package com.bodcorp.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.bodcorp.chartparser.charts.pdf.DistanceSurfaceTrackRecord.RaceDistance;
import com.bodcorp.chartparser.charts.pdf.running_line.HorseJockey;
import com.bodcorp.chartparser.charts.pdf.running_line.IndividualTime;
import com.bodcorp.chartparser.charts.pdf.running_line.LastRaced;
import com.bodcorp.chartparser.charts.pdf.running_line.MedicationEquipment;
import com.bodcorp.chartparser.charts.pdf.running_line.Odds;
import com.bodcorp.chartparser.charts.pdf.running_line.PastPerformanceRunningLinePreview;
import com.bodcorp.chartparser.charts.pdf.running_line.PointOfCallPosition;
import com.bodcorp.chartparser.charts.pdf.running_line.Weight;
import com.bodcorp.chartparser.charts.pdf.wagering.WagerPayoffPools.WinPlaceShowPayoffPool
        .WinPlaceShowPayoff;
import com.bodcorp.chartparser.exceptions.ChartParserException;
import com.bodcorp.chartparser.fractionals.FractionalPoint.Fractional;
import com.bodcorp.chartparser.fractionals.FractionalPoint.Split;
import com.bodcorp.chartparser.points_of_call.PointsOfCall;
import com.bodcorp.chartparser.points_of_call.PointsOfCall.PointOfCall;
import com.bodcorp.chartparser.points_of_call.PointsOfCall.PointOfCall.RelativePosition;
import com.bodcorp.chartparser.points_of_call.PointsOfCall.PointOfCall.RelativePosition
        .TotalLengthsBehind;
import com.bodcorp.chartparser.points_of_call.PointsOfCallService;
import com.bodcorp.chartparser.tracks.TrackService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Everything to do with a participant in a {@link RaceResult}
 */
@JsonPropertyOrder({"lastRaced", "program", "entry", "entryProgram", "horse", "jockey",
        "trainer", "owner", "weight", "medicationEquipment", "claim", "postPosition",
        "finishPosition", "officialPosition", "positionDeadHeat", "wageringPosition", "winner",
        "disqualified", "odds", "choice", "favorite", "wagering", "pointsOfCall", "fractionals",
        "splits", "ratings", "comments"})
public class Starter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Starter.class);

    private final LastRaced lastRaced;
    private final String program;
    private final String entryProgram; // e.g. "1", "1A", and "1X" are all "1"
    private final Horse horse;
    private final Jockey jockey;
    private final Weight weight;
    private final MedicationEquipment medicationEquipment;
    private final Integer postPosition;
    private final Double odds;
    private final Boolean favorite;
    private final String comments;
    private final List<PointOfCall> pointsOfCall;
    private Integer finishPosition; // unofficial finishing position
    private Integer officialPosition; // official finishing position (post DQs etc)
    private boolean positionDeadHeat; // true if dead-heated with at least one other Starter
    @JsonInclude(NON_NULL)
    private Integer wageringPosition; // payoff finishing position (Win=1, Place=2, Show=3)
    private Trainer trainer;
    private Owner owner;
    @JsonInclude(NON_NULL)
    private Claim claim;
    private boolean winner;
    private Boolean disqualified;
    @JsonProperty("wagering")
    @JsonIgnoreProperties({"program", "horse"})
    @JsonInclude(NON_NULL)
    private WinPlaceShowPayoff winPlaceShowPayoff;
    private List<Rating> ratings;
    private List<Fractional> fractionals;
    private List<Split> splits;
    private Integer choice; // the n-th betting choice
    private boolean entry;

    private Starter(Builder builder) {
        lastRaced = builder.lastRaced;
        program = builder.program;
        entryProgram = builder.entryProgram;
        entry = builder.entry;
        horse = (builder.horseJockey != null ? builder.horseJockey.getHorse() : null);
        jockey = (builder.horseJockey != null ? builder.horseJockey.getJockey() : null);
        weight = builder.weight;
        medicationEquipment = builder.medicationEquipment;
        postPosition = builder.postPosition;
        comments = builder.comments;

        pointsOfCall = builder.pointsOfCall;
        if (builder.pointsOfCall != null) {
            updateFinishPosition(builder.pointsOfCall);
        }

        ratings = new ArrayList<>();
        if (builder.speedIndex != null && builder.individualTimeMillis != null) {
            ratings.add(new Rating.AqhaSpeedIndex(builder.speedIndex,
                    builder.individualTimeMillis));
        }

        odds = (builder.odds != null ? builder.odds.getValue() : null);
        favorite = (builder.odds != null ? builder.odds.isFavorite() : null);
    }

    @JsonCreator
    public Starter(LastRaced lastRaced, String program, String entryProgram, boolean entry,
            Horse horse, Jockey jockey, Weight weight, MedicationEquipment medicationEquipment,
            Integer postPosition, Double odds, Boolean favorite, String comments,
            List<PointOfCall> pointsOfCall, Integer finishPosition, Integer officialPosition,
            boolean positionDeadHeat, Integer wageringPosition, Trainer trainer, Owner owner,
            Claim claim, boolean winner, Boolean disqualified,
            WinPlaceShowPayoff winPlaceShowPayoff, List<Rating> ratings,
            List<Fractional> fractionals, List<Split> splits, Integer choice) {
        this.lastRaced = lastRaced;
        this.program = program;
        this.entryProgram = entryProgram;
        this.entry = entry;
        this.horse = horse;
        this.jockey = jockey;
        this.weight = weight;
        this.medicationEquipment = medicationEquipment;
        this.postPosition = postPosition;
        this.odds = odds;
        this.favorite = favorite;
        this.comments = comments;
        this.pointsOfCall = pointsOfCall;
        this.finishPosition = finishPosition;
        this.officialPosition = officialPosition;
        this.positionDeadHeat = positionDeadHeat;
        this.wageringPosition = wageringPosition;
        this.trainer = trainer;
        this.owner = owner;
        this.claim = claim;
        this.winner = winner;
        this.disqualified = disqualified;
        this.winPlaceShowPayoff = winPlaceShowPayoff;
        this.ratings = ratings;
        this.fractionals = fractionals;
        this.splits = splits;
        this.choice = choice;
    }

    // constructor suitable for pre-race
    public Starter(LastRaced lastRaced, String program, String entryProgram, Horse horse,
            Jockey jockey, Weight weight, MedicationEquipment medicationEquipment,
            Integer postPosition, Trainer trainer, Owner owner, Claim claim, List<Rating> ratings,
            boolean entry) {
        this.lastRaced = lastRaced;
        this.program = program;
        this.entryProgram = entryProgram;
        this.horse = horse;
        this.jockey = jockey;
        this.weight = weight;
        this.medicationEquipment = medicationEquipment;
        this.postPosition = postPosition;
        this.trainer = trainer;
        this.owner = owner;
        this.claim = claim;
        this.ratings = ratings;
        this.entry = entry;

        odds = null;
        favorite = null;
        comments = null;
        pointsOfCall = null;
    }

    /**
     * Parses the running line grid and associates the individual column data to the appropriate
     * fields for the {@link Starter} in question
     */
    public static Starter parseRunningLineData(
            Map<String, List<ChartCharacter>> runningLineCharactersByColumn,
            LocalDate raceDate, Breed breed, RaceDistance raceDistance, TrackService trackService,
            PointsOfCallService pointsOfCallService) throws ChartParserException {
        Builder builder = new Builder();

        List<List<ChartCharacter>> pointsOfCall = new ArrayList<>();

        for (String column : runningLineCharactersByColumn.keySet()) {
            List<ChartCharacter> chartCharacters = runningLineCharactersByColumn.get(column);
            switch (column) {
                case "LastRaced":
                    builder.lastRaced(LastRaced.parse(chartCharacters, raceDate, trackService));
                    break;
                case "Pgm":
                    builder.program(Chart.convertToText(chartCharacters));
                    break;
                case "HorseName(Jockey)":
                    builder.horseAndJockey(HorseJockey.parse(chartCharacters));
                    break;
                case "Wgt":
                    builder.weight(Weight.parse(chartCharacters));
                    break;
                case "PP":
                    String postPositionText = Chart.convertToText(chartCharacters);
                    try {

                        if (!postPositionText.isEmpty()) {
                            if (postPositionText.contains("|") || postPositionText.contains(" ")) {
                                String[] split = postPositionText.split("\\||\\s");
                                if (split.length == 2) {
                                    LOGGER.warn(String.format("Detected PP affected by M/E in text: " +
                                                    "%s; extracting the PP to be %s", postPositionText,
                                            split[1]));
                                    postPositionText = split[1];
                                }
                                if (split.length == 3) {
                                    String splitTen = split[1] + split[2];
                                    LOGGER.warn(String.format("Detected PP affected by M/E in text: " +
                                                    "%s; extracting the PP to be %s", postPositionText,
                                            splitTen));
                                    postPositionText = splitTen;
                                }
                            }

                            builder.postPosition(Integer.parseInt(postPositionText));
                        }
                    }
                    catch (Exception p){
                        postPositionText = "0";
                        builder.postPosition(Integer.parseInt(postPositionText));
                    }
                    break;
                case "Odds":
                    builder.odds(Odds.parse(chartCharacters));
                    break;
                case "Ind.Time":
                    builder.individualTimeMillis(
                            IndividualTime.parse(Chart.convertToText(chartCharacters)));
                    break;
                case "Sp.In.":
                    String speedIndexText = Chart.convertToText(chartCharacters);
                    if (!speedIndexText.isEmpty()) {
                        int speedIndex = 0;
                        try {
                            speedIndex = Integer.parseInt(speedIndexText);
                        } catch (NumberFormatException e) {

                        }
                        builder.speedIndex(speedIndex);
                    }
                    break;
                case "M/E":
                    builder.medicationAndEquipment(MedicationEquipment.parse(chartCharacters));
                    break;
                case "Comments":
                    builder.comments(Chart.convertToText(chartCharacters));
                    break;
                default:
                    pointsOfCall.add(chartCharacters);
                    break;
            }
        }

        int numberOfPointsOfCallInChart = pointsOfCall.size();

        PointsOfCall pointsOfCallForDistance = buildPointsOfCall(breed, raceDistance,
                pointsOfCallService, builder, numberOfPointsOfCallInChart);

        // set the relative position (lengths ahead/behind) for each point of call
        for (int i = 0; i < numberOfPointsOfCallInChart; i++) {
            PointOfCall pointOfCall = pointsOfCallForDistance.getCalls().get(i);

            List<ChartCharacter> characters = pointsOfCall.get(i);
            RelativePosition relativePosition = PointOfCallPosition.parse(characters);

            pointOfCall.setRelativePosition(relativePosition);
        }

        updateStretchAndFinishDistances(raceDistance, pointsOfCallForDistance);

        List<PointOfCall> calls = pointsOfCallForDistance.getCalls();

        builder.pointsOfCall(calls);

        return builder.build();
    }

    static PointsOfCall buildPointsOfCall(Breed breed, RaceDistance raceDistance,
            PointsOfCallService pointsOfCallService, Builder builder,
            int numberOfPointsOfCallInChart) throws InvalidPointsOfCallException {
        PointsOfCall pointsOfCallForDistance =
                pointsOfCallService.getPointsOfCallForDistance(breed, raceDistance);

        // handle Mixed/QH races with inconsistent points of call
        // TODO this certainly could be improved
        if (numberOfPointsOfCallInChart > 0 && pointsOfCallForDistance.getCalls() != null) {
            if ((breed.equals(Breed.MIXED) || breed.equals(Breed.QUARTER_HORSE)) &&
                    (numberOfPointsOfCallInChart < pointsOfCallForDistance.getCalls().size())) {
                for (PointOfCall pointOfCall : pointsOfCallForDistance.getCalls()) {
                    if (pointOfCall.getText().equals("Str2")) {
                        LOGGER.warn(String.format("Removing the extraneous \"Str2\"" +
                                        " point of call from the race of distance \"" +
                                        "%s\" for starter \"%s\"", raceDistance.getText(),
                                builder.horseJockey.getHorse().getName()));
                        pointsOfCallForDistance.getCalls().remove(pointOfCall);
                        break;
                    }
                }

                // if still not right, give up
                if (numberOfPointsOfCallInChart != pointsOfCallForDistance.getCalls().size()) {
                    throw new InvalidPointsOfCallException(String.format("Unable to parse values " +
                                    "for all of the following points of call: %s",
                            pointsOfCallForDistance));
                }
            }
        }
        return pointsOfCallForDistance;
    }

    static void updateStretchAndFinishDistances(RaceDistance raceDistance, PointsOfCall
            pointsOfCallForDistance) {
        // set stretch distance if possible (as points of calls cover a variety of distances)
        Optional<PointOfCall> stretchPointOfCall = pointsOfCallForDistance.getStretchPointOfCall();
        if (stretchPointOfCall.isPresent()) {
            PointOfCall stretch = stretchPointOfCall.get();
            // stretch call is supposed to be one-furlong from the finish
            if (!stretch.hasKnownDistance() && raceDistance.getFeet() >= 1320) {
                int stretchFeet = raceDistance.getFeet() - 660;
                stretch.setFeet(stretchFeet);
                String stretchCompact = RaceDistance.lookupCompact(stretchFeet);
                String stretchFurlongs = String.format("%.2ff", stretch.getFurlongs());
                stretch.setCompact(stretchCompact != null ? stretchCompact : stretchFurlongs);
            } else {
                stretch.setCompact("Str");
            }
        }

        // set finish distance (as points of calls cover a variety of distances)
        Optional<PointOfCall> finishPointOfCall = pointsOfCallForDistance.getFinishPointOfCall();
        if (finishPointOfCall.isPresent()) {
            PointOfCall finish = finishPointOfCall.get();
            if (!finish.hasKnownDistance()) {
                finish.setFeet(raceDistance.getFeet());
                finish.setCompact(raceDistance.getCompact());
            }
        }
    }

    public LastRaced getLastRaced() {
        return lastRaced;
    }

    public String getProgram() {
        return program;
    }

    public Horse getHorse() {
        return horse;
    }

    public Jockey getJockey() {
        return jockey;
    }

    public Weight getWeight() {
        return weight;
    }

    public MedicationEquipment getMedicationEquipment() {
        return medicationEquipment;
    }

    public Integer getPostPosition() {
        return postPosition;
    }

    public Double getOdds() {
        return odds;
    }

    public Boolean isFavorite() {
        return favorite;
    }

    public List<Rating> getRatings() {
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.ratings = ratings;
    }

    public String getComments() {
        return comments;
    }

    public List<PointOfCall> getPointsOfCall() {
        return pointsOfCall;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public void setTrainer(Trainer trainer) {
        this.trainer = trainer;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public boolean isDisqualified() {
        return (disqualified != null ? disqualified : false);
    }

    public void setDisqualified(boolean disqualified) {
        this.disqualified = disqualified;
    }

    public void updateDisqualification(Disqualification disqualification) {
        this.disqualified = (disqualification != null);
        this.officialPosition = (disqualification != null ?
                disqualification.getNewPosition() : null);
    }

    public Integer getFinishPosition() {
        return finishPosition;
    }

    /**
     * Return the position at the last point of call (which should always be the finish)
     */
    public void updateFinishPosition(List<PointOfCall> pointsOfCall) {
        if (pointsOfCall != null && !pointsOfCall.isEmpty()) {
            finishPosition = pointsOfCall.get(pointsOfCall.size() - 1).getRelativePosition()
                    .getPosition();
        }

    }

    /**
     * Returns the official position marked on the chart or, if that is not present, the position
     * from the final point of call (the finish)
     */
    public Integer getOfficialPosition() {
        return (officialPosition != null ? officialPosition : finishPosition);
    }

    public void setOfficialPosition(Integer officialPosition) {
        this.officialPosition = officialPosition;
    }

    /**
     * Returns true if at least one other Starter dead-heated for the same finishing position
     */
    public boolean isPositionDeadHeat() {
        return positionDeadHeat;
    }

    public void setPositionDeadHeat(boolean positionDeadHeat) {
        this.positionDeadHeat = positionDeadHeat;
    }

    public Integer getWageringPosition() {
        return wageringPosition;
    }

    public void setWageringPosition(Integer wageringPosition) {
        this.wageringPosition = wageringPosition;
    }

    public WinPlaceShowPayoff getWinPlaceShowPayoff() {
        return winPlaceShowPayoff;
    }

    public void setWinPlaceShowPayoff(WinPlaceShowPayoff winPlaceShowPayoff) {
        this.winPlaceShowPayoff = winPlaceShowPayoff;
        if (this.winPlaceShowPayoff != null) {
            if (this.winPlaceShowPayoff.getWin() != null) {
                this.wageringPosition = 1;
            } else if (this.winPlaceShowPayoff.getPlace() != null) {
                this.wageringPosition = 2;
            } else if (this.winPlaceShowPayoff.getShow() != null) {
                this.wageringPosition = 3;
            }
        }
    }

    public List<Fractional> getFractionals() {
        return fractionals;
    }

    public void setFractionals(List<Fractional> fractionals) {
        this.fractionals = fractionals;
    }

    public List<Split> getSplits() {
        return splits;
    }

    public void setSplits(List<Split> splits) {
        this.splits = splits;
    }

    /**
     * A 1-index based value representing the starter's odds as the n-th betting choice
     */
    public Integer getChoice() {
        return choice;
    }

    public void setChoice(Integer choice) {
        this.choice = choice;
    }

    public String getEntryProgram() {
        return entryProgram;
    }

    public boolean isEntry() {
        return entry;
    }

    public void setEntry(boolean entry) {
        this.entry = entry;
    }

    public boolean matchesProgramOrName(String program, String horseName) {
        return (program != null && this.program.equals(program)) ||
                horse.getName().equals(horseName);
    }

    @JsonIgnore
    public Fractional getFinishFractional() {
        if (fractionals != null && !fractionals.isEmpty()) {
            return fractionals.get(fractionals.size() - 1);
        }
        return null;
    }

    @JsonIgnore
    public PointOfCall getFinishPointOfCall() {
        if (pointsOfCall != null && !pointsOfCall.isEmpty()) {
            return pointsOfCall.get(pointsOfCall.size() - 1);
        }
        return null;
    }

    /**
     * Add the total lengths behind to the {@link Starter}'s {@link RelativePosition} for the
     * applicable {@link PointOfCall}
     */
    public Starter setTotalLengthsBehindAtPointOfCall(String column,
            RelativePosition relativePosition) throws ChartParserException {
        Optional<PointOfCall> pointOfCall = getPointOfCall(column);
        if (pointOfCall.isPresent()) {
            TotalLengthsBehind totalLengthsBehind = createTotalLengthsBehind(relativePosition);
            RelativePosition pocRelPosition = pointOfCall.get().getRelativePosition();
            if (pocRelPosition != null) {
                pocRelPosition.setTotalLengthsBehind(totalLengthsBehind);
            }
        } else {
            throw new ChartParserException(String.format("Point of call not found for column: " +
                    "%s", column));
        }
        return this;
    }

    /**
     * For horses that were not the leader at the point of call, the total lengths behind is taken
     * from the {@link PastPerformanceRunningLinePreview}
     */
    private TotalLengthsBehind createTotalLengthsBehind(RelativePosition relativePosition) {
        Integer position = relativePosition.getPosition();
        if (position != null && position != 1) {
            RelativePosition.LengthsAhead runningLinePreviewLengths =
                    relativePosition.getLengthsAhead();
            if (runningLinePreviewLengths != null) {
                String chart = runningLinePreviewLengths.getText();
                Double lengths = runningLinePreviewLengths.getLengths();
                if (chart != null && lengths != null) {
                    return new TotalLengthsBehind(chart, lengths);
                }
            }
        }
        return null;
    }

    /**
     * Get the point of call that matches the specified distance (in feet)
     */
    public Optional<PointOfCall> getPointOfCall(int feet) {
        List<PointOfCall> pointsOfCall = getPointsOfCall();
        if (pointsOfCall != null) {
            for (PointOfCall pointOfCall : pointsOfCall) {
                if (pointOfCall.getFeet() != null && pointOfCall.getFeet() == feet) {
                    return Optional.of(pointOfCall);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get the point of call that matches the specified description in the chart
     */
    public Optional<PointOfCall> getPointOfCall(String chartPointOfCall) {
        List<PointOfCall> pointsOfCall = getPointsOfCall();
        if (pointsOfCall != null) {
            for (PointOfCall pointOfCall : pointsOfCall) {
                if (pointOfCall.getText().equals(chartPointOfCall)) {
                    return Optional.of(pointOfCall);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @return true if this Starter was an official winner of the race
     */
    public boolean isWinner() {
        return (getOfficialPosition() != null && getOfficialPosition() == 1);
    }

    /**
     * Different than {@link #isWinner()} because the horse that finished first may not have been
     * the official winner
     * @return true if this Starter was the unofficial winner of the race (by finishing first)
     */
    @JsonIgnore
    public boolean finishedFirst() {
        return (getFinishPosition() != null && getFinishPosition() == 1);
    }

    /**
     * A race's winner(s) contain additional breeding information - update the {@link Starter}'s
     * {@link Horse} instance with this information
     */
    public Horse updateWinner(Winner winner) {
        if (horse != null && winner != null) {
            horse.setColor(winner.getHorseColor());
            horse.setSex(winner.getHorseSex());
            horse.setSire(winner.getSireName() != null ? new Horse(winner.getSireName()) : null);
            horse.setDam(winner.getDamName() != null ? new Horse(winner.getDamName()) : null);
            horse.setDamSire(winner.getDamSireName() != null ?
                    new Horse(winner.getDamSireName()) : null);
            horse.setFoalingDate(winner.getFoalingDate());
            horse.setFoalingLocation(winner.getFoalingLocation());
            horse.setBreeder(winner.getBreeder());
        }
        return horse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Starter starter = (Starter) o;

        if (winner != starter.winner) return false;
        if (entry != starter.entry) return false;
        if (lastRaced != null ? !lastRaced.equals(starter.lastRaced) : starter.lastRaced != null)
            return false;
        if (program != null ? !program.equals(starter.program) : starter.program != null)
            return false;
        if (entryProgram != null ? !entryProgram.equals(starter.entryProgram) : starter
                .entryProgram != null)
            return false;
        if (horse != null ? !horse.equals(starter.horse) : starter.horse != null) return false;
        if (jockey != null ? !jockey.equals(starter.jockey) : starter.jockey != null) return false;
        if (weight != null ? !weight.equals(starter.weight) : starter.weight != null) return false;
        if (medicationEquipment != null ? !medicationEquipment.equals(starter
                .medicationEquipment) : starter.medicationEquipment != null)
            return false;
        if (postPosition != null ? !postPosition.equals(starter.postPosition) : starter
                .postPosition != null)
            return false;
        if (odds != null ? !odds.equals(starter.odds) : starter.odds != null) return false;
        if (favorite != null ? !favorite.equals(starter.favorite) : starter.favorite != null)
            return false;
        if (comments != null ? !comments.equals(starter.comments) : starter.comments != null)
            return false;
        if (pointsOfCall != null ? !pointsOfCall.equals(starter.pointsOfCall) : starter
                .pointsOfCall != null)
            return false;
        if (finishPosition != null ? !finishPosition.equals(starter.finishPosition) : starter
                .finishPosition != null)
            return false;
        if (officialPosition != null ? !officialPosition.equals(starter.officialPosition) :
                starter.officialPosition != null)
            return false;
        if (wageringPosition != null ? !wageringPosition.equals(starter.wageringPosition) :
                starter.wageringPosition != null)
            return false;
        if (trainer != null ? !trainer.equals(starter.trainer) : starter.trainer != null)
            return false;
        if (owner != null ? !owner.equals(starter.owner) : starter.owner != null) return false;
        if (claim != null ? !claim.equals(starter.claim) : starter.claim != null) return false;
        if (disqualified != null ? !disqualified.equals(starter.disqualified) : starter
                .disqualified != null)
            return false;
        if (winPlaceShowPayoff != null ? !winPlaceShowPayoff.equals(starter.winPlaceShowPayoff) :
                starter.winPlaceShowPayoff != null)
            return false;
        if (ratings != null ? !ratings.equals(starter.ratings) : starter.ratings != null)
            return false;
        if (fractionals != null ? !fractionals.equals(starter.fractionals) : starter.fractionals
                != null)
            return false;
        if (splits != null ? !splits.equals(starter.splits) : starter.splits != null) return false;
        return choice != null ? choice.equals(starter.choice) : starter.choice == null;
    }

    @Override
    public int hashCode() {
        int result = lastRaced != null ? lastRaced.hashCode() : 0;
        result = 31 * result + (program != null ? program.hashCode() : 0);
        result = 31 * result + (entryProgram != null ? entryProgram.hashCode() : 0);
        result = 31 * result + (horse != null ? horse.hashCode() : 0);
        result = 31 * result + (jockey != null ? jockey.hashCode() : 0);
        result = 31 * result + (weight != null ? weight.hashCode() : 0);
        result = 31 * result + (medicationEquipment != null ? medicationEquipment.hashCode() : 0);
        result = 31 * result + (postPosition != null ? postPosition.hashCode() : 0);
        result = 31 * result + (odds != null ? odds.hashCode() : 0);
        result = 31 * result + (favorite != null ? favorite.hashCode() : 0);
        result = 31 * result + (comments != null ? comments.hashCode() : 0);
        result = 31 * result + (pointsOfCall != null ? pointsOfCall.hashCode() : 0);
        result = 31 * result + (finishPosition != null ? finishPosition.hashCode() : 0);
        result = 31 * result + (officialPosition != null ? officialPosition.hashCode() : 0);
        result = 31 * result + (wageringPosition != null ? wageringPosition.hashCode() : 0);
        result = 31 * result + (trainer != null ? trainer.hashCode() : 0);
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (claim != null ? claim.hashCode() : 0);
        result = 31 * result + (winner ? 1 : 0);
        result = 31 * result + (disqualified != null ? disqualified.hashCode() : 0);
        result = 31 * result + (winPlaceShowPayoff != null ? winPlaceShowPayoff.hashCode() : 0);
        result = 31 * result + (ratings != null ? ratings.hashCode() : 0);
        result = 31 * result + (fractionals != null ? fractionals.hashCode() : 0);
        result = 31 * result + (splits != null ? splits.hashCode() : 0);
        result = 31 * result + (choice != null ? choice.hashCode() : 0);
        result = 31 * result + (entry ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Starter{" +
                "lastRaced=" + lastRaced +
                ", program='" + program + '\'' +
                ", entryProgram='" + entryProgram + '\'' +
                ", horse=" + horse +
                ", jockey=" + jockey +
                ", weight=" + weight +
                ", medicationEquipment=" + medicationEquipment +
                ", postPosition=" + postPosition +
                ", odds=" + odds +
                ", favorite=" + favorite +
                ", comments='" + comments + '\'' +
                ", pointsOfCall=" + pointsOfCall +
                ", finishPosition=" + finishPosition +
                ", officialPosition=" + officialPosition +
                ", wageringPosition=" + wageringPosition +
                ", trainer=" + trainer +
                ", owner=" + owner +
                ", claim=" + claim +
                ", winner=" + winner +
                ", disqualified=" + disqualified +
                ", winPlaceShowPayoff=" + winPlaceShowPayoff +
                ", ratings=" + ratings +
                ", fractionals=" + fractionals +
                ", splits=" + splits +
                ", choice=" + choice +
                ", entry=" + entry +
                '}';
    }

    /**
     * Builder pattern used to create {@link Starter} instances
     */
    public static class Builder {
        private LastRaced lastRaced;
        private String program;
        private String entryProgram;
        private boolean entry;
        private HorseJockey horseJockey;
        private Weight weight;
        private MedicationEquipment medicationEquipment;
        private Integer postPosition;
        private List<PointOfCall> pointsOfCall;
        private Odds odds;
        private Integer speedIndex;
        private Long individualTimeMillis;
        private String comments;

        public Builder lastRaced(final LastRaced lastRaced) {
            this.lastRaced = lastRaced;
            return this;
        }

        public Builder program(final String program) {
            this.program = (program != null ? program.toUpperCase() : null);
            this.entryProgram = Chart.getEntryProgram(this.program);
            this.entry = (this.program != null && this.program != entryProgram);
            return this;
        }

        public Builder horseAndJockey(final HorseJockey horseJockey) {
            this.horseJockey = horseJockey;
            return this;
        }

        public Builder weight(final Weight weight) {
            this.weight = weight;
            return this;
        }

        public Builder medicationAndEquipment(final MedicationEquipment medicationEquipment) {
            this.medicationEquipment = medicationEquipment;
            return this;
        }

        public Builder postPosition(final Integer postPosition) {
            this.postPosition = postPosition;
            return this;
        }

        public Builder pointsOfCall(final List<PointOfCall> pointsOfCall) {
            this.pointsOfCall = pointsOfCall;
            return this;
        }

        public Builder odds(final Odds odds) {
            this.odds = odds;
            return this;
        }

        public Builder speedIndex(final Integer speedIndex) {
            this.speedIndex = speedIndex;
            return this;
        }

        public Builder individualTimeMillis(final Long individualTimeMillis) {
            this.individualTimeMillis = individualTimeMillis;
            return this;
        }

        public Builder comments(final String comments) {
            this.comments = comments;
            return this;
        }

        public Starter build() {
            return new Starter(this);
        }
    }

    public static class InvalidPointsOfCallException extends ChartParserException {
        public InvalidPointsOfCallException(String message) {
            super(message);
        }
    }

    public static class Claim {
        private final int price;
        private boolean claimed;
        @JsonInclude(NON_NULL)
        private String newTrainerName;
        @JsonInclude(NON_NULL)
        private String newOwnerName;

        public Claim(ClaimingPrice claimingPrice, ClaimedHorse claimedHorse) {
            if (claimingPrice != null) {
                price = claimingPrice.getClaimingPrice();
            } else {
                price = 0;
            }

            if (claimedHorse != null) {
                claimed = true;
                newTrainerName = claimedHorse.getNewTrainerName();
                newOwnerName = claimedHorse.getNewOwnerName();
            } else {
                claimed = false;
                newTrainerName = null;
                newOwnerName = null;
            }
        }

        public int getPrice() {
            return price;
        }

        public boolean isClaimed() {
            return claimed;
        }

        public String getNewTrainerName() {
            return newTrainerName;
        }

        public String getNewOwnerName() {
            return newOwnerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Claim claim = (Claim) o;

            if (price != claim.price) return false;
            if (claimed != claim.claimed) return false;
            if (newTrainerName != null ? !newTrainerName.equals(claim.newTrainerName) : claim
                    .newTrainerName != null)
                return false;
            return newOwnerName != null ? newOwnerName.equals(claim.newOwnerName) : claim
                    .newOwnerName == null;
        }

        @Override
        public int hashCode() {
            int result = price;
            result = 31 * result + (claimed ? 1 : 0);
            result = 31 * result + (newTrainerName != null ? newTrainerName.hashCode() : 0);
            result = 31 * result + (newOwnerName != null ? newOwnerName.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Claim{" +
                    "price=" + price +
                    ", claimed=" + claimed +
                    ", newTrainerName='" + newTrainerName + '\'' +
                    ", newOwnerName='" + newOwnerName + '\'' +
                    '}';
        }
    }

}
