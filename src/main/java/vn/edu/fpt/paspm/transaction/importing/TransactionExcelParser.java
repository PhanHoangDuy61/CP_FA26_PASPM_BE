package vn.edu.fpt.paspm.transaction.importing;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionImportProperties;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;

/** Bounded file-based Excel adapters share CSV's normalized row/order pipeline. */
@Component
public class TransactionExcelParser {
    private final TransactionImportProperties properties;
    private final ExcelImportProperties limits;
    public TransactionExcelParser(TransactionImportProperties properties) { this(properties, ExcelImportProperties.defaults()); }
    @Autowired
    public TransactionExcelParser(TransactionImportProperties properties, ExcelImportProperties limits) {
        this.properties = properties; this.limits = limits;
    }
    public void parse(InputStream input, Consumer<ParsedCsvRow> sink) {
        Path file = null;
        List<ParsedCsvRow> accepted;
        try {
            file = Files.createTempFile("paspm-excel-", ".upload");
            try (InputStream source = input; var target = Files.newOutputStream(file)) {
                long bytes = 0;
                byte[] buffer = new byte[8192];
                int length;
                while ((length = source.read(buffer)) != -1) {
                    bytes += length;
                    if (bytes > properties.maxFileBytes()) throw new ApiException(413, "Uploaded file exceeds the configured limit");
                    target.write(buffer, 0, length);
                }
            }
            var rows = new ExcelRows(properties.maxRows(), limits);
            byte[] magic;
            try (var stream = Files.newInputStream(file)) { magic = stream.readNBytes(8); }
            if (magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4)
                new TransactionXlsxReader(limits).read(file, rows);
            else if (magic.length == 8 && java.util.Arrays.equals(magic, new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1}))
                new TransactionXlsReader(rows, limits).read(file);
            else throw ExcelRows.invalid("Uploaded content is not a supported Excel workbook");
            accepted = rows.finish();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ExcelRows.invalid("Excel workbook is malformed or contains unsupported content");
        } finally {
            if (file != null) {
                try { Files.deleteIfExists(file); }
                catch (java.io.IOException exception) { file.toFile().deleteOnExit(); }
            }
        }
        // Do not swallow failures raised by the downstream persistence/validation consumer.
        accepted.forEach(sink);
    }
}
