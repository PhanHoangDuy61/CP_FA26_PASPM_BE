package vn.edu.fpt.paspm.auth;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Helpers for tests that mock {@code JavaMailSender} and read the OTP e-mail it was asked to send. */
public final class OtpMailTestSupport {
    private static final Pattern CODE = Pattern.compile("Your verification code: ([0-9]{6})");

    private OtpMailTestSupport() {
    }

    public static MimeMessage newMessage() {
        return new MimeMessage((Session) null);
    }

    public static String text(MimeMessage message) throws MessagingException, IOException {
        return find(message, "text/plain");
    }

    public static String html(MimeMessage message) throws MessagingException, IOException {
        return find(message, "text/html");
    }

    public static String code(MimeMessage message) throws MessagingException, IOException {
        Matcher matcher = CODE.matcher(text(message));
        if (!matcher.find()) {
            throw new AssertionError("no verification code in the text part");
        }
        return matcher.group(1);
    }

    private static String find(Part part, String type) throws MessagingException, IOException {
        if (part.isMimeType(type)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
