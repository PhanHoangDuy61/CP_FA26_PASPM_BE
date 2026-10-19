package vn.edu.fpt.paspm.transaction.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;

class TransactionCsvParserTest {
    private static final String HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status";

    @Test
    void parsesBomQuotedCommasEscapedQuotesAndEmbeddedNewline() {
        String csv = "\uFEFF" + HEADER + "\r\n" +
                "\"order \"\"A\"\"\",2026-10-19T12:00:00+07:00,\"SKU,\r\n01\",2,100.00,60.00,COMPLETED\r\n";
        List<TransactionCsvParser.ParsedCsvRow> rows = parse(csv, 10);

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("order \"A\"", rows.get(0).values().get("source_order_id"));
        assertEquals("SKU,\r\n01", rows.get(0).values().get("sku"));
    }

    @Test
    void skipsBlankLinesWithoutCountingThemAgainstDataRowLimit() {
        List<TransactionCsvParser.ParsedCsvRow> rows = parse(HEADER + "\n\norder-1,2026-10-19T12:00:00Z,SKU-1,1,10,5,COMPLETED\n\n", 1);

        assertEquals(1, rows.size());
        assertEquals("order-1", rows.get(0).values().get("source_order_id"));
    }

    @Test
    void rejectsHeaderThatDoesNotMatchTheContract() {
        assertThrows(ApiException.class, () -> parse("source_order_id,sku\norder-1,SKU-1", 10));
    }

    @Test
    void rejectsMalformedQuotedCsv() {
        assertThrows(ApiException.class, () -> parse(HEADER + "\norder-1,2026-10-19T12:00:00Z,\"SKU-1,1,10,5,COMPLETED", 10));
    }

    @Test
    void rejectsFilesBeyondConfiguredDataRowLimit() {
        String csv = HEADER + "\norder-1,2026-10-19T12:00:00Z,SKU-1,1,10,5,COMPLETED\n" +
                "order-2,2026-10-19T12:00:00Z,SKU-2,1,10,5,COMPLETED\n";

        assertThrows(ApiException.class, () -> parse(csv, 1));
    }

    private List<TransactionCsvParser.ParsedCsvRow> parse(String csv, int maxRows) {
        List<TransactionCsvParser.ParsedCsvRow> rows = new ArrayList<>();
        new TransactionCsvParser(maxRows).parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), rows::add);
        return rows;
    }
}
