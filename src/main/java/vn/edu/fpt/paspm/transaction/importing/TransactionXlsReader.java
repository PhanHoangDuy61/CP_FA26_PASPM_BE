package vn.edu.fpt.paspm.transaction.importing;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.*;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import vn.edu.fpt.paspm.transaction.importing.ExcelRows.Cell;
import vn.edu.fpt.paspm.transaction.importing.ExcelRows.Kind;

/** Low-memory BIFF event reader. No HSSFWorkbook, cached-formula acceptance or external evaluation. */
final class TransactionXlsReader implements HSSFListener {
    private final ExcelRows rows;
    private final ExcelImportProperties limits;
    private final Map<Integer, Cell> cells = new TreeMap<>();
    private final Set<Integer> seenRows = new java.util.HashSet<>();
    private SSTRecord strings;
    private boolean inSheet;
    private int activeRow = -1;
    private int styles;
    TransactionXlsReader(ExcelRows rows, ExcelImportProperties limits) { this.rows = rows; this.limits = limits; }

    void read(Path file) throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem(file.toFile(), true)) {
            Set<String> supported = Set.of("Workbook", "Book", "\u0005SummaryInformation", "\u0005DocumentSummaryInformation");
            int entries = 0;
            for (Entry entry : fs.getRoot()) {
                if (++entries > limits.maxEntries() || !entry.isDocumentEntry() || !supported.contains(entry.getName()))
                    throw ExcelRows.invalid("Excel contains encrypted, embedded or unsupported OLE content");
            }
            if (!fs.getRoot().hasEntry("Workbook") && !fs.getRoot().hasEntry("Book"))
                throw ExcelRows.invalid("Excel workbook stream is missing");
            preflight(fs);
            HSSFRequest request = new HSSFRequest();
            request.addListenerForAllRecords(this);
            new HSSFEventFactory().processWorkbookEvents(request, fs);
            flush();
        }
    }

    /** Inspect BIFF lengths/counts before POI constructs an SST or complex active-content record. */
    private void preflight(POIFSFileSystem fs) throws java.io.IOException {
        String workbook = fs.getRoot().hasEntry("Workbook") ? "Workbook" : "Book";
        Set<Integer> unsupported = Set.of((int) FormulaRecord.sid, (int) FilePassRecord.sid,
                (int) HyperlinkRecord.sid, (int) DrawingRecord.sid, (int) DrawingGroupRecord.sid,
                (int) ObjRecord.sid, (int) NameRecord.sid, (int) ExternalNameRecord.sid,
                (int) CFRuleRecord.sid, (int) CFRule12Record.sid, (int) CFHeaderRecord.sid,
                (int) CFHeader12Record.sid, (int) DVRecord.sid, (int) DVALRecord.sid);
        int styleCount = 0;
        try (var stream = fs.createDocumentInputStream(workbook)) {
            while (true) {
                byte[] header = stream.readNBytes(4);
                if (header.length == 0) break;
                if (header.length != 4) throw ExcelRows.invalid("Excel BIFF record header is truncated");
                int sid = (header[0] & 255) | ((header[1] & 255) << 8);
                int length = (header[2] & 255) | ((header[3] & 255) << 8);
                if (length > 8224) throw ExcelRows.invalid("Excel BIFF record exceeds the supported size");
                if (unsupported.contains(sid)) throw ExcelRows.invalid("Excel formulas and active BIFF content are unsupported");
                if (sid == (ExtendedFormatRecord.sid & 65535) && ++styleCount > limits.maxStyles())
                    throw ExcelRows.invalid("Excel styles exceed the configured limit");
                if (sid == SSTRecord.sid) {
                    if (length < 8) throw ExcelRows.invalid("Excel shared-string header is truncated");
                    byte[] counts = stream.readNBytes(8);
                    if (counts.length != 8) throw ExcelRows.invalid("Excel shared-string header is truncated");
                    long total = littleEndianInt(counts, 0);
                    long unique = littleEndianInt(counts, 4);
                    if (unique > limits.maxSharedStrings() || unique > total)
                        throw ExcelRows.invalid("Excel shared strings exceed the configured limit");
                    length -= 8;
                }
                stream.skipNBytes(length);
            }
        }
    }
    private static long littleEndianInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong((bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8)
                | ((bytes[offset + 2] & 255) << 16) | ((bytes[offset + 3] & 255) << 24));
    }

    @Override public void processRecord(Record record) {
        if (record instanceof BOFRecord bof) {
            if (bof.getType() != BOFRecord.TYPE_WORKBOOK && bof.getType() != BOFRecord.TYPE_WORKSHEET)
                throw ExcelRows.invalid("Excel non-worksheet substreams are unsupported");
            if (bof.getType() == BOFRecord.TYPE_WORKSHEET) {
                flush(); rows.beginSheet(); seenRows.clear(); inSheet = true; activeRow = -1;
            }
        } else if (record instanceof EOFRecord) { flush(); inSheet = false; activeRow = -1; }
        else if (record instanceof FormulaRecord || record instanceof FilePassRecord || record instanceof HyperlinkRecord
                || record instanceof DrawingRecord || record instanceof DrawingGroupRecord || record instanceof ObjRecord
                || record instanceof NameRecord || record instanceof ExternalNameRecord
                || record instanceof CFRuleBase || record instanceof CFHeaderBase || record instanceof DVRecord || record instanceof DVALRecord) {
            throw ExcelRows.invalid("Excel formulas, external links and active workbook content are unsupported");
        } else if (record instanceof SupBookRecord book && !book.isInternalReferences()) {
            throw ExcelRows.invalid("Excel external workbook references are unsupported");
        } else if (record instanceof SSTRecord sst) {
            if (sst.getNumUniqueStrings() > limits.maxSharedStrings()) throw ExcelRows.invalid("Excel shared strings exceed the configured limit");
            for (int index = 0; index < sst.getNumUniqueStrings(); index++)
                if (sst.getString(index).getString().length() > limits.maxCellCharacters())
                    throw ExcelRows.invalid("Excel shared string exceeds the configured text limit");
            strings = sst;
        } else if (record instanceof ExtendedFormatRecord) {
            if (++styles > limits.maxStyles()) throw ExcelRows.invalid("Excel styles exceed the configured limit");
        } else if (inSheet && record instanceof RowRecord row) scanRow(row.getRowNumber());
        else if (inSheet && record instanceof LabelSSTRecord label) {
            if (strings == null || label.getSSTIndex() < 0 || label.getSSTIndex() >= strings.getNumUniqueStrings())
                throw ExcelRows.invalid("Excel shared-string reference is invalid");
            put(label.getRow(), label.getColumn(), new Cell(Kind.TEXT, strings.getString(label.getSSTIndex()).getString()));
        } else if (inSheet && record instanceof LabelRecord label)
            put(label.getRow(), label.getColumn(), new Cell(Kind.TEXT, label.getValue()));
        else if (inSheet && record instanceof NumberRecord number)
            put(number.getRow(), number.getColumn(), numeric(number.getValue()));
        else if (inSheet && record instanceof RKRecord number)
            put(number.getRow(), number.getColumn(), numeric(number.getRKNumber()));
        else if (inSheet && record instanceof MulRKRecord numbers) {
            for (int index = 0; index < numbers.getNumColumns(); index++)
                put(numbers.getRow(), numbers.getFirstColumn() + index, numeric(numbers.getRKNumberAt(index)));
        } else if (inSheet && record instanceof BlankRecord blank)
            put(blank.getRow(), blank.getColumn(), new Cell(Kind.BLANK, ""));
        else if (inSheet && record instanceof BoolErrRecord)
            throw ExcelRows.invalid("Excel boolean and error cells are unsupported");
    }
    private Cell numeric(double value) { return new Cell(Kind.NUMBER, BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()); }
    private void scanRow(int row) { if (seenRows.add(row)) rows.scannedRow(); }
    private void put(int row, int column, Cell cell) {
        scanRow(row);
        if (row != activeRow) {
            if (row < activeRow) throw ExcelRows.invalid("Excel worksheet rows are out of order");
            flush(); activeRow = row;
        }
        if (cells.putIfAbsent(column, cell) != null) throw ExcelRows.invalid("Excel contains duplicate cell positions");
    }
    private void flush() {
        if (activeRow >= 0 && !cells.isEmpty()) rows.accept(activeRow, cells);
        cells.clear();
    }
}
