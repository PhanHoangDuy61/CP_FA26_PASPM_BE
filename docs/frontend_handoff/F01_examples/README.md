# F01 Examples

All identities, names, emails, password strings, OTPs and IDs in these files are synthetic examples. The OTP `123456` is not an issued code. `<synthetic JWT omitted>` is illustrative and cannot authenticate. The Google ID token string is a placeholder; replace it with a fresh token from Google Identity Services in an authorized development environment. Never commit real credentials/tokens/OTPs.

`password_change_request.json` and `password_change_response.json` show the authenticated self-service password change. Credentials are synthetic and must not be used outside examples.

The current synthetic `login_response.json` includes `csrfToken`. Its token/CSRF placeholders are not valid credentials; refresh credentials are supplied in HttpOnly cookies, never response JSON. See [session integration contract](../../api/F01_AUTH_USER_API.md#session-integration-update--2026-11-11).
