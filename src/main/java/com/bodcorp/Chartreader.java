package com.bodcorp;

import com.microsoft.azure.functions.annotation.*;
import com.azure.storage.blob.*;
import com.azure.data.tables.*;
import com.azure.data.tables.models.TableEntity;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.bodcorp.chartparser.ChartParser;
import com.bodcorp.chartparser.charts.pdf.RaceResult;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with Azure Blob trigger.
 */
public class Chartreader {
    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     * @throws IOException
     */
    @FunctionName("chartreader")
    @StorageAccount("AzureWebJobsStorage")
    public void run(
        @BlobTrigger(name = "content", path = "pdf/{name}", dataType = "binary") byte[] content,
        @BindingName("name") String name,
        @BlobOutput(name = "target", path = "csvs/{name}", dataType = "binary") OutputBinding<String> csvFile,
        final ExecutionContext context
    ) throws IOException {
        
        Logger log = context.getLogger();
        log.info("Java Blob trigger function processed a blob. Name: " + name + "\n  Size: " + content.length + " Bytes");
        String connectionString = System.getenv("AzureWebJobsStorage");
        String tableName = System.getenv("TableName");

        TableServiceClient tsc = new TableServiceClientBuilder().connectionString(connectionString).buildClient();
        TableClient tc = tsc.createTableIfNotExists(tableName);
        BlobServiceClient bsc = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        
        BlobContainerClient csvBlob = bsc.getBlobContainerClient("csv");
        csvBlob.createIfNotExists();

        BlobContainerClient xlsBlob = bsc.getBlobContainerClient("xls");
        xlsBlob.createIfNotExists();
        
        List<RaceResult> raceResults;
        try{
             raceResults = ChartParser.create().parse(content, name);
        }
        catch (Exception x){
            log.severe(name + " | could not parse race results.  Existing");
            log.severe(x.getMessage());
            return;
        }

        ExcelService ex = new ExcelService();
        XSSFWorkbook wbook = ex.create(raceResults);
        log.info(name + " | Workbooks have been created");
        
        ArrayList<String> csvs = ex.printAsCsv(wbook);
        log.info(name + " | CSV files have been created");
        Dictionary<String,String> raceData = new Hashtable<String,String>();

        for(int i=0;i<raceResults.size();i++){
            String raceNumberStr = "";
            try{
                RaceResult result = raceResults.get(i);
                raceNumberStr = result.getRaceNumber().toString();
                if(result.getRaceNumber() < 10){
                    raceNumberStr = "0"+raceNumberStr;
                }
                String fileName = result.getRaceDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"_"+result.getTrack().getName()+"_race"+raceNumberStr+".csv";

                String dist = result.getDistanceSurfaceTrackRecord().getRaceDistance().getText().toLowerCase();
                dist = dist.replace(" ","_");
                dist = dist.replace("about_", "");

                raceData.put(fileName,dist);
            }
            catch (NullPointerException n){
                log.severe(name + " | Race "+raceNumberStr+" failed to process.");
                log.severe(n.getMessage());
            }
        }

        int idx =0;
        if(tc == null){
            tc = tsc.getTableClient(tableName);
        }
        for(Enumeration<String> i = raceData.keys(); i.hasMoreElements();){
            String fileName = (String)i.nextElement();
            String distance = (String)raceData.get(fileName);           
            String raceCsv = csvs.get(idx);     
            String schema = raceCsv.split("\n")[0];      
            int columnCount = schema.split(",").length;

            Map<String,Object> rowData = new HashMap<>();
            rowData.put("num_columns", columnCount);
            rowData.put("schema", schema);

            try{
                TableEntity row = new TableEntity(distance, fileName).setProperties(rowData);
                tc.upsertEntity(row);
            }
            catch (NullPointerException n){
                log.severe(name + " | " + n.getMessage());

            }
            log.info(name + " | DataIntegrity row added");

            BlobClient raceUpload = csvBlob.getBlobClient(distance+"/"+fileName);
            InputStream stream = new ByteArrayInputStream(raceCsv.getBytes(Charset.forName("UTF-8")));

            raceUpload.upload(stream, true);
            log.info(name + " | Csv blob uploaded");
            idx++;

        }

        //write excel to blob
        String[] nameInfo = name.split("/");
        String fname = nameInfo[1].substring(0, fname.length() - 3)+"xlsx";
        String track = nameInfo[0];
        File excel = new File(fname );

        if (excel != null) {
            try (FileOutputStream outputStream = new FileOutputStream(excel)) {
                wbook.write(outputStream);
                BlobClient xlsBc = xlsBlob.getBlobClient(fname);
                xlsBc.uploadFromFile(excel.getPath(),true);

            } catch (IOException exc) {
                log.severe(name + " | Excel file not uploaded");
                log.severe(name + " | "+exc.getMessage());
            }
        }

        log.info(name + " | Process Successful");
    }
}
