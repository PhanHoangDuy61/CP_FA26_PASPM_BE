package vn.edu.fpt.paspm.transaction.importing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionImportProperties;

class TransactionExcelParserTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void readsXlsAndXlsxWithRawIdentityNumericMoneyAndActualRowNumber(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Sheet sheet = template(book);
            Row row = data(sheet, 2);
            row.getCell(0).setCellValue(" 0001 ");
            row.getCell(2).setCellValue("00003");
            row.getCell(3).setCellValue(2);
            row.getCell(4).setCellValue(10.25);
            sheet.createRow(3);
            List<TransactionCsvParser.ParsedCsvRow> rows = parse(bytes(book), 10);
            assertEquals(1, rows.size());
            assertEquals(3, rows.get(0).rowNumber());
            assertEquals(" 0001 ", rows.get(0).values().get("source_order_id"));
            assertEquals("00003", rows.get(0).values().get("sku"));
            assertEquals("2", rows.get(0).values().get("quantity"));
            assertEquals("10.25", rows.get(0).values().get("unit_price"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void keepsBusinessInvalidTextForTheSharedOrderValidator(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Row row = data(template(book), 1);
            row.getCell(0).setCellValue("x".repeat(151));
            row.getCell(1).setCellValue("not-a-date");
            row.getCell(3).setCellValue("-3");
            row.getCell(4).setCellValue("2.123");
            var values = parse(bytes(book), 10).get(0).values();
            assertEquals("x".repeat(151), values.get("source_order_id"));
            assertEquals("not-a-date", values.get("occurred_at"));
            assertEquals("-3", values.get("quantity"));
            assertEquals("2.123", values.get("unit_price"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void allowsEmptyExtraSheetsButRejectsASecondNonemptySheet(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            data(template(book), 1);
            book.createSheet("blank").createRow(0).createCell(0).setBlank();
            assertEquals(1, parse(bytes(book), 10).size());
            book.getSheet("blank").getRow(0).getCell(0).setCellValue("unexpected");
            reject(bytes(book), 10);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsEmptyWorkbookAndHeaderOnlyWorkbook(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            reject(bytes(book), 10);
            template(book);
            reject(bytes(book), 10);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void requiresExactHeaderOrderAndRejectsDuplicateMissingOrExtraHeaders(boolean xlsx) throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            try (Workbook book = workbook(xlsx)) {
                Sheet sheet = template(book); data(sheet, 1);
                Row header = sheet.getRow(0);
                switch (kind) {
                    case 0 -> header.getCell(1).setCellValue("source_order_id");
                    case 1 -> header.getCell(0).setCellValue("sku");
                    case 2 -> header.removeCell(header.getCell(6));
                    default -> header.createCell(7).setCellValue("store_id");
                }
                reject(bytes(book), 10);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsFormulaEvenWhenCachedResultCouldBeValid(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Row row = data(template(book), 1);
            row.getCell(3).setCellFormula("1+1");
            book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(row.getCell(3));
            reject(bytes(book), 10);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsNumericIdentifiersAndNativeExcelDates(boolean xlsx) throws Exception {
        for (int column : new int[]{0, 1, 2}) {
            try (Workbook book = workbook(xlsx)) {
                Row row = data(template(book), 1);
                if (column == 1) row.getCell(column).setCellValue(LocalDateTime.of(2026, 10, 19, 12, 0));
                else row.getCell(column).setCellValue(123);
                reject(bytes(book), 10);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsBooleanErrorAndUnexpectedNonblankColumns(boolean xlsx) throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            try (Workbook book = workbook(xlsx)) {
                Row row = data(template(book), 1);
                if (kind == 0) row.getCell(6).setCellValue(true);
                else if (kind == 1) row.getCell(3).setCellErrorValue(FormulaError.DIV0.getCode());
                else row.createCell(7).setCellValue("forged-store");
                reject(bytes(book), 10);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void enforcesNonblankRowCountAndUploadByteLimit(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Sheet sheet = template(book); data(sheet, 1); data(sheet, 4);
            byte[] file = bytes(book);
            assertEquals(2, parse(file, 2).size());
            reject(file, 1);
            var parser = new TransactionExcelParser(new TransactionImportProperties(file.length - 1, 10));
            ApiException error = assertThrows(ApiException.class, () -> parser.parse(new ByteArrayInputStream(file), row -> {}));
            assertEquals(413, error.status());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsExternalHyperlinks(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Row row = data(template(book), 1);
            Hyperlink link = book.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL);
            link.setAddress("https://example.invalid/private");
            row.getCell(0).setHyperlink(link);
            reject(bytes(book), 10);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void numericFormattingNeverRoundsMoneyOrFractionalQuantity(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Row row = data(template(book), 1);
            row.getCell(3).setCellValue(2.5);
            row.getCell(4).setCellValue(10.257);
            CellStyle style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("0.00"));
            row.getCell(4).setCellStyle(style);
            var values = parse(bytes(book), 10).get(0).values();
            assertEquals("2.5", values.get("quantity"));
            assertEquals("10.257", values.get("unit_price"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void blankFieldsRemainSharedBusinessValidationInput(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Row row = data(template(book), 1);
            row.getCell(0).setBlank(); row.getCell(1).setBlank(); row.getCell(2).setBlank();
            var values = parse(bytes(book), 10).get(0).values();
            assertEquals("", values.get("source_order_id"));
            assertEquals("", values.get("occurred_at"));
            assertEquals("", values.get("sku"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void headerCannotBeShiftedBelowFirstRow(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Sheet sheet = template(book); data(sheet, 1); sheet.shiftRows(0, 1, 1);
            reject(bytes(book), 10);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void enforcesSharedStringsStyleTextAndScannedRowTechnicalCaps(boolean xlsx) throws Exception {
        try (Workbook book = workbook(xlsx)) {
            Sheet sheet = template(book); data(sheet, 1); sheet.createRow(3);
            byte[] file = bytes(book);
            List<ExcelImportProperties> caps = List.of(
                new ExcelImportProperties(33554432,8388608,128,32767,1,1024,100000),
                new ExcelImportProperties(33554432,8388608,128,32767,100000,1,100000),
                new ExcelImportProperties(33554432,8388608,128,10,100000,1024,100000),
                new ExcelImportProperties(33554432,8388608,128,32767,100000,1024,2));
            for (ExcelImportProperties cap : caps) {
                assertThrows(ApiException.class, () -> new TransactionExcelParser(
                    new TransactionImportProperties(5242880,10),cap).parse(new ByteArrayInputStream(file), row -> {}));
            }
        }
    }

    @Test void rejectsWrongSheetRootAndNamespace() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book), 1);
            byte[] file = bytes(book);
            reject(replaceZipEntry(file,"xl/worksheets/sheet1.xml", xml -> xml.replace("worksheet", "wrongRoot")),10);
            reject(replaceZipEntry(file,"xl/worksheets/sheet1.xml", xml -> xml.replace(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "urn:unexpected")),10);
        }
    }

    @Test void decodesExcelEscapedCharactersOnceAndRetainsLiteralEscapeText() throws Exception {
        try (Workbook book = workbook(true)) {
            Row row = data(template(book), 1);
            row.getCell(0).setCellValue("A_x0041_B");
            row.getCell(2).setCellValue("SKU\r01");
            byte[] file = replaceZipEntry(bytes(book), "xl/sharedStrings.xml", xml ->
                xml.replace("A_x0041_B", "A_x005F_x0041_B"));
            var values = parse(file,10).get(0).values();
            assertEquals("A_x0041_B",values.get("source_order_id"));
            assertEquals("SKU\r01",values.get("sku"));
        }
    }

    @Test void rejectsCaseFoldedDuplicateZipPathsAndUnsafeExpansionRatio() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book),1); byte[] file=bytes(book);
            ApiException duplicate = assertThrows(ApiException.class, () -> parse(
                addZipEntry(file,"XL/WORKBOOK.XML","<workbook/>".getBytes(StandardCharsets.UTF_8)),10));
            assertTrue(duplicate.getMessage().contains("duplicate"));
            ApiException ratio = assertThrows(ApiException.class, () -> parse(
                addZipEntry(file,"docProps/custom.xml","x".repeat(1000000).getBytes(StandardCharsets.UTF_8)),10));
            assertTrue(ratio.getMessage().contains("compression ratio"));
        }
    }

    @Test void rejectsExternalXmlEntitiesWithoutResolvingThem() throws Exception {
        try (Workbook book=workbook(true)) {
            data(template(book),1);
            byte[] file=replaceZipEntry(bytes(book),"xl/worksheets/sheet1.xml", xml ->
                xml.replaceFirst("<worksheet", "<!DOCTYPE worksheet [<!ENTITY ext SYSTEM \"https://example.invalid/never-fetch\">]><worksheet"));
            reject(file,10);
        }
    }

    @Test void doesNotConvertDownstreamInfrastructureFailureIntoInputError() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book),1);
            IllegalStateException failure=new IllegalStateException("injected consumer failure");
            assertSame(failure, assertThrows(IllegalStateException.class, () -> new TransactionExcelParser(
                new TransactionImportProperties(5242880,10)).parse(new ByteArrayInputStream(bytes(book)), row -> {throw failure;})));
        }
    }

    @Test void rejectsInvalidTechnicalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ExcelImportProperties(0,1,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class, () -> new ExcelImportProperties(1,2,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class, () -> new ExcelImportProperties(1,1,1,32768,1,1,1));
    }

    @Test void numericExponentDoesNotExpandBeforeSharedBusinessValidation() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book),1);
            byte[] file=replaceZipEntry(bytes(book),"xl/worksheets/sheet1.xml", xml ->
                xml.replaceFirst("<c r=\"D2\"[^>]*>.*?</c>", "<c r=\"D2\" t=\"n\"><v>1E10000</v></c>"));
            assertEquals("1E10000",parse(file,10).get(0).values().get("quantity"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsConditionalFormattingAndDataValidationFormulas(boolean xlsx) throws Exception {
        for (int kind=0;kind<2;kind++) {
            try (Workbook book=workbook(xlsx)) {
                Sheet sheet=template(book);data(sheet,1);
                if(kind==0) {
                    SheetConditionalFormatting formatting=sheet.getSheetConditionalFormatting();
                    ConditionalFormattingRule rule=formatting.createConditionalFormattingRule("A2=1");
                    formatting.addConditionalFormatting(new org.apache.poi.ss.util.CellRangeAddress[]{
                        new org.apache.poi.ss.util.CellRangeAddress(1,1,0,0)},rule);
                } else {
                    DataValidationHelper helper=sheet.getDataValidationHelper();
                    DataValidationConstraint constraint=helper.createCustomConstraint("A2=1");
                    sheet.addValidationData(helper.createValidation(constraint,
                        new org.apache.poi.ss.util.CellRangeAddressList(1,1,0,0)));
                }
                reject(bytes(book),10);
            }
        }
    }

    @Test void xlsCellRowsCannotBypassScannedRowLimitWithoutRowRecords() {
        var caps=new ExcelImportProperties(33554432,8388608,128,32767,100000,1024,1);
        var rows=new ExcelRows(10,caps);
        var reader=new TransactionXlsReader(rows,caps);
        var strings=new org.apache.poi.hssf.record.SSTRecord();
        reader.processRecord(strings);
        reader.processRecord(org.apache.poi.hssf.record.BOFRecord.createSheetBOF());
        assertThrows(ApiException.class, () -> {
            for(int column=0;column<7;column++) sendXlsText(reader,strings,0,column,TransactionCsvParser.HEADERS.get(column));
            String[] values={"id","2026-10-19T09:00:00Z","SKU-1","1","10","5","COMPLETED"};
            for(int column=0;column<7;column++) sendXlsText(reader,strings,1,column,values[column]);
            reader.processRecord(org.apache.poi.hssf.record.EOFRecord.instance);
        });
    }

    @Test void xlsMulBlankOnlyRowsCannotBypassScannedRowLimitWithoutRowRecords() {
        var caps=new ExcelImportProperties(33554432,8388608,128,32767,100000,1024,3);
        var rows=new ExcelRows(10,caps);
        var reader=new TransactionXlsReader(rows,caps);
        var strings=new org.apache.poi.hssf.record.SSTRecord();
        reader.processRecord(strings);
        reader.processRecord(org.apache.poi.hssf.record.BOFRecord.createSheetBOF());
        for(int column=0;column<7;column++) sendXlsText(reader,strings,0,column,TransactionCsvParser.HEADERS.get(column));
        String[] values={"id","2026-10-19T09:00:00Z","SKU-1","1","10","5","COMPLETED"};
        for(int column=0;column<7;column++) sendXlsText(reader,strings,1,column,values[column]);
        reader.processRecord(new org.apache.poi.hssf.record.MulBlankRecord(2,0,new short[]{0,0,0,0,0,0,0}));
        ApiException error=assertThrows(ApiException.class, () -> reader.processRecord(
            new org.apache.poi.hssf.record.MulBlankRecord(3,0,new short[]{0,0,0,0,0,0,0})));
        assertTrue(error.getMessage().contains("scanned-row limit"));
    }

    @ParameterizedTest @ValueSource(strings={"array","shared"})
    void rejectsOrphanArrayAndSharedFormulaRecords(String type) {
        var caps=ExcelImportProperties.defaults();
        var reader=new TransactionXlsReader(new ExcelRows(10,caps),caps);
        org.apache.poi.hssf.record.Record record=type.equals("shared")
            ? new org.apache.poi.hssf.record.SharedFormulaRecord()
            : new org.apache.poi.hssf.record.ArrayRecord(org.apache.poi.ss.formula.Formula.create(
                new org.apache.poi.ss.formula.ptg.Ptg[]{new org.apache.poi.ss.formula.ptg.IntPtg(1)}),
                new org.apache.poi.hssf.util.CellRangeAddress8Bit(1,1,0,0));
        assertThrows(ApiException.class, () -> reader.processRecord(record));
    }

    static void sendXlsText(TransactionXlsReader reader,org.apache.poi.hssf.record.SSTRecord strings,int row,int column,String value) {
        var cell=new org.apache.poi.hssf.record.LabelSSTRecord();
        cell.setRow(row);cell.setColumn((short)column);
        cell.setSSTIndex(strings.addString(new org.apache.poi.hssf.record.common.UnicodeString(value)));
        reader.processRecord(cell);
    }

    @Test void rejectsMalformedInputAndUnsupportedZipPayload() {
        reject(new byte[0], 10);
        reject("not-excel".getBytes(StandardCharsets.UTF_8), 10);
        reject(new byte[]{'P','K',3,4,1}, 10);
    }

    @Test void rejectsExternalRelationshipsMacrosEmbeddedContentAndDuplicateZipNames() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book), 1);
            byte[] original = bytes(book);
            reject(addZipEntry(original, "xl/vbaProject.bin", new byte[]{1,2}), 10);
            reject(addZipEntry(original, "xl/embeddings/oleObject1.bin", new byte[]{1,2}), 10);
            reject(addZipEntry(original, "xl/externalLinks/externalLink1.xml", "<externalLink/>".getBytes(StandardCharsets.UTF_8)), 10);
        }
    }

    @Test void rejectsZipDecompressionAbuseAndBoundsCellText() throws Exception {
        try (Workbook book = workbook(true)) {
            data(template(book), 1);
            byte[] file = bytes(book);
            reject(addZipEntry(file, "docProps/custom.xml", "x".repeat(10_000_000).getBytes(StandardCharsets.UTF_8)), 10);
        }
    }

    static Workbook workbook(boolean xlsx) { return xlsx ? new XSSFWorkbook() : new HSSFWorkbook(); }
    static Sheet template(Workbook book) {
        Sheet sheet = book.createSheet("Transactions");
        Row header = sheet.createRow(0);
        for (int i = 0; i < TransactionCsvParser.HEADERS.size(); i++) header.createCell(i).setCellValue(TransactionCsvParser.HEADERS.get(i));
        return sheet;
    }
    static Row data(Sheet sheet, int index) {
        String[] values = {"order-" + index,"2026-10-19T09:00:00+07:00","SKU-1","1","10.25","5.00","COMPLETED"};
        Row row = sheet.createRow(index);
        for (int i=0;i<values.length;i++) row.createCell(i).setCellValue(values[i]);
        return row;
    }
    static byte[] bytes(Workbook book) throws IOException {
        var out = new ByteArrayOutputStream(); book.write(out); return out.toByteArray();
    }
    static List<TransactionCsvParser.ParsedCsvRow> parse(byte[] input, int maxRows) {
        var rows = new ArrayList<TransactionCsvParser.ParsedCsvRow>();
        new TransactionExcelParser(new TransactionImportProperties(5_242_880, maxRows)).parse(new ByteArrayInputStream(input), rows::add);
        return rows;
    }
    static void reject(byte[] input, int maxRows) {
        ApiException error = assertThrows(ApiException.class, () -> parse(input, maxRows));
        assertTrue(error.status() == 400 || error.status() == 413);
    }
    static byte[] replaceZipEntry(byte[] input, String name, java.util.function.UnaryOperator<String> change) throws IOException {
        var out=new ByteArrayOutputStream();
        try(var source=new ZipInputStream(new ByteArrayInputStream(input)); var zip=new ZipOutputStream(out)) {
            ZipEntry entry;
            while((entry=source.getNextEntry())!=null) {
                byte[] value=source.readAllBytes();
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(entry.getName().equals(name)?change.apply(new String(value,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8):value);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    static byte[] addZipEntry(byte[] input, String name, byte[] value) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var source = new ZipInputStream(new ByteArrayInputStream(input)); var zip = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry=source.getNextEntry())!=null) {
                zip.putNextEntry(new ZipEntry(entry.getName())); source.transferTo(zip); zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name)); zip.write(value); zip.closeEntry();
        }
        return out.toByteArray();
    }
}
