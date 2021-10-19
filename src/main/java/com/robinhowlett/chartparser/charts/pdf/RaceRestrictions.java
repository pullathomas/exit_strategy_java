package com.robinhowlett.chartparser.charts.pdf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Parses the race conditions text to determine, if possible, the age, gender, and breeding location
 * restrictions imposed for the race
 */
public class RaceRestrictions {

    public static final int ALL_SEXES = 31;

    private static final Map<Integer, String> SEXES_CODES;
    static {
        Map<Integer, String> sexesCodes = new LinkedHashMap<>();
        sexesCodes.put(0, null);
        sexesCodes.put(1, "C");
        sexesCodes.put(2, "G");
        sexesCodes.put(3, "C&G");
        sexesCodes.put(4, "H");
        sexesCodes.put(5, "C&H");
        sexesCodes.put(6, "G&H");
        sexesCodes.put(7, "C&G&H");
        sexesCodes.put(8, "F");
        sexesCodes.put(9, "C&F");
        sexesCodes.put(10, "G&F");
        sexesCodes.put(11, "C&G&F");
        sexesCodes.put(12, "F&H");
        sexesCodes.put(13, "C&H&F");
        sexesCodes.put(14, "G&H&F");
        sexesCodes.put(15, "C&G&H&F");
        sexesCodes.put(16, "M");
        sexesCodes.put(17, "C&M");
        sexesCodes.put(18, "G&M");
        sexesCodes.put(19, "C&G&M");
        sexesCodes.put(20, "H&M");
        sexesCodes.put(21, "C&H&M");
        sexesCodes.put(22, "G&H&M");
        sexesCodes.put(23, "C&G&H&M");
        sexesCodes.put(24, "F&M");
        sexesCodes.put(25, "C&F&M");
        sexesCodes.put(26, "G&F&M");
        sexesCodes.put(27, "C&G&F&M");
        sexesCodes.put(28, "H&F&M");
        sexesCodes.put(29, "C&H&F&M");
        sexesCodes.put(30, "G&H&F&M");
        sexesCodes.put(ALL_SEXES, "A");
        SEXES_CODES = Collections.unmodifiableMap(sexesCodes);
    }

    // matches text within parentheses e.g. "(S)", "(NW1 L)", and "( C)" etc.
    private static final Pattern PARENTHESES_TEXT = Pattern.compile("(\\s*\\([^)]+\\)\\s*)");
    // e.g. matches "(c)", "(s)", "(snw1 y x)", "(nw2 l)" etc.
    private static final Pattern RESTRICTIONS_CODE =
            Pattern.compile("\\(\\s*([c])\\s*\\)|\\(\\s*([s])\\s*\\)|\\((s?nw[^)]+)\\)");
    // matches "weight 120 lbs"
    private static final Pattern WEIGHT_DETECTION =
            Pattern.compile(".*\\bweight \\d{3}\\s?lbs\\b.*");

    // to catch e.g. " allowed 120 lbs", " 119 lbs)"
    private static final String FALSE_POSITIVE_DETECTION =
            "((\\s?allowed)?(\\s?\\d?\\d?\\d\\s?lbs)?)?";

    /*
    2 year olds
    3 years old & older
    3 & up
    4yo
    3 & 4 year olds
    3 4 & 5 year olds
    4 & 5 & 6 year olds
    2yrs
    3yos
    4yrs & older
    4 5 6 & 7 year olds
     */
    private static final String AGES = "((((?<!#)\\b\\d)(\\s&?\\s?(\\d?\\d))?(\\s&?\\s?(\\d?\\d))?" +
            "(\\s&?\\s?(\\d?\\d))?(\\s&?\\s?(\\d?\\d))?(\\s?yrs? olds?|yrs?|yos?|\\s?years? " +
            "olds?))( & (upwards?|up\\b|olders?))?|(\\b\\d\\b)( & (upwards?|up\\b)))" +
            FALSE_POSITIVE_DETECTION;

    /*
    colts geldings & horses
    colts geldings & fillies
    colts geldings & mares
    colts & geldings
    fillies & mares
    fillies
    colts
    colts & fillies
     */
    private static final String SEXES_REGEX =
            "(colts|fillies|mares)( (geldings))?( & (geldings|mares|horses|fillies))?" +
                    FALSE_POSITIVE_DETECTION;

