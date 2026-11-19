package vn.edu.fpt.paspm.transaction.importing;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.util.XMLHelper;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

final class ExcelXml {
    private static final Pattern EXCEL_ESCAPE = Pattern.compile("_x([0-9a-fA-F]{4})_");
    private ExcelXml() {}
    static void parse(InputStream stream, Handler handler) throws Exception {
        var parser = XMLHelper.newXMLReader();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parser.setContentHandler(handler);
        parser.setEntityResolver((publicId, systemId) -> { throw ExcelRows.invalid("Excel external XML entities are unsupported"); });
        parser.parse(new InputSource(stream));
    }
    static String text(String encoded) {
        Matcher matcher = EXCEL_ESCAPE.matcher(encoded);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(matcher.group(1), 16))));
        matcher.appendTail(result);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i)))
                    throw ExcelRows.invalid("Excel contains invalid Unicode text");
            } else if (Character.isLowSurrogate(c)) throw ExcelRows.invalid("Excel contains invalid Unicode text");
        }
        return result.toString();
    }
    abstract static class Handler extends DefaultHandler {
        private int depth;
        private String namespace;
        @Override public final void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (++depth > 64) throw ExcelRows.invalid("Excel XML nesting exceeds the supported limit");
            String name = localName.isEmpty() ? qName : localName;
            if (depth == 1) { namespace = uri; root(uri, name); }
            else if (!java.util.Objects.equals(namespace, uri)) throw ExcelRows.invalid("Excel contains unsupported XML namespaces");
            start(name, attributes);
        }
        @Override public final void endElement(String uri, String localName, String qName) {
            end(localName.isEmpty() ? qName : localName); depth--;
        }
        void root(String uri, String name) {}
        void start(String name, Attributes attributes) {}
        void end(String name) {}
    }
}
