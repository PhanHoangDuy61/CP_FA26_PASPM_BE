package vn.edu.fpt.paspm.persistence;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PilotCloneSafetyTest {
    private Properties target() {
        var p=new Properties();p.setProperty("marker","012345abcdef");p.setProperty("disposable","true");
        p.setProperty("schema","paspm_jpa_pilot_012345abcdef");p.setProperty("sourceSchema","source_db");
        p.setProperty("url","jdbc:mysql://127.0.0.1:3306/paspm_jpa_pilot_012345abcdef");
        p.setProperty("username","jpa_pilot_012345abcdef");return p;
    }
    @Test void rejectsMissingMutationConsent(){assertThrows(IllegalStateException.class,()->PilotCloneTarget.validate(target(),"false"));}
    @Test void rejectsSourceCollision(){var p=target();p.setProperty("sourceSchema",p.getProperty("schema"));assertThrows(IllegalStateException.class,()->PilotCloneTarget.validate(p,"true"));}
    @Test void rejectsRemoteAndUnmarkedTargets(){var p=target();p.setProperty("url","jdbc:mysql://remote.example:3306/paspm_jpa_pilot_012345abcdef");assertThrows(IllegalStateException.class,()->PilotCloneTarget.validate(p,"true"));var unmarked=target();unmarked.setProperty("disposable","false");assertThrows(IllegalStateException.class,()->PilotCloneTarget.validate(unmarked,"true"));}
    @Test void acceptsMatchingDisposableIdentity(){assertSame(Properties.class,PilotCloneTarget.validate(target(),"true").getClass());}
}
