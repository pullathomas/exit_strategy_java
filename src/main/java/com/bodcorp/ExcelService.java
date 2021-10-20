package com.bodcorp;//package com.robinhowlett.handycapper.services.excel;

import com.robinhowlett.chartparser.charts.pdf.DistanceSurfaceTrackRecord;
import com.robinhowlett.chartparser.charts.pdf.DistanceSurfaceTrackRecord.RaceDistance;
import com.robinhowlett.chartparser.charts.pdf.RaceResult;
import com.robinhowlett.chartparser.charts.pdf.RaceTypeNameBlackTypeBreed;
import com.robinhowlett.chartparser.charts.pdf.Starter;
import com.robinhowlett.chartparser.charts.pdf.running_line.LastRaced;
import com.robinhowlett.chartparser.charts.pdf.wagering.WagerPayoffPools;
import com.robinhowlett.chartparser.fractionals.FractionalPoint;
import com.robinhowlett.chartparser.fractionals.FractionalPoint.Fractional;
import com.robinhowlett.chartparser.fractionals.FractionalPoint.Split;
import com.robinhowlett.chartparser.points_of_call.PointsOfCall.PointOfCall;
import com.robinhowlett.chartparser.points_of_call.PointsOfCall.PointOfCall.RelativePosition
        .TotalLengthsBehind;

import com.robinhowlett.chartparser.tracks.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.bodcorp.XSSFCellCreator.asBoolean;
import static com.bodcorp.XSSFCellCreator.asDate;
import static com.bodcorp.XSSFCellCreator.asNumber;
import static com.bodcorp.XSSFCellCreator.asText;