    private static final Pattern AGES_PATTERN = Pattern.compile(AGES);
    private static final Pattern SEXES_PATTERN = Pattern.compile(SEXES_REGEX);

    private static final Pattern SEXES_THEN_AGES = Pattern.compile(
            "^(for.+?|.+?for.+?|.*?)?" + SEXES_REGEX + "[^\\d]*(" + AGES + ")?.*");
    private static final Pattern AGES_THEN_SEXES = Pattern.compile(
            "^(for.+?|.*?)?" + AGES + ".*?(?=" + SEXES_REGEX + "|$).*");

    @JsonInclude(NON_NULL)
    private final String code;
    private final Integer minAge;
    private final Integer maxAge;
    private final String ageCode;

    /*
     a bitwise-style value to store the gender restrictions that apply

     1 = colts
     2 = geldings
     4 = horses
     8 = fillies
     16 = mares
     31 = all
      */
    private final int sexes;
    private final String sexesCode;
    private final boolean femaleOnly;
    private final boolean stateBred;

    RaceRestrictions(RaceRestrictionCodes raceRestrictionCodes, Integer minAge, Integer maxAge,
            int sexes) {
        this((raceRestrictionCodes != null ? raceRestrictionCodes.getCode() : null),
                minAge, maxAge, sexes,
                (raceRestrictionCodes != null && raceRestrictionCodes.isStateBred()));
    }

    public RaceRestrictions(String code, Integer minAge, Integer maxAge, int sexes, boolean stateBred) {
        this(code, minAge,
                // maxAge: if no specified max, then assume same as the minimum
                (maxAge != null ? maxAge : minAge),
                // ageCode
                createAgeCode(minAge, (maxAge != null ? maxAge : minAge)),
                sexes,
                // sexesCode
                SEXES_CODES.getOrDefault(sexes, null),
                // femaleOnly: 8 = fillies, 16 = mares, 24 = fillies & mares
                (sexes % 8 == 0),
                stateBred);
    }

    @JsonCreator
    public RaceRestrictions(String code, Integer minAge, Integer maxAge, String ageCode, int
            sexes, String sexesCode, boolean femaleOnly, boolean stateBred) {
        this.code = code;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.ageCode = ageCode;
        this.sexes = sexes;
        this.sexesCode = sexesCode;
        this.femaleOnly = femaleOnly;
        this.stateBred = stateBred;
    }

    static String createAgeCode(Integer minAge, Integer maxAge) {
        if (minAge != null) {
            if (minAge == maxAge) {
                return String.valueOf(minAge);
            } else if (maxAge == -1) {
                return String.valueOf(minAge) + "+";
            } else {
                return String.valueOf(minAge) + "-" + String.valueOf(maxAge);
            }
        } else {
            return null;
        }
    }

    public static RaceRestrictions parse(String raceConditionsText) {
        String text = cleanUpText(raceConditionsText);

        // 9. extract parenthesis and pattern match for (C) or (S) or (SNW...) or (NW1...)
        Matcher parenMatcher = PARENTHESES_TEXT.matcher(text);
        List<String> textInParentheses = new ArrayList<>();
        while (parenMatcher.find()) {
            textInParentheses.add(parenMatcher.group());
        }

        // 10. identify the short race restriction code and whether the race was for state-bred
        // horses
        RaceRestrictionCodes raceRestrictionCodes = parseRestrictionsCode(textInParentheses);

        // 11. remove the parentheses-contained texts from the main race conditions text
        for (String match : textInParentheses) {
            text = text.replaceAll(Pattern.quote(match), " ").trim();
        }

        RaceRestrictions raceRestrictions = buildRaceRestrictions(text, raceRestrictionCodes);

        if (raceRestrictions != null) {
            return raceRestrictions;
        }

        return new RaceRestrictions(raceRestrictionCodes, null, null, ALL_SEXES);
    }

