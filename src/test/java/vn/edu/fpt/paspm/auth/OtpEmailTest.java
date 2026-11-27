package vn.edu.fpt.paspm.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OtpEmailTest {
    private static final Map<String, String> SUBJECTS = Map.of(
            "PASSWORD_RESET", "Reset your PASPM password",
            "SELF_REGISTER", "Verify your PASPM account",
            "ADMIN_INVITE", "You're invited to PASPM");
    private static final List<String> INTERNAL = List.of("PASSWORD_RESET", "SELF_REGISTER", "ADMIN_INVITE", "PENDING_VERIFICATION");

    @Test
    void everyPurposeHasItsOwnSubjectCodeExpiryAndSecurityNote() {
        for (var entry : SUBJECTS.entrySet()) {
            OtpEmail email = OtpEmail.compose(entry.getKey(), "Linh", "679015", 600);

            assertEquals(entry.getValue(), email.subject());
            for (String part : List.of(email.text(), email.html())) {
                assertTrue(part.contains("679015"), entry.getKey());
                assertTrue(part.contains("expires in 10 minutes"), entry.getKey());
                assertTrue(part.contains("Hello Linh,"), entry.getKey());
                assertTrue(part.contains("you can ignore this email"), entry.getKey());
                assertTrue(part.contains("Never share this code"), entry.getKey());
                assertTrue(part.contains("PASPM"), entry.getKey());
            }
            assertTrue(email.text().contains("Your verification code: 679015"));
        }
    }

    @Test
    void neverShowsEnumNamesOrInternalValues() {
        for (String purpose : SUBJECTS.keySet()) {
            OtpEmail email = OtpEmail.compose(purpose, null, "123456", 600);
            for (String internal : INTERNAL) {
                assertFalse(email.subject().contains(internal), purpose + " subject");
                assertFalse(email.text().contains(internal), purpose + " text");
                assertFalse(email.html().contains(internal), purpose + " html");
            }
            assertFalse(email.text().contains("_"), purpose + " text has an underscore-style token");
        }
    }

    @Test
    void htmlEscapesEveryDynamicValue() {
        OtpEmail email = OtpEmail.compose("ADMIN_INVITE", "<script>alert(1)</script> & \"Co\"", "<b>1</b>", 600);

        assertFalse(email.html().contains("<script>"));
        assertFalse(email.html().contains("<b>1</b>"));
        assertTrue(email.html().contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot;"));
        assertTrue(email.html().contains("&lt;b&gt;1&lt;/b&gt;"));
    }

    @Test
    void plainTextPartExistsAndKeepsTheCodeInTheMailpitSnippet() {
        for (String purpose : SUBJECTS.keySet()) {
            OtpEmail email = OtpEmail.compose(purpose, "A very long display name of a person that goes on and on", "246810", 600);
            assertFalse(email.text().isBlank());
            assertFalse(email.text().contains("<"), "text part is not HTML");
            assertTrue(email.text().indexOf("246810") < 200, purpose + ": the code must be in the first 200 characters");
        }
    }

    @Test
    void nameIsOptionalAndControlCharactersAreRemoved() {
        assertTrue(OtpEmail.compose("SELF_REGISTER", null, "111111", 600).text().contains("Hello,\n"));
        assertTrue(OtpEmail.compose("SELF_REGISTER", "  ", "111111", 600).text().contains("Hello,\n"));
        OtpEmail email = OtpEmail.compose("SELF_REGISTER", "Evil\r\nBcc: x", "111111", 600);
        assertTrue(email.text().contains("Hello Evil Bcc: x,\n"));
    }

    @Test
    void htmlHasNoRemoteResourcesOrTracking() {
        for (String purpose : SUBJECTS.keySet()) {
            String html = OtpEmail.compose(purpose, "Linh", "123456", 600).html().toLowerCase();
            assertFalse(html.contains("<img"));
            assertFalse(html.contains("<script"));
            assertFalse(html.contains("<link"));
            assertFalse(html.contains("http://"));
            assertFalse(html.contains("https://"));
            assertFalse(html.contains("url("));
            assertTrue(html.contains("style=\""));
        }
    }

    @Test
    void expiryFollowsTheConfiguredLifetime() {
        assertEquals("10 minutes", OtpEmail.expiry(600));
        assertEquals("1 minute", OtpEmail.expiry(60));
        assertEquals("30 minutes", OtpEmail.expiry(1800));
        assertEquals("1 minute 30 seconds", OtpEmail.expiry(90));
        assertTrue(OtpEmail.compose("PASSWORD_RESET", null, "123456", 300).text().contains("expires in 5 minutes"));
    }

    @Test
    void unknownPurposeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OtpEmail.compose("SOMETHING_ELSE", null, "123456", 600));
        assertThrows(IllegalArgumentException.class, () -> OtpEmail.compose(null, null, "123456", 600));
    }
}