@Service
public class ExcelService {
    private CreationHelper helper;
    private CellStyle dateFormat;
    private CellStyle twoDigitFormat;
    private CellStyle threeDigitFormat;
    private CellStyle commaNumberFormat;
    private CellStyle twoDigitCommaFormat;
    private int maxCols;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelService.class);

    public XSSFWorkbook create(List<RaceResult> raceResults) {
        XSSFWorkbook workbook = new XSSFWorkbook();

        maxCols = 0;
        helper = workbook.getCreationHelper();

        dateFormat = workbook.createCellStyle();
        dateFormat.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));

        twoDigitFormat = workbook.createCellStyle();
        twoDigitFormat.setDataFormat(helper.createDataFormat().getFormat("0.00"));

        threeDigitFormat = workbook.createCellStyle();
        threeDigitFormat.setDataFormat(helper.createDataFormat().getFormat("0.000"));

        commaNumberFormat = workbook.createCellStyle();
        commaNumberFormat.setDataFormat(helper.createDataFormat().getFormat("#,##0"));

        twoDigitCommaFormat = workbook.createCellStyle();
        twoDigitCommaFormat.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));

        createResultsSheets(raceResults, workbook);
        try {
            createWageringSheet(raceResults, workbook);
        }
        catch (Exception e){
            LOGGER.error("Failed to create Wagering Sheet");
            LOGGER.error(e.getMessage());
        }

        try {
            createBreedingSheet(raceResults, workbook);

        }
        catch (Exception e){
            LOGGER.error("Failed to create Breeding Sheet");
            LOGGER.error(e.getMessage());
        }
        return workbook;
    }

    XSSFWorkbook createResultsSheets(List<RaceResult> raceResults,
            XSSFWorkbook workbook) {
        for (int i = 0; i < raceResults.size(); i++) {
            RaceResult raceResult = raceResults.get(i);
            validate(raceResult);
            if(raceResult.getCancellation().isCancelled()){
                continue;
            }
            String raceShortDescription = raceResult.getRaceDate()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    .concat(raceResult.getTrack().getCode())
                    .concat("R" + (raceResult.getRaceNumber() != null ?
                            raceResult.getRaceNumber() : "X"));

            //for data factory ease
            raceShortDescription = "Sheet"+(raceResult.getRaceNumber() != null ? raceResult.getRaceNumber() : "X");
            // Results
            XSSFSheet sheet = workbook.createSheet(raceShortDescription);
            int row = 0;
            int col = 0;

            createResultsHeaderRow(sheet, row++, col, raceResult);

            if (raceResult.getStarters() != null) {
                for (Starter starter : raceResult.getStarters()) {
                    int startersColNum = 0;
                    XSSFRow starterRow = sheet.createRow(row++);
                    DistanceSurfaceTrackRecord.RaceDistance raceDistance =
                            raceResult.getDistanceSurfaceTrackRecord().getRaceDistance();

                    startersColNum = fillStandardCols(raceResult, starter, startersColNum,
                            starterRow, raceDistance);

                    // weight
                    asNumber(starterRow, startersColNum++, starter.getWeight().getWeightCarried());

                    // runUp
                    asNumber(starterRow, startersColNum++, raceDistance.getRunUp());

                    // # of runners
                    asNumber(starterRow, startersColNum++, raceResult.getNumberOfRunners());

                    // last raced track
                    LastRaced lastRaced = starter.getLastRaced();
                    asText(starterRow, startersColNum++, (lastRaced != null ?
                            lastRaced.getLastRacePerformance().getTrack().getCode() : null));

                    // days since
                    asNumber(starterRow, startersColNum++, (lastRaced != null ?
                            lastRaced.getDaysSince() : null));

                    // individual fractionals
                    List<FractionalPoint.Fractional> fractionals = starter.getFractionals();
                    if (fractionals != null) {
                        for (int j = 0; j < fractionals.size(); j++) {
                            FractionalPoint.Fractional fractional = fractionals.get(j);
                            Long millis = fractional.getMillis();
                            asNumber(starterRow, startersColNum++,
                                    (millis != null ? (double) millis / 1000 : null))
                                    .setCellStyle(threeDigitFormat);
                        }
                    }

                    // individual splits
                    List<FractionalPoint.Split> splits = starter.getSplits();
                    if (splits != null) {
                        for (int j = 0; j < splits.size(); j++) {
                            FractionalPoint.Split split = splits.get(j);
                            Long millis = split.getMillis();
                            asNumber(starterRow, startersColNum++,
                                    (millis != null ? (double) millis / 1000 : null))
                                    .setCellStyle(threeDigitFormat);
                        }
                    }

                    // points of call
                    List<PointOfCall> pointsOfCall = starter.getPointsOfCall();
                    if (pointsOfCall != null) {
                        // position
                        for (int j = 0; j < pointsOfCall.size(); j++) {
                            PointOfCall pointOfCall = pointsOfCall.get(j);
                            PointOfCall.RelativePosition relativePosition = pointOfCall
                                    .getRelativePosition();
                            asNumber(starterRow, startersColNum++,
                                    (relativePosition != null ? relativePosition.getPosition() :
                                            null));
                        }
                        // lengths behind
                        for (int j = 0; j < pointsOfCall.size(); j++) {
                            PointOfCall pointOfCall = pointsOfCall.get(j);
                            PointOfCall.RelativePosition relativePosition = pointOfCall
                                    .getRelativePosition();
                            TotalLengthsBehind totalLengthsBehind = null;
                            if (relativePosition != null) {
                                totalLengthsBehind = relativePosition.getTotalLengthsBehind();
                            }
                            asNumber(starterRow, startersColNum++,
                                    (totalLengthsBehind != null) ?
                                            totalLengthsBehind.getLengths() : 0)
                                    .setCellStyle(twoDigitFormat);
                        }
                    }

                    // leader fractionals
                    /*
                    List<FractionalPoint.Fractional> leaderFractionals = raceResult
                            .getFractionals();
                    if (leaderFractionals != null) {
                        for (int j = 0; j < leaderFractionals.size(); j++) {
                            Fractional fractional = leaderFractionals.get(j);
                            Long millis = fractional.getMillis();
                            asNumber(starterRow, startersColNum++,
                                    (millis != null ? (double) millis / 1000 : null))
                                    .setCellStyle(threeDigitFormat);
                        }
                    }

                    // leader splits
                    List<Split> leaderSplits = raceResult.getSplits();
                    if (leaderSplits != null) {
                        for (int j = 0; j < leaderSplits.size(); j++) {
                            Split split = leaderSplits.get(j);
                            Long millis = split.getMillis();
                            asNumber(starterRow, startersColNum++,
                                    (millis != null ? (double) millis / 1000 : null))
                                    .setCellStyle(threeDigitFormat);
                        }
                    }

                     */
                }
            }

            try {
                printAsCsv(sheet, raceResult);
            }
            catch(IOException io){
                System.out.println(io.getMessage());
            }

        }
        return workbook;
    }

    private void printAsCsv(XSSFSheet sheet, RaceResult raceResult) throws IOException {
        // Iterate through all the rows in the selected sheet

        Iterator<Row> rowIterator = sheet.iterator();
        StringBuffer sb = new StringBuffer();
        Dictionary colCheck = new Hashtable();
        int idx = 0;
        String schema = "";
        while (rowIterator.hasNext()) {

            Row row = rowIterator.next();

            // Iterate through all the columns in the row and build ","
            // separated string
            Iterator<Cell> cellIterator = row.cellIterator();
            idx = 0;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                idx = cell.getColumnIndex();
                if (sb.length() != 0 && cell.getColumnIndex() != 0) {
                    sb.append(",");
                }

                // If you are using poi 4.0 or over, change it to
                // cell.getCellType
                switch (cell.getCellTypeEnum()) {
                    case STRING:
                        String tempVal = cell.getStringCellValue();
                        if(tempVal.contains(",")){

                            tempVal = "\""+tempVal+"\"";
                        }
                        if(cell.getColumnIndex() == 27 && row.getRowNum() > 0){
                            tempVal = "00:0"+tempVal;
                        }
                        sb.append(tempVal);
                        break;
                    case NUMERIC:
                        if(cell.getColumnIndex() == 0){
                            Date cellDate = cell.getDateCellValue();
                            Instant inst = cellDate.toInstant();
                            LocalDate ld = inst.atZone(ZoneId.systemDefault()).toLocalDate();
                            String value = ld.format(DateTimeFormatter.ofPattern("YYYY-MM-dd"));
                            sb.append(value);
                        }
                        else {
                            Double cellValue = cell.getNumericCellValue();
                            Double decimalValue = cellValue - cellValue.intValue();
                            if(decimalValue > 0)
                                sb.append(cellValue);
                            else
                                sb.append(cellValue.intValue());
                        }
                        break;
                    case BOOLEAN:
                        sb.append(cell.getBooleanCellValue());
                        break;
                    default:
                }
            }
            if(row.getRowNum() == 0){
                schema = sb.toString();

            }
            sb.append('\n');
        }

        String PATH = "../data_files/csv/";
        int year = raceResult.getRaceDate().getYear();
        String raceTrack = raceResult.getTrack().getName();
        String dist = raceResult.getDistanceSurfaceTrackRecord().getRaceDistance().getText().toLowerCase();
        dist = dist.replace(" ","_");
        dist = dist.replace("about_", "");
        File dirDist = new File(PATH+"/"+dist);
        String abPath = dirDist.getAbsolutePath();

        if(!dirDist.exists()){
            dirDist.mkdir();
        }



        File file = new File(PATH+"/"+dist+"/"+raceResult.getRaceDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"_"+raceTrack+"_race"+raceResult.getRaceNumber()+"_"+dist+".csv");


        colCheck.put(file, idx);
        System.out.println(file.getName()+": "+idx);
        String integrityPath = PATH+"/data_integrity";
        File integrity = new File(integrityPath);
        if(!integrity.exists()){
            integrity.mkdir();
        }
        File colFile = new File(integrityPath+"/data_integrity.csv");
        BufferedWriter bf = null;
        if(colFile.createNewFile()){
            bf = new BufferedWriter(new FileWriter(colFile));
            bf.write("filename,num_columns,distance,schema\n");
            bf.write(file.getName()+","+idx+","+dist+",\""+schema+"\"\n");
        }
        else{
            bf = new BufferedWriter(new FileWriter(colFile, true));
            bf.append(file.getName()+","+idx+","+dist+",\""+schema+"\"\n");
        }
        bf.close();

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(sb.toString());
        } finally {
            if (writer != null) writer.close();
        }
    }


    public int fillStandardCols(RaceResult raceResult, Starter starter, int startersColNum,
            XSSFRow starterRow, RaceDistance raceDistance) {
        startersColNum = fillSeedCols(raceResult, startersColNum, starterRow, raceDistance);

        // (horse) name
        asText(starterRow, startersColNum++, starter.getHorse().getName());

        // pp
        asNumber(starterRow, startersColNum++, starter.getPostPosition());

        // odds
        asNumber(starterRow, startersColNum++, starter.getOdds())
                .setCellStyle(twoDigitFormat);

        // favorite
        asBoolean(starterRow, startersColNum++, starter.isFavorite());

        // choice
        asNumber(starterRow, startersColNum++, starter.getChoice());

        // position
        asNumber(starterRow, startersColNum++, starter.getOfficialPosition());

        // dq
        asBoolean(starterRow, startersColNum++, starter.isDisqualified());

        // trainer
        asText(starterRow, startersColNum++, starter.getTrainer().getName());

        // jockey
        asText(starterRow, startersColNum++, starter.getJockey().getName());

        // claiming price (if any)
        asNumber(starterRow, startersColNum++,
                starter.getClaim() != null ? starter.getClaim().getPrice() : null);

        // was claimed (if applicable)
        asBoolean(starterRow, startersColNum++,
                starter.getClaim() != null ? starter.getClaim().isClaimed() : null);

        // new trainer (if any)
        asText(starterRow, startersColNum++,
                starter.getClaim() != null ? starter.getClaim().getNewTrainerName() : null);

        // new owner (if any)
        asText(starterRow, startersColNum++,
                starter.getClaim() != null ? starter.getClaim().getNewOwnerName() : null);

        // final time
        Fractional finishFractional = starter.getFinishFractional();
        asText(starterRow, startersColNum++,
                (finishFractional != null ? finishFractional.getTime() : null))
                .setCellStyle(threeDigitFormat);

        // seconds
        asNumber(starterRow, startersColNum++,
                (finishFractional != null && finishFractional.getMillis() != null ?
                        (double) finishFractional.getMillis() / 1000 : null))
                .setCellStyle(threeDigitFormat);
        return startersColNum;
    }

    public int fillSeedCols(RaceResult raceResult, int startersColNum, XSSFRow starterRow,
            RaceDistance raceDistance) {
        // date
        asDate(starterRow, startersColNum++, raceResult.getRaceDate())
                .setCellStyle(dateFormat);

        // track
        asText(starterRow, startersColNum++, raceResult.getTrack().getCode());

        // raceNumber
        asNumber(starterRow, startersColNum++, raceResult.getRaceNumber());

        RaceTypeNameBlackTypeBreed typeBreed =
                raceResult.getRaceConditions().getRaceTypeNameBlackTypeBreed();
        // breed
        asText(starterRow, startersColNum++, typeBreed.getBreed().getCode());

        // type
        asText(starterRow, startersColNum++, typeBreed.getType());

        // min claim (if any)
        asNumber(starterRow, startersColNum++,
                (raceResult.getRaceConditions().getClaimingPriceRange() != null ?
                        raceResult.getRaceConditions().getClaimingPriceRange().getMin() : null))
                .setCellStyle(commaNumberFormat);

        // max claim (if any)
        asNumber(starterRow, startersColNum++,
                (raceResult.getRaceConditions().getClaimingPriceRange() != null ?
                        raceResult.getRaceConditions().getClaimingPriceRange().getMax() : null))
                .setCellStyle(commaNumberFormat);

        // raceName
        asText(starterRow, startersColNum++, typeBreed.getName());

        // grade
        asNumber(starterRow, startersColNum++, typeBreed.getGrade());

        // purse
        asNumber(starterRow, startersColNum++, raceResult.getRaceConditions().getPurse().getValue())
                .setCellStyle(commaNumberFormat);

        // distance
        asNumber(starterRow, startersColNum++,
                raceDistance.getFurlongs()).setCellStyle(twoDigitFormat);
        asText(starterRow, startersColNum++, raceDistance.getCompact());

        // surface
        asText(starterRow, startersColNum++,
                raceResult.getDistanceSurfaceTrackRecord().getSurface());

        // course
        asText(starterRow, startersColNum++,
                raceResult.getDistanceSurfaceTrackRecord().getCourse());
        return startersColNum;
    }

    public XSSFRow createResultsHeaderRow(XSSFSheet sheet, int row, int col,
            RaceResult raceResult) {
        XSSFRow headerRow = sheet.createRow(row++);

        col = standardCols(col, headerRow);

        List<String> columns = Arrays.asList("weight", "run_up", "number_runners",
                "track_last_raced", "days_since");
        for (String column : columns) {
            CellUtil.createCell(headerRow, col++, column);
        }

        List<Starter> winners = raceResult.getWinners();

        if (winners.size() > 0) {
            Starter winner = winners.get(0);

            // individual fractionals
            if (winner.getFractionals() != null) {
                List<Fractional> fractionals = winner.getFractionals();
                for (int j = 0; j < fractionals.size(); j++) {
                    Fractional fractional = fractionals.get(j);
                    CellUtil.createCell(headerRow, col++, "ind_" + fractional.getText().toLowerCase().replace(" ", "_").replace("/", ""));
                }

                // splits
                List<Split> splits = winner.getSplits();
                for (int j = 0; j < splits.size(); j++) {
                    Split split = splits.get(j);
                    CellUtil.createCell(headerRow, col++, "ind_" + split.getText().toLowerCase().replace(" ", "_").replace("/", ""));
                }
            }

            // points of call
            List<PointOfCall> pointsOfCall = winner.getPointsOfCall();
            if (pointsOfCall != null) {
                // position
                for (int j = 0; j < pointsOfCall.size(); j++) {
                    PointOfCall pointOfCall = pointsOfCall.get(j);
                    CellUtil.createCell(headerRow, col++, "pos_" + pointOfCall.getText().toLowerCase().replace(" ", "_").replace("/", ""));
                }
                // lengths behind
                for (int j = 0; j < pointsOfCall.size(); j++) {
                    PointOfCall pointOfCall = pointsOfCall.get(j);
                    CellUtil.createCell(headerRow, col++, "len_behind_" + pointOfCall.getText().toLowerCase().replace(" ", "_").replace("/", ""));
                }
            }

            // leader fractionals
            /*
            List<Fractional> leaderFractionals = raceResult.getFractionals();
            for (int j = 0; j < leaderFractionals.size(); j++) {
                Fractional fractional = leaderFractionals.get(j);
                CellUtil.createCell(headerRow, col++, "Leader " + fractional.getText());
            }

            // leader splits
            List<Split> leaderSplits = raceResult.getSplits();
            for (int j = 0; j < leaderSplits.size(); j++) {
                Split split = leaderSplits.get(j);
                CellUtil.createCell(headerRow, col++, "Leader " + split.getText());
            }

             */
        }

        return headerRow;
    }

    void createBreedingSheet(List<RaceResult> raceResults, XSSFWorkbook workbook) {
        // Breeding
        XSSFSheet sheet = workbook.createSheet("Breeding");
        int row = 0;
        int col = 0;

        createBreedingHeaderRow(sheet, row++, col);

        for (int i = 0; i < raceResults.size(); i++) {
            RaceResult raceResult = raceResults.get(i);
            validate(raceResult);
            if(raceResult.getCancellation().isCancelled()){
                continue;
            }

            List<Starter> winners = raceResult.getWinners();
            if (winners != null) {
                for (Starter winner : winners) {
                    try {
                        int winnersColNum = 0;
                        XSSFRow winnerRow = sheet.createRow(row++);
                        RaceDistance raceDistance =
                                raceResult.getDistanceSurfaceTrackRecord().getRaceDistance();

                        winnersColNum = fillStandardCols(raceResult, winner, winnersColNum,
                                winnerRow, raceDistance);

                        // sex
                        asText(winnerRow, winnersColNum++, winner.getHorse().getSex());

                        // sire
                        asText(winnerRow, winnersColNum++, winner.getHorse().getSire().getName());

                        // dam
                        asText(winnerRow, winnersColNum++, winner.getHorse().getDam().getName());

                        // damSire
                        asText(winnerRow, winnersColNum++, winner.getHorse().getDamSire().getName());

                        // foalDate
                        asDate(winnerRow, winnersColNum++, winner.getHorse().getFoalingDate())
                                .setCellStyle(dateFormat);

                        // age
                        asNumber(winnerRow, winnersColNum++,
                                (winner.getHorse().getFoalingDate() != null ?
                                        raceResult.getRaceDate().getYear() -
                                                winner.getHorse().getFoalingDate().getYear() : null));
                    }
                    catch (NullPointerException np){
                        System.out.println(np.getMessage());
                        System.out.println(winner.toString());
                    }
                }
            }
        }
    }

    public XSSFRow createBreedingHeaderRow(XSSFSheet sheet, int row, int col) {
        XSSFRow headerRow = sheet.createRow(row++);

        col = standardCols(col, headerRow);

        List<String> columns = Arrays.asList("sex", "sire", "dam", "damSire", "foalDate", "age");
        for (String column : columns) {
            CellUtil.createCell(headerRow, col++, column);
        }

        return headerRow;
    }

    void createWageringSheet(List<RaceResult> raceResults, XSSFWorkbook workbook) {
        // Breeding
        XSSFSheet sheet = workbook.createSheet("Wagering");
        int row = 0;
        int col = 0;

        File wagerFile = new File("C:\\Users\\bod\\OneDrive\\Dev\\Personal\\exit_strategy\\data_files\\wager_sheet.csv");
        BufferedWriter wagerWriter = null;
        String wagerHeader = "date_track_race,win,win_unit,exacta,exact_unit,trifecta,tri_unit,superfecta,super_unit," +
                "daily_double,daily_double_unit,pick_3,pick_3_unit,pick_4,pick_4_unit,pick_5,pick_5_unit,pick_6,pick_6_unit";
        String wagerLine = "";

        createWageringHeaderRow(sheet, row++, col);

        for (int i = 0; i < raceResults.size(); i++) {
            RaceResult raceResult = raceResults.get(i);
            validate(raceResult);

            if(raceResult.getCancellation().isCancelled()){
                continue;
            }

            String wagerColOne = raceResult.getRaceDate()+"_"+raceResult.getTrack().getCode()+"_"+raceResult.getRaceNumber();
            wagerLine += wagerColOne+",";
            int raceColNum = 0;
            XSSFRow raceRow = sheet.createRow(row++);
            RaceDistance raceDistance =
                    raceResult.getDistanceSurfaceTrackRecord().getRaceDistance();

            raceColNum = fillSeedCols(raceResult, raceColNum, raceRow, raceDistance);

            WagerPayoffPools wagerPayoffPools = raceResult.getWagerPayoffPools();
            WagerPayoffPools.WinPlaceShowPayoffPool wpsPayoffPools =
                    wagerPayoffPools.getWinPlaceShowPayoffPools();
            List<WagerPayoffPools.ExoticPayoffPool> exoticPayoffPools =
                    wagerPayoffPools.getExoticPayoffPools();

            Starter winner = raceResult.getWinners().get(0);

            if(winner.getWinPlaceShowPayoff() != null &&
                    winner.getWinPlaceShowPayoff().getWin() != null){
                wagerLine += winner.getWinPlaceShowPayoff().getWin().getPayoff()+",2,";
            }
            // winPayoff
            asNumber(raceRow, raceColNum++,
                    (winner.getWinPlaceShowPayoff() != null &&
                            winner.getWinPlaceShowPayoff().getWin() != null ?
                            winner.getWinPlaceShowPayoff().getWin().getPayoff() : null))
                    .setCellStyle(twoDigitCommaFormat);

            // placePayoff
            asNumber(raceRow, raceColNum++,
                    (winner.getWinPlaceShowPayoff() != null &&
                            winner.getWinPlaceShowPayoff().getPlace() != null ?
                            winner.getWinPlaceShowPayoff().getPlace().getPayoff() : null))
                    .setCellStyle(twoDigitCommaFormat);

            // showPayoff
            asNumber(raceRow, raceColNum++,
                    (winner.getWinPlaceShowPayoff() != null &&
                            winner.getWinPlaceShowPayoff().getShow() != null ?
                            winner.getWinPlaceShowPayoff().getShow().getPayoff() : null))
                    .setCellStyle(twoDigitCommaFormat);

            // totalWpsPool
            asNumber(raceRow, raceColNum++, wpsPayoffPools.getTotalWinPlaceShowPool())
                    .setCellStyle(commaNumberFormat);

            List<String> horizontals = Arrays.asList("Exacta", "Trifecta", "Superfecta", "Daily Double","Pick 3", "Pick 4", "Pick 5, Pick 6");
            for (String horizontal : horizontals) {
                Optional<WagerPayoffPools.ExoticPayoffPool> wager = exoticPayoffPools.stream()
                        .filter(exoticPayoffPool -> exoticPayoffPool.getName()
                                .equalsIgnoreCase(horizontal)).findFirst();

                if(wager.isPresent() && wager.get().getPayoff() != null ){
                    wagerLine += wager.get().getPayoff() + "," + wager.get().getUnit() + ",";
                }
                else{
                    wagerLine += ",,";
                }
                // payoff
                asNumber(raceRow, raceColNum++,
                        (wager.isPresent() && wager.get().getPayoff() != null ?
                                ((double) wager.get().getPayoff() / wager.get().getUnit()) * 2 :
                                null))
                        .setCellStyle(twoDigitCommaFormat);

                // pool
                asNumber(raceRow, raceColNum++,
                        (wager.isPresent() ? wager.get().getPool() : null))
                        .setCellStyle(commaNumberFormat);
            }
            wagerLine += "\n";
        }

        try {
            if (wagerFile.createNewFile()) {
                wagerWriter = new BufferedWriter(new FileWriter((wagerFile)));
                wagerWriter.write(wagerHeader);
                wagerWriter.write(wagerLine);
            } else {
                wagerWriter = new BufferedWriter(new FileWriter(wagerFile, true));
                wagerWriter.write(wagerLine);
            }
            wagerWriter.close();
        }
        catch(IOException io){
            System.out.println(io.getMessage());
        }
    }

    public XSSFRow createWageringHeaderRow(XSSFSheet sheet, int row, int col) {
        XSSFRow headerRow = sheet.createRow(row++);

        col = seedCols(col, headerRow);

        List<String> columns = Arrays.asList("winPayoff", "placePayoff",
                "showPayoff", "totalWpsPool", "double$2Payoff", "doublePool", "exacta$2Payoff",
                "exactaPool", "trifecta$2Payoff", "trifectaPool", "superfecta$2Payoff",
                "superfectaPool", "pick3$2Payoff", "pick3Pool", "pick4$2Payoff", "pick4Pool",
                "pick5$2Payoff", "pick5Pool");
        for (String column : columns) {
            CellUtil.createCell(headerRow, col++, column);
        }

        return headerRow;
    }

    int seedCols(int col, XSSFRow headerRow) {
        List<String> columns = Arrays.asList("date", "track", "race_number", "breed", "type",
                "min_claim", "max_claim", "race_name", "grade", "purse", "distance_float", "distance", "surface",
                "course");
        for (String column : columns) {
            CellUtil.createCell(headerRow, col++, column);
        }
        return col;
    }

    int standardCols(int col, XSSFRow headerRow) {
        col = seedCols(col, headerRow);
        List<String> columns = Arrays.asList("name", "pp", "odds", "favorite", "choice",
                "position", "dq", "trainer", "jockey", "claim_price", "claimed", "new_trainer",
                "new_owner", "final_time", "seconds");
        for (String column : columns) {
            CellUtil.createCell(headerRow, col++, column);
        }
        return col;
    }

    private void validate(RaceResult raceResult) {
        if (raceResult.getCancellation().isCancelled()) {
            System.err.println("Race was cancelled - spreadsheet will not be created");
        }

        if (raceResult == null || raceResult.getStarters() == null) {
//            throw new RuntimeException("RaceResult instance or Starters List is null");
        }

    }
}