    static String cleanUpText(String raceConditionsText) {
        return raceConditionsText
                // 1. convert to lowercase
                .toLowerCase()
                // 2. replace edge-case typos
                .replaceAll("\\by-year-olds\\b", "year olds")
                .replaceAll("\\bfillies/mares\\b", "fillies and mares")
                .replaceAll("\\b(thre|theee)\\b", "three")
                .replaceAll("\\bthreeyear\\b", "three year")
                .replaceAll("\\bttwo\\b", "two")
                .replaceAll("\\bmaresthree\\b", "mares three")
                .replaceAll("\\btears? olds?\\b", "year olds")
                // 3. replace punctuation with space character
                .replaceAll("[.,:;\\[\\]\\-\"'%+\\\\/*!]", " ")
                // 4. ensure spaces around &, and parentheses; not after #
                .replaceAll("&", " & ")
                .replaceAll("# ", "#")
                .replaceAll("\\(\\s*", " (")
                .replaceAll("\\s*\\)", ") ")
                // 5. remove text contained in angle bracket
                .replaceAll("<.+>\\s?", "")
                // 6. fix common typos
                .replaceAll("\\b(fof|f0r|fo|foe|fofor|foor|ffor)\\b", "for")
                .replaceAll("\\b(colt)\\b", "colts")
                .replaceAll("\\b(gelding)\\b", "geldings")
                .replaceAll("\\b(filly|filiies|filles|filllies|filies|fillie|fililies|filliies" +
                        "|fllies|filli\\ses|fillie\\ss)\\b", "fillies")
                .replaceAll("\\b(mare|maress|mareds|marees|amres)\\b", "mares")
                .replaceAll("\\b(yaer|yera|yr|yar|yer|yers)\\b", "years")
                .replaceAll("\\b(and|adn|ands|und|amd|ans|a\\snd|an\\sd)\\b", "&")
                .replaceAll("\\b(oldsa|olda|ols|0lds|onld)\\b", "olds")
                .replaceAll("\\b(up|upaward|uwpard|uward|upwrd|upqward|upwa|upwar)\\b", "upwards")
                // 7. replace numeric words with their digit equivalent
                .replaceAll("\\b(one)\\b", "1")
                .replaceAll("\\b(two)\\b", "2")
                .replaceAll("\\b(three)\\b", "3")
                .replaceAll("\\b(four)\\b", "4")
                .replaceAll("\\b(five)\\b", "5")
                .replaceAll("\\b(six)\\b", "6")
                .replaceAll("\\b(seven)\\b", "7")
                .replaceAll("\\b(eight)\\b", "8")
                .replaceAll("\\b(nine)\\b", "9")
                .replaceAll("\\b(ten)\\b", "10")
                .replaceAll("\\b(eleven)\\b", "11")
                .replaceAll("\\b(twelve)\\b", "12")
                // 8. replace consecutive spaces or tabs with a single space
                .replaceAll("\\s{2,}|\\t{1,}", " ");
    }

    private static RaceRestrictionCodes parseRestrictionsCode(List<String> matches) {
        for (String match : matches) {
            Matcher codeMatcher = RESTRICTIONS_CODE.matcher(match);
            if (codeMatcher.find()) {
                if (codeMatcher.group(1) != null) {
                    // complex, compound, combination (?)
                    return new RaceRestrictionCodes("C", false);
                } else if (codeMatcher.group(2) != null) {
                    // state-bred
                    return new RaceRestrictionCodes(null, true);
                } else if (codeMatcher.group(3) != null) {
                    boolean stateBred = false;
                    String group = codeMatcher.group(3);
                    // identify state bred restriction
                    if (group.contains("s")) {
                        group = group.replaceAll("s", "");
                        stateBred = true;
                    }
                    return new RaceRestrictionCodes(group.toUpperCase(Locale.US), stateBred);
                }
            }
        }
        return null;
    }

