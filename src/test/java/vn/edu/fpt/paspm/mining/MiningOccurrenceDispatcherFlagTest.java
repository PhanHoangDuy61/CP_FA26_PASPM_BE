package vn.edu.fpt.paspm.mining;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pins the safety switch: no background schedule dispatch unless the instance explicitly opts in. */
class MiningOccurrenceDispatcherFlagTest {
    private static JdbcTemplate emptyDb() {
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test void flagDefaultsToOffWhenPropertyIsAbsent() throws Exception {
        var ctor = MiningOccurrenceDispatcher.class.getConstructors()[0];
        var param = ctor.getParameters()[4];
        assertEquals(boolean.class, param.getType());
        assertEquals("${paspm.mining.schedule-dispatch-enabled:false}", param.getAnnotation(Value.class).value());
    }

    @Test void disabledDispatcherNeverReservesOrPollsEvenAfterApplicationReady() throws Exception {
        var reservation = Mockito.mock(MiningScheduleReservation.class);
        var dispatcher = new MiningOccurrenceDispatcher(emptyDb(), reservation, null, null, false);
        try {
            dispatcher.start();
            Thread.sleep(400);
            verifyNoInteractions(reservation);
        } finally {
            dispatcher.close();
        }
    }

    @Test void enabledDispatcherPollsSoTheDisabledAssertionCanActuallyFail() {
        var reservation = Mockito.mock(MiningScheduleReservation.class);
        when(reservation.reserveOne(any(Instant.class))).thenReturn(Optional.empty());
        var dispatcher = new MiningOccurrenceDispatcher(emptyDb(), reservation, null, null, true);
        try {
            dispatcher.start();
            verify(reservation, timeout(3000).atLeastOnce()).reserveOne(any(Instant.class));
        } finally {
            dispatcher.close();
        }
    }
}
