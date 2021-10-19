package com.robinhowlett.chartparser.charts.pdf;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The registered run-up, measured in feet (the distance between the starting stalls and when the
 * race timer is actually started - a longer run up means the horses are potentially traveling at a
 * much higher speed already versus a short run-up)
 */
public class RunUpTemporaryRail {

    private static final Pattern RUN_UP_PATTERN = Pattern.compile("Run-Up: ?(\\d+) feet( " +
            "Temporary Rail: ?(\\d+) feet)?");

    private final Integer runUp;
    private final Integer tempRail;

    public RunUpTemporaryRail(Integer runUp, Integer tempRail) {
        this.runUp = runUp;
        this.tempRail = tempRail;
    }

    public static RunUpTemporaryRail parse(List<List<ChartCharacter>> runningLines) {
        Integer runUpInFeet = null;
        Integer temporaryRailInFeet = null;
        if (runningLines != null) {
            for (int i = 0; i < runningLines.size(); i++) {
                String runUpCandidate = Chart.convertToText(runningLines.get(i));
                if (runUpCandidate.startsWith("Run-Up:")) {
                    runningLines.remove(i);

                    Matcher matcher = RUN_UP_PATTERN.matcher(runUpCandidate);
                    if (matcher.find()) {
                        runUpInFeet = Integer.parseInt(matcher.group(1));

                        String rail = matcher.group(2);
                        if (rail != null) {
                            temporaryRailInFeet = Integer.parseInt(matcher.group(3));
                        }
                        break;
                    }
                }
            }
        }
        return new RunUpTemporaryRail(runUpInFeet, temporaryRailInFeet);
    }

    public Integer getRunUp() {
        return runUp;
    }

    public Integer getTempRail() {
        return tempRail;
    }

    @Override
    public String toString() {
        return "RunUpTemporaryRail{" +
                "runUp=" + runUp +
                ", tempRail=" + tempRail +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RunUpTemporaryRail runUpTemporaryRail1 = (RunUpTemporaryRail) o;

        if (runUp != null ? !runUp.equals(runUpTemporaryRail1.runUp) : runUpTemporaryRail1.runUp != null) return false;
        return tempRail != null ? tempRail.equals(runUpTemporaryRail1.tempRail) : runUpTemporaryRail1.tempRail == null;
    }

    @Override
    public int hashCode() {
        int result = runUp != null ? runUp.hashCode() : 0;
        result = 31 * result + (tempRail != null ? tempRail.hashCode() : 0);
        return result;
    }
}
