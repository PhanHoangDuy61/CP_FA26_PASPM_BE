package vn.edu.fpt.paspm.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.transaction.importing.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit benchmark class; not automatically discovered as a correctness test. */
class F04ExcelParserBenchmark {
    @Test void measureXlsAndXlsxAtConfiguredRowBoundary()throws Exception {
        List<Map<String,Object>> samples=new ArrayList<>();
        var parser=new TransactionExcelParser(new TransactionImportProperties(5242880,10000));
        for(String format:List.of("xlsx","xls")){
            parser.parse(new ByteArrayInputStream(workbook(format,100)),ignored->{});
            for(int rows:new int[]{100,1000,10000}){
                byte[] bytes=workbook(format,rows);
                System.gc(); // Separate closed fixture-writer allocations from the parser observation.
                long base=usedHeap();AtomicLong peak=new AtomicLong(base),accepted=new AtomicLong();
                var monitor=Executors.newSingleThreadScheduledExecutor();
                monitor.scheduleAtFixedRate(()->peak.accumulateAndGet(usedHeap(),Math::max),0,5,TimeUnit.MILLISECONDS);
                long cpuStart=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();long start=System.nanoTime();
                try{parser.parse(new ByteArrayInputStream(bytes),row->accepted.incrementAndGet());}
                finally{monitor.shutdownNow();}
                long wall=System.nanoTime()-start;long cpu=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpuStart;
                assertEquals(rows,accepted.get());assertTrue(bytes.length<=5242880);
                var sample=new LinkedHashMap<String,Object>();sample.put("format",format);sample.put("rows",rows);sample.put("uploadBytes",bytes.length);
                sample.put("elapsedMs",wall/1_000_000.0);sample.put("threadCpuMs",cpu/1_000_000.0);sample.put("sampledHeapGrowthBytes",Math.max(0,peak.get()-base));samples.add(sample);
            }
        }
        var report=Map.of("scope","Synthetic parser-only, warmup100 per format, one caller; fixture generation outside timing; explicitGC requested before measurement, heap sampled5ms; no DB, HTTP/RBAC or productionSLA measurement", "java",System.getProperty("java.version"),"samples",samples);
        Path output=Path.of("target/f04-excel-parser-performance.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
    }
    static long usedHeap(){var rt=Runtime.getRuntime();return rt.totalMemory()-rt.freeMemory();}
    static byte[] workbook(String format,int count)throws Exception{
        try(Workbook workbook=format.equals("xlsx")?new XSSFWorkbook():new HSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=workbook.createSheet("Transactions");var header=sheet.createRow(0);int c=0;
            for(String h:TransactionCsvParser.HEADERS)header.createCell(c++).setCellValue(h);
            for(int n=0;n<count;n++){
                var row=sheet.createRow(n+1);String[] values={"0001-"+n,"2026-10-19T05:00:00Z","00123","1","10.00","6.00","COMPLETED"};
                for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
            }
            workbook.write(output);return output.toByteArray();
        }
    }
}
