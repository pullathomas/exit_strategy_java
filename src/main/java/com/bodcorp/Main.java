package com.bodcorp;

import com.robinhowlett.chartparser.ChartParser;
import com.robinhowlett.chartparser.charts.pdf.RaceResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) {
	// write your code here
        String basePath = "C:\\Users\\bod\\OneDrive\\Dev\\Personal\\exit_strategy\\data_files\\";
        String[] directoriesToParse = {
                //basePath+"pdf\\CD",
                //basePath+"pdf\\FP",
                //basePath+"pdf\\HAW",
                basePath+"pdf\\2021"
                //basePath+"pdf\\2020"
                //basePath+"pdf\\2019",
                //basePath+"pdf\\2018"
        };
        for(String path : directoriesToParse) {
            File[] files = new File(path).listFiles();
            for (File f : files) {
                //File test = new File("C:\\Users\\bod\\OneDrive\\Dev\\Personal\\exit_strategy\\data_files\\pdf\\2020\\CD_2020-06-21.pdf");
                //List<RaceResult> raceResults = ChartParser.create().parse(test);
                List<RaceResult> raceResults = ChartParser.create().parse(f);
                ExcelService ex = new ExcelService();
                System.out.println(f.getName());
                int debug = 0;
                if (f.getName().equalsIgnoreCase("08-21-10.pdf")) {
                    debug = 1;
                }
                //else continue;
                XSSFWorkbook wbook = ex.create(raceResults);
                String fname = f.getName();
                fname = fname.substring(0, fname.length() - 3);
                File file = new File(basePath+"excel\\" + fname + "xlsx");

                if (file != null) {
                    try (FileOutputStream outputStream = new FileOutputStream(file)) {
                        wbook.write(outputStream);
                    } catch (IOException exc) {
                        System.out.println(exc.getMessage());
                    }
                }
            }
        }


    }
}
