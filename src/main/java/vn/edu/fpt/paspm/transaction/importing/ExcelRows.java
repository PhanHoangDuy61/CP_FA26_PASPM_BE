package vn.edu.fpt.paspm.transaction.importing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;

/** The single shared typed-cell contract for both Excel readers; no order validation occurs here. */
final class ExcelRows {
    enum Kind { TEXT, NUMBER, BLANK, FORMULA, BOOLEAN, ERROR }
    record Cell(Kind kind, String value) {}
    private final int maxRows;
    private final ExcelImportProperties limits;
    private final List<ParsedCsvRow> data = new ArrayList<>();
    private int nonemptySheets;
    private int scannedRows;
    private boolean sheetNonempty;
    private boolean header;

    ExcelRows(int maxRows, ExcelImportProperties limits) {
        this.maxRows = maxRows;
        this.limits = limits;
    }
    void beginSheet() { sheetNonempty = false; header = false; }
    void scannedRow() {
        if (++scannedRows > limits.maxScannedRows()) throw invalid("Excel exceeds the configured scanned-row limit");
    }
    void accept(int zeroBasedRow, Map<Integer, Cell> cells) {
        boolean nonempty = false;
        for (var entry : cells.entrySet()) {
            Cell cell = entry.getValue();
            if (cell.value().length() > limits.maxCellCharacters()) throw invalid("Excel cell exceeds the configured text limit");
            if (cell.kind() == Kind.FORMULA || cell.kind() == Kind.BOOLEAN || cell.kind() == Kind.ERROR)
                throw invalid("Excel formulas, boolean and error cells are unsupported");
            if (cell.kind() != Kind.BLANK && !cell.value().isBlank()) {
                nonempty = true;
                if (entry.getKey() >= TransactionCsvParser.HEADERS.size()) throw invalid("Excel contains unexpected columns");
            }
        }
        if (!nonempty) return;
        if (!sheetNonempty) {
            sheetNonempty = true;
            if (++nonemptySheets > 1) throw invalid("Excel must contain exactly one nonempty worksheet");
        }
        if (!header) {
            if (zeroBasedRow != 0) throw invalid("Excel header must be on the first worksheet row");
            for (int column = 0; column < TransactionCsvParser.HEADERS.size(); column++) {
                Cell cell = cells.get(column);
                if (cell == null || cell.kind() != Kind.TEXT || !TransactionCsvParser.HEADERS.get(column).equals(cell.value()))
                    throw invalid("Excel header must match the approved F04 template");
            }
            header = true;
            return;
        }
        if (data.size() >= maxRows) throw invalid("Excel exceeds the configured row limit");
        Map<String, String> values = new LinkedHashMap<>();
        for (int column = 0; column < TransactionCsvParser.HEADERS.size(); column++) {
            Cell cell = cells.getOrDefault(column, new Cell(Kind.BLANK, ""));
            if ((column == 0 || column == 1 || column == 2 || column == 6)
                    && cell.kind() != Kind.TEXT && cell.kind() != Kind.BLANK)
                throw invalid("Excel order IDs, timestamps, SKUs and statuses must be text cells");
            String value = cell.value();
            if (column == 3 && cell.kind() == Kind.NUMBER) {
                try { value = Integer.toString(new BigDecimal(value).intValueExact()); }
                catch (ArithmeticException ignored) { /* Fractional/out-of-range quantity stays compact for shared business validation. */ }
            }
            values.put(TransactionCsvParser.HEADERS.get(column), value);
        }
        data.add(new ParsedCsvRow(Math.addExact(zeroBasedRow, 1), Map.copyOf(values)));
    }
    List<ParsedCsvRow> finish() {
        if (nonemptySheets != 1 || data.isEmpty()) throw invalid("Excel must contain a header and at least one data row");
        return List.copyOf(data);
    }
    static ApiException invalid(String message) { return new ApiException(400, message); }
}