    private static RaceRestrictions buildRaceRestrictions(String text, RaceRestrictionCodes
            raceRestrictionCodes) {
        // 12. attempt to identify multiple conditions e.g. 3yo fillies or 4yo mares
        String[] conditions = text.split("\\bor\\b");

        boolean weightDetected = false;

        RaceRestrictions conditionRestrictions, raceRestrictions = null;
        for (int i = 0; i < conditions.length && !weightDetected; i++) {
            String condition = conditions[i];

            if (condition == null || condition.isEmpty()) {
                continue;
            }

            Matcher weightMatcher = WEIGHT_DETECTION.matcher(condition);
            if (weightMatcher.find()) {
                weightDetected = true;
            }

            // 13. check whether the age restriction comes first or the sexes restriction
            AgeSexPattern ageSexPattern = determineAgeSexPatternToUse(condition.trim());

            if (ageSexPattern == null) {
                continue;
            }

            // 14. extract the age- and sexes restrictions for this section of conditions
            conditionRestrictions = createRaceRestrictionsFromConditions(raceRestrictions,
                    condition.trim(), raceRestrictionCodes, ageSexPattern);

            if (conditionRestrictions != null) {
                // 15. if condition restrictions were previously identified, merge them together
                if (raceRestrictions != null) {
                    conditionRestrictions = mergeConditionRestrictions(raceRestrictionCodes,
                            conditionRestrictions, raceRestrictions);

                }
                raceRestrictions = conditionRestrictions;
            }
        }
        return raceRestrictions;
    }

    private static AgeSexPattern determineAgeSexPatternToUse(String condition) {
        int agePosition = Integer.MAX_VALUE;
        int sexPosition = Integer.MAX_VALUE;

        Matcher matcher = AGES_PATTERN.matcher(condition);
        if (matcher.find()) {
            agePosition = matcher.start();
        }

        matcher = SEXES_PATTERN.matcher(condition);
        if (matcher.find()) {
            sexPosition = matcher.start();
        }

        if (agePosition == sexPosition) {
            return null;
        } else {
            return (agePosition < sexPosition ?
                    new AgeSexPattern(20, 4, AGES_THEN_SEXES) :
                    new AgeSexPattern(0, 13, SEXES_THEN_AGES));
        }
    }

    private static RaceRestrictions createRaceRestrictionsFromConditions(
            RaceRestrictions raceRestrictions, String conditions,
            RaceRestrictionCodes raceRestrictionCodes, AgeSexPattern ageSexPattern) {
        Matcher matcher = ageSexPattern.getPattern().matcher(conditions);
        if (matcher.matches()) {
            // sexes
            int sexes = getBitwiseSexesValue(matcher, ageSexPattern.getSexOffset());

            // regular ages (e.g. 3yrs, 3 years old, 3 & 4 year olds, 3 year olds & older...)
            String firstAgeGroup = matcher.group(ageSexPattern.getAgeOffset());
            String secondAgeGroup = matcher.group(ageSexPattern.getAgeOffset() + 2);
            String thirdAgeGroup = matcher.group(ageSexPattern.getAgeOffset() + 4);
            String fourthAgeGroup = matcher.group(ageSexPattern.getAgeOffset() + 6);
            String fifthAgeGroup = matcher.group(ageSexPattern.getAgeOffset() + 8);
            String yearsOldGroup = matcher.group(ageSexPattern.getAgeOffset() + 9);
            String andOlderGroup = matcher.group(ageSexPattern.getAgeOffset() + 10);

            // irregular ages (e.g. 3 and up, 4 and upwards)
            String altFirstAgeGroup = matcher.group(ageSexPattern.getAgeOffset() + 12);
            String altAndOlderGroup = matcher.group(ageSexPattern.getAgeOffset() + 13);

            // try to filter out false positives by detecting age-based weight-related information
            // e.g. "Three Year Olds 119 lbs" should be ignored
            String falsePositiveGroup = matcher.group(ageSexPattern.getAgeOffset() + 15);

            // false positives can have, well, false positives e.g. Maidens 2 year olds 120 lbs
            // continue if...
            // 1. a false positive was not detected
            // 2. a false positive was detected but yet to have constructed a RaceRestriction,
            // meaning it most likely isn't a false positive
            if ((falsePositiveGroup == null || falsePositiveGroup.trim().isEmpty()) ||
                    (falsePositiveGroup != null && raceRestrictions == null)) {
                if (yearsOldGroup != null) {
                    if (firstAgeGroup != null) {
                        // min age identified, can proceed
                        int ageMin = Integer.parseInt(firstAgeGroup);

                        Integer ageMax = null;
                        if (andOlderGroup != null) {                    // "and upward|older|up"
                            ageMax = -1;
                        } else {
                            if (fifthAgeGroup != null) {
                                ageMax = Integer.parseInt(fifthAgeGroup);
                            } else if (fourthAgeGroup != null) {
                                ageMax = Integer.parseInt(fourthAgeGroup);
                            } else if (thirdAgeGroup != null) {
                                ageMax = Integer.parseInt(thirdAgeGroup);
                            } else if (secondAgeGroup != null) {
                                ageMax = Integer.parseInt(secondAgeGroup);
                            }
                        }

                        return new RaceRestrictions(raceRestrictionCodes, ageMin, ageMax, sexes);
                    }
                } else if (altFirstAgeGroup != null && altAndOlderGroup != null) {
                    int ageMin = Integer.parseInt(altFirstAgeGroup);
                    int ageMax = -1;

                    return new RaceRestrictions(raceRestrictionCodes, ageMin, ageMax, sexes);
                }
            }
        }

        return null;
    }

