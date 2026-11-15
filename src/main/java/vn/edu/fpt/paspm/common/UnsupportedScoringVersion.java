package vn.edu.fpt.paspm.common;

/** Stored data cannot be interpreted by this Backend version; no automatic rewrite is safe. */
public class UnsupportedScoringVersion extends ApiException {
    public static final String CODE="UNSUPPORTED_SCORING_VERSION";
    public UnsupportedScoringVersion(String storedValue) {
        super(409,"Unsupported stored scoringVersion "+storedValue+"; this Backend cannot read that scoring model. Contact the administrator before retrying.");
    }
}
