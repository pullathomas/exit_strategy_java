package com.robinhowlett.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.robinhowlett.chartparser.exceptions.ChartParserException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Parses and stores the race type (e.g. "STAKES"), short code (e.g. "STK"), name (e.g. "Kentucky
 * Derby"), grade (e.g. 1), black type (e.g. "Grade 1"), and for what {@link Breed}
 */
@JsonPropertyOrder({"breed", "type", "code", "name", "grade", "blackType"})
public class RaceTypeNameBlackTypeBreed {

    public static final Map<String, String> RACE_TYPE_CODES;
    static {
        Map<String, String> raceCodes = new LinkedHashMap<>();
        raceCodes.put("SPEED INDEX OPTIONAL CLAIMING", "SPO");
        raceCodes.put("INVITATIONAL HANDICAP STAKES", "IHS");
        raceCodes.put("ALLOWANCE OPTIONAL CLAIMING", "AOC");
        raceCodes.put("OPTIONAL CLAIMING HANDICAP", "OCH");
        raceCodes.put("STARTER OPTIONAL CLAIMING", "SOC");
        raceCodes.put("MAIDEN OPTIONAL CLAIMING", "MOC");
        raceCodes.put("MAIDEN STARTER ALLOWANCE", "MSA");
        raceCodes.put("OPTIONAL CLAIMING STAKES", "OCS");
        raceCodes.put("SPEED INDEX CONSOLATION", "SPC");
        raceCodes.put("WAIVER MAIDEN CLAIMING", "WMC");
        raceCodes.put("INVITATIONAL HANDICAP", "INH");
        raceCodes.put("MAIDEN SPECIAL WEIGHT", "MSW");
        raceCodes.put("FUTURITY CONSOLATION", "FCN");
        raceCodes.put("INVITATIONAL STAKES", "INS");
        raceCodes.put("CLAIMING HANDICAP", "CLH");
        raceCodes.put("OPTIONAL CLAIMING", "OCL");
        raceCodes.put("SPEED INDEX FINAL", "SPF");
        raceCodes.put("SPEED INDEX TRIAL", "SPT");
        raceCodes.put("STARTER ALLOWANCE", "STA");
        raceCodes.put("UNKNOWN RACE TYPE", "UNK");
        raceCodes.put("SPEED INDEX RACE", "SPR");
        raceCodes.put("STARTER HANDICAP", "STH");
        raceCodes.put("ALLOWANCE TRIAL", "ATR");
        raceCodes.put("CLAIMING STAKES", "CST");
        raceCodes.put("HANDICAP STAKES", "HCS");
        raceCodes.put("MAIDEN CLAIMING", "MCL");
        raceCodes.put("WAIVER CLAIMING", "WCL");
        raceCodes.put("FUTURITY TRIAL", "FTR");
        raceCodes.put("MATURITY TRIAL", "MTR");
        raceCodes.put("STARTER STAKES", "STS");
        raceCodes.put("MAIDEN STAKES", "MST");
        raceCodes.put("CHAMPIONSHIP", "CHP");
        raceCodes.put("INVITATIONAL", "INV");
        raceCodes.put("MAIDEN TRIAL", "MDT");
        raceCodes.put("CONSOLATION", "CON");
        raceCodes.put("DERBY TRIAL", "DTR");
        raceCodes.put("MATCH RACE", "MCH");
        raceCodes.put("ALLOWANCE", "ALW");
        raceCodes.put("CLAIMING", "CLM");
        raceCodes.put("FUTURITY", "FUT");
        raceCodes.put("HANDICAP", "HCP");
        raceCodes.put("MATURITY", "MAT");
        raceCodes.put("MAIDEN", "MDN");
        raceCodes.put("STAKES", "STK");
        raceCodes.put("TRIALS", "TRL");
        raceCodes.put("DERBY", "DBY");
        raceCodes.put("FINAL", "FNL");
        raceCodes.put("MATCH", "MCH");
        raceCodes.put("STAKE", "STK");
        raceCodes.put("TRIAL", "TRL");
        RACE_TYPE_CODES = Collections.unmodifiableMap(raceCodes);
    }

    static final Pattern RACE_TYPE_NAME_GRADE_BREED =
            Pattern.compile("^(" + String.join("|", RACE_TYPE_CODES.keySet()) + ")\\s+(.+?)?\\s?" +
                    "(Grade ([123])|Listed|Black Type)?\\s*-\\s*(Thoroughbred|Quarter " +
                    "Horse|Arabian|Mixed)$");

    private static final Logger LOGGER = LoggerFactory.getLogger(RaceTypeNameBlackTypeBreed.class);

    private final String type;
    @JsonInclude(NON_NULL)
    private final String code;
    private final String name;
    private final Integer grade;
    private final String blackType;
    private final Breed breed;

    public RaceTypeNameBlackTypeBreed(String type, Breed breed) {
        this(type, null, breed);
    }

    public RaceTypeNameBlackTypeBreed(String type, String name, Breed breed) {
        this(type, name, null, breed);
    }

    public RaceTypeNameBlackTypeBreed(String type, String name, String blackType, Breed breed) {
        this(type, name, null, blackType, breed);
    }

    public RaceTypeNameBlackTypeBreed(String type, String name, Integer grade, String blackType,
            Breed breed) {
        this(type,
                // code
                RACE_TYPE_CODES.getOrDefault(type, null),
                name, grade, blackType, breed);
    }

    @JsonCreator
    public RaceTypeNameBlackTypeBreed(String type, String code, String name, Integer grade,
            String blackType, Breed breed) {
        this.type = type;
        this.code = code;
        this.name = name;
        this.grade = grade;
        this.blackType = blackType;
        this.breed = breed;
    }

    public static RaceTypeNameBlackTypeBreed parse(List<List<ChartCharacter>> lines)
            throws Breed.NoMatchingBreedException, RaceTypeNameOrBreedNotIdentifiable {
        for (List<ChartCharacter> line : lines) {
            String rawText = Chart.convertToText(line);
            RaceTypeNameBlackTypeBreed raceTypeNameGradeBreed =
                    parseRaceTypeNameBlackTypeBreed(rawText);
            if (raceTypeNameGradeBreed != null) {
                return raceTypeNameGradeBreed;
            }
        }
        throw new RaceTypeNameOrBreedNotIdentifiable("Unable to identify a valid race type, name " +
                "and/or breed");
    }

    static RaceTypeNameBlackTypeBreed parseRaceTypeNameBlackTypeBreed(String text)
            throws Breed.NoMatchingBreedException {
        // GRAND PRAIRIE (GPR) edge-case handling
        if (text.startsWith("Claiming stake ")) {
            text = text.replace("Claiming stake ", "CLAIMING STAKES ");
        }

        Matcher matcher = RACE_TYPE_NAME_GRADE_BREED.matcher(text);
        if (matcher.find()) {
            String breedText = matcher.group(5);
            Breed breed = Breed.forChartValue(breedText);
            String type = matcher.group(1);
            if (type == null) {
                LOGGER.warn("A race type was not found from text: " + text);
            } else {
                type = type.trim();
            }

            Integer grade = null;
            String blackType = matcher.group(3);
            if (blackType != null) {
                String gradeText = matcher.group(4);
                if (gradeText != null) {
                    grade = Integer.parseInt(gradeText);
                }
            }

            String name = (matcher.group(2) != null ? matcher.group(2).trim() : null);

            return new RaceTypeNameBlackTypeBreed(type, name, grade, blackType, breed);
        }
        return null;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getGrade() {
        return grade;
    }

    public String getBlackType() {
        return blackType;
    }

    public Breed getBreed() {
        return breed;
    }

    @Override
    public String toString() {
        return "RaceTypeNameBlackTypeBreed{" +
                "type='" + type + '\'' +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", grade=" + grade +
                ", blackType='" + blackType + '\'' +
                ", breed=" + breed +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RaceTypeNameBlackTypeBreed that = (RaceTypeNameBlackTypeBreed) o;

        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (grade != null ? !grade.equals(that.grade) : that.grade != null) return false;
        if (blackType != null ? !blackType.equals(that.blackType) : that.blackType != null)
            return false;
        return breed == that.breed;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (grade != null ? grade.hashCode() : 0);
        result = 31 * result + (blackType != null ? blackType.hashCode() : 0);
        result = 31 * result + (breed != null ? breed.hashCode() : 0);
        return result;
    }

    public static class RaceTypeNameOrBreedNotIdentifiable extends ChartParserException {
        public RaceTypeNameOrBreedNotIdentifiable(String message) {
            super(message);
        }
    }
}