    /*
    1 = colts
    2 = geldings
    4 = horses
    8 = fillies
    16 = mares
    31 = all
     */
    private static int getBitwiseSexesValue(Matcher matcher, int seed) {
        int sexes = 0;

        // try to filter out false positives by detecting weight-related information early
        // e.g. FOR THREE YEAR OLDS AND UPWARD. All Fillies and Mares allowed 3 lbs.
        // it should not identify the race as for Fillies and Mares only
        String falsePositiveGroup = matcher.group(7 + seed);

        String firstSexGroup = matcher.group(2 + seed);
        if ((falsePositiveGroup == null || falsePositiveGroup.trim().isEmpty()) &&
                firstSexGroup != null) {
            if (firstSexGroup.equals("colts")) {
                sexes += 1;
            } else if (firstSexGroup.equals("fillies")) {
                sexes += 8;
            } else if (firstSexGroup.equals("mares")) {
                sexes += 16;
            }

            String secondSexGroup = matcher.group(4 + seed);
            if (secondSexGroup != null && secondSexGroup.equals("geldings")) {
                sexes += 2;
            }

            String thirdSexGroup = matcher.group(6 + seed);
            if (thirdSexGroup != null) {
                if (thirdSexGroup.equals("geldings")) {
                    sexes += 2;
                } else if (thirdSexGroup.equals("horses")) {
                    sexes += 4;
                } else if (thirdSexGroup.equals("fillies")) {
                    sexes += 8;
                } else if (thirdSexGroup.equals("mares")) {
                    sexes += 16;
                }
            }
        } else {
            sexes += ALL_SEXES;
        }
        return sexes;
    }

    private static RaceRestrictions mergeConditionRestrictions(
            RaceRestrictionCodes raceRestrictionCodes, RaceRestrictions conditionRestrictions,
            RaceRestrictions raceRestrictions) {
        Integer ageMin;
        if (raceRestrictions.getMinAge() == null) {
            ageMin = conditionRestrictions.getMinAge();
        } else if (conditionRestrictions.getMinAge() == null) {
            ageMin = raceRestrictions.getMinAge();
        } else {
            ageMin = Math.min(raceRestrictions.getMinAge(), conditionRestrictions.getMinAge());
        }

        Integer ageMax = null;
        if (raceRestrictions.getMaxAge() == null) {
            if (conditionRestrictions.getMaxAge() != null) {
                ageMax = (conditionRestrictions.getMaxAge() < 0) ? -1 : null;
            }
        } else if (conditionRestrictions.getMaxAge() == null) {
            if (raceRestrictions.getMaxAge() != null) {
                ageMax = (raceRestrictions.getMaxAge() < 0) ? -1 : null;
            }
        } else {
            if (conditionRestrictions.getMaxAge() != null && raceRestrictions.getMaxAge() != null) {
                if (raceRestrictions.getMaxAge() < 0 || conditionRestrictions.getMaxAge() < 0) {
                    ageMax = -1;
                } else {
                    Math.max(raceRestrictions.getMaxAge(), conditionRestrictions.getMaxAge());
                }
            }
        }

        int sexes = (raceRestrictions.getSexes() == conditionRestrictions.getSexes()) ?
                raceRestrictions.getSexes() :
                (Math.min(ALL_SEXES, raceRestrictions.getSexes() + conditionRestrictions.getSexes()));

        return new RaceRestrictions(raceRestrictionCodes, ageMin, ageMax, sexes);
    }

