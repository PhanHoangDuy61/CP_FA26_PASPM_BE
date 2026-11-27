package vn.edu.fpt.paspm.auth;

import org.springframework.web.util.HtmlUtils;

/**
 * Composes the one-time-code e-mails (subject, plain-text part and HTML part).
 *
 * <p>The e-mail is English, brand text only (no remote images, no tracking, inline CSS) and never
 * shows internal data such as purpose names or IDs. Every dynamic value that reaches the HTML part
 * is escaped. The plain-text part keeps the code within the first 200 characters because Mailpit
 * (and some mail clients) preview only the start of the text part.
 */
public final class OtpEmail {
    static final String SELF_REGISTER = "SELF_REGISTER";
    static final String ADMIN_INVITE = "ADMIN_INVITE";
    static final String PASSWORD_RESET = "PASSWORD_RESET";

    private static final String SECURITY_NOTE = "If you didn't request this, you can ignore this email.";
    private static final String NEVER_SHARE = "Never share this code with anyone.";

    private final String subject;
    private final String text;
    private final String html;

    private OtpEmail(String subject, String text, String html) {
        this.subject = subject;
        this.text = text;
        this.html = html;
    }

    public String subject() {
        return subject;
    }

    public String text() {
        return text;
    }

    public String html() {
        return html;
    }

    /**
     * @param purpose one of SELF_REGISTER, ADMIN_INVITE, PASSWORD_RESET
     * @param name    the recipient's display name, or null/blank when the account has none yet
     * @param code    the six-digit code
     * @param ttlSeconds the real challenge lifetime
     */
    public static OtpEmail compose(String purpose, String name, String code, long ttlSeconds) {
        Copy copy = copy(purpose);
        String greetingName = clean(name);
        String expiry = expiry(ttlSeconds);

        String greeting = greetingName.isEmpty() ? "Hello," : "Hello " + greetingName + ",";
        String expiryLine = "This code expires in " + expiry + ".";

        String text = "PASPM\n\n"
                + copy.heading + "\n\n"
                + greeting + "\n\n"
                + copy.purposeLine + "\n\n"
                + "Your verification code: " + code + "\n\n"
                + expiryLine + "\n\n"
                + SECURITY_NOTE + " " + NEVER_SHARE + "\n\n"
                + "-- \nPASPM. This is an automated message; please do not reply.\n";

        String html = html(copy, greeting, code, expiryLine);
        return new OtpEmail(copy.subject, text, html);
    }

    private static String html(Copy copy, String greeting, String code, String expiryLine) {
        String font = "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(copy.subject) + "</title></head>\n"
                + "<body style=\"margin:0;padding:0;background-color:#f4f6f8;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:#f4f6f8;\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:520px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:8px;\">\n"
                + "<tr><td style=\"padding:20px 32px;background-color:#0f766e;border-radius:8px 8px 0 0;" + font
                + "font-size:22px;font-weight:700;letter-spacing:2px;color:#ffffff;\">PASPM</td></tr>\n"
                + "<tr><td style=\"padding:28px 32px 8px 32px;" + font + "color:#0f172a;\">\n"
                + "<h1 style=\"margin:0 0 16px 0;font-size:20px;line-height:28px;font-weight:700;color:#0f172a;\">" + esc(copy.heading) + "</h1>\n"
                + "<p style=\"margin:0 0 12px 0;font-size:15px;line-height:24px;\">" + esc(greeting) + "</p>\n"
                + "<p style=\"margin:0 0 20px 0;font-size:15px;line-height:24px;\">" + esc(copy.purposeLine) + "</p>\n"
                + "<p style=\"margin:0 0 6px 0;font-size:13px;line-height:20px;color:#475569;\">Your verification code</p>\n"
                + "<p style=\"margin:0 0 20px 0;padding:16px 12px;background-color:#f1f5f9;border:1px solid #cbd5e1;border-radius:6px;"
                + "text-align:center;font-family:'SFMono-Regular',Menlo,Consolas,'Courier New',monospace;font-size:34px;line-height:40px;"
                + "font-weight:700;letter-spacing:8px;color:#0f172a;\">" + esc(code) + "</p>\n"
                + "<p style=\"margin:0 0 20px 0;font-size:15px;line-height:24px;\">" + esc(expiryLine) + "</p>\n"
                + "<p style=\"margin:0 0 8px 0;padding:12px 14px;background-color:#fffbeb;border-left:4px solid #d97706;font-size:14px;line-height:22px;color:#78350f;\">"
                + esc(SECURITY_NOTE) + " <strong>" + esc(NEVER_SHARE) + "</strong></p>\n"
                + "</td></tr>\n"
                + "<tr><td style=\"padding:16px 32px 24px 32px;border-top:1px solid #e2e8f0;" + font
                + "font-size:12px;line-height:18px;color:#64748b;\">PASPM &middot; This is an automated message; please do not reply.</td></tr>\n"
                + "</table>\n</td></tr></table>\n</body></html>\n";
    }

    private static Copy copy(String purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("Unsupported OTP purpose");
        }
        return switch (purpose) {
            case PASSWORD_RESET -> new Copy(
                    "Reset your PASPM password",
                    "Reset your password",
                    "We received a request to reset the password of your PASPM account. Enter this code to continue.");
            case ADMIN_INVITE -> new Copy(
                    "You're invited to PASPM",
                    "You're invited to PASPM",
                    "You have been invited to join PASPM. Enter this code to activate your account.");
            case SELF_REGISTER -> new Copy(
                    "Verify your PASPM account",
                    "Verify your email address",
                    "Thanks for signing up for PASPM. Enter this code to verify your email address.");
            default -> throw new IllegalArgumentException("Unsupported OTP purpose");
        };
    }

    static String expiry(long ttlSeconds) {
        long minutes = ttlSeconds / 60;
        long seconds = ttlSeconds % 60;
        String m = minutes + (minutes == 1 ? " minute" : " minutes");
        if (seconds == 0) {
            return m;
        }
        return minutes == 0 ? seconds + " seconds" : m + " " + seconds + " seconds";
    }

    /** Single-line, trimmed, no control characters (the value ends up in a text body and an HTML body). */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private record Copy(String subject, String heading, String purposeLine) {
    }
}
