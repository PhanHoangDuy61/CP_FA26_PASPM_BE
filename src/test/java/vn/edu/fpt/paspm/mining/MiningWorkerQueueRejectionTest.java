package vn.edu.fpt.paspm.mining;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiningWorkerQueueRejectionTest {
    @Test void fullQueueRecordsRejectedRequestWithoutMarkingItFailed() throws Exception {
        var repository=mock(MiningRepository.class);
        var worker=spy(new MiningWorker(repository,mock(MiningEngine.class),mock(MiningDispatchLease.class),true));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();release.await(10,TimeUnit.SECONDS);return null;}).when(worker).execute(anyInt(),anyLong());
        Logger logger=(Logger)LoggerFactory.getLogger(MiningWorker.class);Level previous=logger.getLevel();
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);logger.setLevel(Level.DEBUG);
        try {
            worker.recover();worker.enqueue(17,1);assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(long id=2;id<=10;id++)worker.enqueue(17,id);
            assertTrue(appender.list.stream().anyMatch(e->e.getFormattedMessage().contains("MINING_QUEUE_REJECTED store=17 request=10")),"full queue must expose the rejected Store/request identity");
            verify(repository,never()).failRun(any(),anyLong(),anyString(),anyString());
        } finally {release.countDown();worker.close();logger.detachAppender(appender);logger.setLevel(previous);appender.stop();}
    }
}