    public String getCode() {
        return code;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public String getAgeCode() {
        return ageCode;
    }

    public int getSexes() {
        return sexes;
    }

    public String getSexesCode() {
        return sexesCode;
    }

    public boolean isFemaleOnly() {
        return femaleOnly;
    }

    public boolean isStateBred() {
        return stateBred;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RaceRestrictions that = (RaceRestrictions) o;

        if (sexes != that.sexes) return false;
        if (femaleOnly != that.femaleOnly) return false;
        if (stateBred != that.stateBred) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (minAge != null ? !minAge.equals(that.minAge) : that.minAge != null) return false;
        if (maxAge != null ? !maxAge.equals(that.maxAge) : that.maxAge != null) return false;
        if (ageCode != null ? !ageCode.equals(that.ageCode) : that.ageCode != null) return false;
        return sexesCode != null ? sexesCode.equals(that.sexesCode) : that.sexesCode == null;
    }

    @Override
    public int hashCode() {
        int result = code != null ? code.hashCode() : 0;
        result = 31 * result + (minAge != null ? minAge.hashCode() : 0);
        result = 31 * result + (maxAge != null ? maxAge.hashCode() : 0);
        result = 31 * result + (ageCode != null ? ageCode.hashCode() : 0);
        result = 31 * result + sexes;
        result = 31 * result + (sexesCode != null ? sexesCode.hashCode() : 0);
        result = 31 * result + (femaleOnly ? 1 : 0);
        result = 31 * result + (stateBred ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RaceRestrictions{" +
                "code='" + code + '\'' +
                ", minAge=" + minAge +
                ", maxAge=" + maxAge +
                ", ageCode='" + ageCode + '\'' +
                ", sexes=" + sexes +
                ", sexesCode='" + sexesCode + '\'' +
                ", femaleOnly=" + femaleOnly +
                ", stateBred=" + stateBred +
                '}';
    }

    private static class RaceRestrictionCodes {
        private final String code;
        private final boolean stateBred;

        public RaceRestrictionCodes(String code, boolean stateBred) {
            this.code = code;
            this.stateBred = stateBred;
        }

        public String getCode() {
            return code;
        }

        public boolean isStateBred() {
            return stateBred;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RaceRestrictionCodes that = (RaceRestrictionCodes) o;

            if (stateBred != that.stateBred) return false;
            return code != null ? code.equals(that.code) : that.code == null;
        }

        @Override
        public int hashCode() {
            int result = code != null ? code.hashCode() : 0;
            result = 31 * result + (stateBred ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "RaceRestrictionCodes{" +
                    "code='" + code + '\'' +
                    ", stateBred=" + stateBred +
                    '}';
        }
    }

    private static class AgeSexPattern {
        private final int sexOffset;
        private final int ageOffset;
        private final Pattern pattern;

        public AgeSexPattern(int sexOffset, int ageOffset, Pattern pattern) {
            this.sexOffset = sexOffset;
            this.ageOffset = ageOffset;
            this.pattern = pattern;
        }

        public int getSexOffset() {
            return sexOffset;
        }

        public int getAgeOffset() {
            return ageOffset;
        }

        public Pattern getPattern() {
            return pattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AgeSexPattern that = (AgeSexPattern) o;

            if (sexOffset != that.sexOffset) return false;
            if (ageOffset != that.ageOffset) return false;
            return pattern != null ? pattern.equals(that.pattern) : that.pattern == null;
        }

        @Override
        public int hashCode() {
            int result = sexOffset;
            result = 31 * result + ageOffset;
            result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "AgeSexPattern{" +
                    "sexOffset=" + sexOffset +
                    ", ageOffset=" + ageOffset +
                    ", pattern=" + pattern +
                    '}';
        }
    }
}
