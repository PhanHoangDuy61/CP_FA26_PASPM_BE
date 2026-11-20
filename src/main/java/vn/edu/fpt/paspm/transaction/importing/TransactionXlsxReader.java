package vn.edu.fpt.paspm.transaction.importing;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import vn.edu.fpt.paspm.transaction.importing.ExcelRows.Cell;
import vn.edu.fpt.paspm.transaction.importing.ExcelRows.Kind;

/** File-backed OOXML preflight and typed SAX reader; no workbook user model or formula evaluation. */
final class TransactionXlsxReader {
    private static final Pattern ALLOWED_PART = Pattern.compile(
            "(?:\\[Content_Types]\\.xml|_rels/\\.rels|docProps/(?:core|app|custom)\\.xml|xl/workbook\\.xml|xl/_rels/workbook\\.xml\\.rels|xl/worksheets/sheet[0-9]+\\.xml|xl/worksheets/_rels/sheet[0-9]+\\.xml\\.rels|xl/(?:styles|sharedStrings)\\.xml|xl/theme/theme[0-9]+\\.xml)");
    private final ExcelImportProperties limits;
    TransactionXlsxReader(ExcelImportProperties limits) { this.limits = limits; }

    void read(Path file, ExcelRows rows) throws Exception {
        preflight(file);
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            List<String> shared = new ArrayList<>();
            try (InputStream stream = reader.getSharedStringsData()) {
                if (stream != null) ExcelXml.parse(stream, new StringsHandler(shared));
            }
            var sheets = reader.getSheetsData();
            while (sheets.hasNext()) {
                rows.beginSheet();
                try (InputStream sheet = sheets.next()) { ExcelXml.parse(sheet, new SheetHandler(rows, shared)); }
            }
        }
    }

    private void preflight(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Set<String> names = new HashSet<>();
            int count = 0;
            long total = 0;
            byte[] buffer = new byte[8192];
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (++count > limits.maxEntries()) throw ExcelRows.invalid("Excel ZIP entry count exceeds the configured limit");
                if (!names.add(name.toLowerCase(Locale.ROOT)) || name.contains("..") || name.contains("\\") || name.startsWith("/"))
                    throw ExcelRows.invalid("Excel ZIP contains duplicate or invalid entry names");
                if (entry.isDirectory()) continue;
                if (!ALLOWED_PART.matcher(name).matches()) throw ExcelRows.invalid("Excel contains unsupported workbook content");
                long expanded = 0;
                try (InputStream stream = zip.getInputStream(entry)) {
                    int length;
                    while ((length = stream.read(buffer)) != -1) {
                        expanded += length; total += length;
                        if (expanded > limits.maxEntryBytes() || total > limits.maxExpandedBytes())
                            throw ExcelRows.invalid("Excel expanded content exceeds the configured limit");
                    }
                }
                if (expanded > 102400 && entry.getCompressedSize() >= 0
                        && entry.getCompressedSize() < expanded * 0.01d)
                    throw ExcelRows.invalid("Excel ZIP compression ratio is unsafe");
                if (name.endsWith(".rels") || name.equals("[Content_Types].xml")
                        || name.equals("xl/styles.xml") || name.equals("xl/workbook.xml")) {
                    try (InputStream stream = zip.getInputStream(entry)) { ExcelXml.parse(stream, new MetadataHandler(name)); }
                }
            }
            if (!names.contains("[content_types].xml") || !names.contains("xl/workbook.xml"))
                throw ExcelRows.invalid("Excel workbook parts are missing");
        }
    }

    private final class MetadataHandler extends ExcelXml.Handler {
        private final String part;
        int styles;
        int sheets;
        MetadataHandler(String part) { this.part = part; }
        @Override void root(String uri, String name) {
            String root = part.endsWith(".rels") ? "Relationships" : part.equals("[Content_Types].xml") ? "Types"
                    : part.equals("xl/styles.xml") ? "styleSheet" : "workbook";
            String namespace = part.endsWith(".rels") ? "http://schemas.openxmlformats.org/package/2006/relationships"
                    : part.equals("[Content_Types].xml") ? "http://schemas.openxmlformats.org/package/2006/content-types"
                    : "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
            if (!name.equals(root) || !uri.equals(namespace)) throw ExcelRows.invalid("Excel workbook XML root is invalid");
        }
        @Override void start(String name, Attributes attributes) {
            if (name.equals("Relationship")) {
                String target = attributes.getValue("Target");
                String mode = attributes.getValue("TargetMode");
                if ((mode != null && !mode.equals("Internal")) || target == null || target.contains(":") || target.startsWith("//"))
                    throw ExcelRows.invalid("Excel external links are unsupported");
            }
            if (name.equals("Override") || name.equals("Default")) {
                String type = attributes.getValue("ContentType");
                if (type != null && (type.toLowerCase(Locale.ROOT).contains("macro")
                        || type.toLowerCase(Locale.ROOT).contains("vba")
                        || type.toLowerCase(Locale.ROOT).contains("oleobject")))
                    throw ExcelRows.invalid("Excel active content is unsupported");
            }
            if (name.equals("externalReference") || name.equals("definedName"))
                throw ExcelRows.invalid("Excel external references and defined-name formulas are unsupported");
            if (name.equals("sheet") && ++sheets > limits.maxEntries())
                throw ExcelRows.invalid("Excel worksheet count exceeds the configured limit");
            if (name.equals("xf") && ++styles > limits.maxStyles())
                throw ExcelRows.invalid("Excel styles exceed the configured limit");
        }
    }

    private final class StringsHandler extends ExcelXml.Handler {
        private final List<String> shared;
        private final StringBuilder text = new StringBuilder();
        private boolean inText;
        private boolean phonetic;
        StringsHandler(List<String> shared) { this.shared = shared; }
        @Override void root(String uri, String name) {
            if (!name.equals("sst") || !uri.equals("http://schemas.openxmlformats.org/spreadsheetml/2006/main"))
                throw ExcelRows.invalid("Excel shared-string XML root is invalid");
        }
        @Override void start(String name, Attributes attributes) {
            if (name.equals("si")) {
                if (shared.size() >= limits.maxSharedStrings()) throw ExcelRows.invalid("Excel shared strings exceed the configured limit");
                text.setLength(0);
            }
            if (name.equals("rPh")) phonetic = true;
            if (name.equals("t")) inText = true;
        }
        @Override void end(String name) {
            if (name.equals("t")) inText = false;
            if (name.equals("rPh")) phonetic = false;
            if (name.equals("si")) shared.add(ExcelXml.text(text.toString()));
        }
        @Override public void characters(char[] characters, int offset, int length) {
            if (inText && !phonetic) {
                if (text.length() + length > limits.maxCellCharacters()) throw ExcelRows.invalid("Excel shared string exceeds the configured text limit");
                text.append(characters, offset, length);
            }
        }
    }

    private final class SheetHandler extends ExcelXml.Handler {
        private final ExcelRows rows;
        private final List<String> shared;
        private final Map<Integer, Cell> cells = new TreeMap<>();
        private final StringBuilder value = new StringBuilder();
        private int row = -1;
        private int column;
        private int nextColumn;
        private boolean capture;
        private boolean phonetic;
        private String type;
        SheetHandler(ExcelRows rows, List<String> shared) { this.rows = rows; this.shared = shared; }
        @Override void root(String uri, String name) {
            if (!name.equals("worksheet") || !uri.equals("http://schemas.openxmlformats.org/spreadsheetml/2006/main"))
                throw ExcelRows.invalid("Excel worksheet XML root is invalid");
        }
        @Override void start(String name, Attributes attributes) {
            if (name.equals("row")) {
                rows.scannedRow();
                String reference = attributes.getValue("r");
                int next = reference == null ? row + 1 : Integer.parseInt(reference) - 1;
                if (next <= row || next < 0 || next >= 1048576) throw ExcelRows.invalid("Excel worksheet rows are invalid or out of order");
                row = next; cells.clear(); nextColumn = 0;
            } else if (name.equals("c")) {
                String reference = attributes.getValue("r");
                column = reference == null ? nextColumn : column(reference);
                if (column < nextColumn || column > 16383) throw ExcelRows.invalid("Excel cell positions are invalid or out of order");
                nextColumn = column + 1;
                type = attributes.getValue("t");
                if (type != null && !Set.of("s", "inlineStr", "n").contains(type))
                    throw ExcelRows.invalid("Excel contains unsupported cell types");
                value.setLength(0);
            } else if (Set.of("f", "formula", "formula1", "formula2", "cfRule", "conditionalFormatting", "dataValidation", "dataValidations").contains(name)) {
                throw ExcelRows.invalid("Excel formulas are unsupported");
            } else if (name.equals("rPh")) phonetic = true;
            else if (name.equals("v") || name.equals("t")) capture = true;
        }
        @Override void end(String name) {
            if (name.equals("v") || name.equals("t")) capture = false;
            else if (name.equals("rPh")) phonetic = false;
            else if (name.equals("c")) {
                String text = value.toString();
                Cell cell;
                if ("s".equals(type)) {
                    int index = Integer.parseInt(text);
                    if (index < 0 || index >= shared.size()) throw ExcelRows.invalid("Excel shared-string reference is invalid");
                    cell = new Cell(Kind.TEXT, shared.get(index));
                } else if ("inlineStr".equals(type)) cell = new Cell(Kind.TEXT, ExcelXml.text(text));
                else cell = text.isEmpty() ? new Cell(Kind.BLANK, "") : new Cell(Kind.NUMBER, text);
                cells.put(column, cell);
            } else if (name.equals("row")) rows.accept(row, cells);
        }
        private int column(String reference) {
            int index = 0;
            int letters = 0;
            while (letters < reference.length() && reference.charAt(letters) >= 'A' && reference.charAt(letters) <= 'Z') {
                index = Math.addExact(Math.multiplyExact(index, 26), reference.charAt(letters) - 'A' + 1); letters++;
            }
            if (letters == 0 || letters == reference.length() || Integer.parseInt(reference.substring(letters)) != row + 1)
                throw ExcelRows.invalid("Excel cell row reference is inconsistent");
            return index - 1;
        }
        @Override public void characters(char[] characters, int offset, int length) {
            if (capture && !phonetic) {
                if (value.length() + length > limits.maxCellCharacters()) throw ExcelRows.invalid("Excel cell exceeds the configured text limit");
                value.append(characters, offset, length);
            }
        }
    }
}
