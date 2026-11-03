package vn.edu.fpt.paspm.transaction.importing;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionImportProperties;

/** Streaming RFC 4180 parser for the approved F04 transaction CSV contract. */
@Component
public class TransactionCsvParser {
    public static final List<String> HEADERS = List.of(
            "source_order_id", "occurred_at", "sku", "quantity", "unit_price", "unit_cost", "status");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final int maxRows;

    @Autowired
    public TransactionCsvParser(TransactionImportProperties properties) {
        this(properties.maxRows());
    }

    public TransactionCsvParser(int maxRows) {
        if (maxRows < 1) throw new IllegalArgumentException("F04 CSV max rows must be positive");
        this.maxRows = maxRows;
    }

    public void parse(InputStream input, Consumer<ParsedCsvRow> sink) {
        try (var reader = new InputStreamReader(withoutBom(input), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT));
             var parser = new CSVParser(reader, CSVFormat.RFC4180)) {
            var records = parser.iterator();
            if (!records.hasNext()) throw badCsv("CSV header is required");
            CSVRecord header = records.next();
            validateHeader(header);

            int dataRows = 0;
            while (records.hasNext()) {
                CSVRecord record = records.next();
                if (isBlank(record)) continue;
                if (++dataRows > maxRows) throw badCsv("CSV exceeds the configured row limit");
                if (record.size() != HEADERS.size()) {
                    throw badCsv("CSV record " + record.getRecordNumber() + " has an unexpected column count");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < HEADERS.size(); i++) values.put(HEADERS.get(i), record.get(i));
                sink.accept(new ParsedCsvRow(Math.toIntExact(record.getRecordNumber()), Map.copyOf(values)));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | UncheckedIOException | ArithmeticException exception) {
            throw badCsv("CSV is malformed or is not valid UTF-8");
        }
    }

    private PushbackInputStream withoutBom(InputStream input) throws IOException {
        PushbackInputStream stream = new PushbackInputStream(input, UTF8_BOM.length);
        byte[] prefix = stream.readNBytes(UTF8_BOM.length);
        boolean isBom = prefix.length == UTF8_BOM.length;
        for (int i = 0; isBom && i < UTF8_BOM.length; i++) isBom = prefix[i] == UTF8_BOM[i];
        if (!isBom) stream.unread(prefix);
        return stream;
    }

    private void validateHeader(CSVRecord header) {
        if (header.size() != HEADERS.size()) throw badCsv("CSV header must match the approved F04 template");
        for (int i = 0; i < HEADERS.size(); i++) {
            if (!HEADERS.get(i).equals(header.get(i))) throw badCsv("CSV header must match the approved F04 template");
        }
    }

    private boolean isBlank(CSVRecord record) {
        for (String value : record) if (!value.isBlank()) return false;
        return true;
    }

    private ApiException badCsv(String message) {
        return new ApiException(400, message);
    }

    public record ParsedCsvRow(int rowNumber, Map<String, String> values) {}
}
