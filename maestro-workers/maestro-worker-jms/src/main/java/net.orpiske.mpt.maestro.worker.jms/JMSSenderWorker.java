/*
 *  Copyright 2017 Otavio R. Piske <angusyoung@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.orpiske.mpt.maestro.worker.jms;

import net.orpiske.mpt.common.content.ContentStrategy;
import net.orpiske.mpt.common.content.ContentStrategyFactory;
import net.orpiske.mpt.common.duration.EpochClocks;
import net.orpiske.mpt.common.duration.EpochMicroClock;
import net.orpiske.mpt.common.duration.TestDuration;
import net.orpiske.mpt.common.duration.TestDurationBuilder;
import net.orpiske.mpt.common.exceptions.DurationParseException;
import net.orpiske.mpt.common.jms.SenderClient;
import net.orpiske.mpt.common.worker.MaestroSenderWorker;
import net.orpiske.mpt.common.worker.WorkerOptions;
import net.orpiske.mpt.common.worker.WorkerStateInfo;
import net.orpiske.mpt.common.writers.OneToOneWorkerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;


public class JMSSenderWorker implements MaestroSenderWorker {
    private static final Logger logger = LoggerFactory.getLogger(JMSSenderWorker.class);
    private ContentStrategy contentStrategy;
    private TestDuration duration;
    private final OneToOneWorkerChannel workerChannel;
    private final AtomicLong messageCount = new AtomicLong(0);
    private volatile long startedEpochMillis = Long.MIN_VALUE;

    private String url;
    private long rate = 0;

    private final Supplier<? extends SenderClient> clientFactory;

    public JMSSenderWorker() {
        this(JMSSenderClient::new, 128 * 1024);
    }

    public JMSSenderWorker(Supplier<? extends SenderClient> clientFactory, int channelCapacity) {
        this.clientFactory = clientFactory;
        this.workerChannel = new OneToOneWorkerChannel(channelCapacity);
    }

    @Override
    public OneToOneWorkerChannel workerChannel() {
        return workerChannel;
    }

    private volatile WorkerStateInfo workerStateInfo = new WorkerStateInfo();

    @Override
    public long startedEpochMillis() {
        return this.startedEpochMillis;
    }

    @Override
    public WorkerStateInfo getWorkerState() {
        return workerStateInfo;
    }


    @Override
    public long messageCount() {
        return messageCount.get();
    }

    private void setMessageSize(String messageSize) {
        contentStrategy = ContentStrategyFactory.parse(messageSize);
    }

    private void setRate(String rate) {
        this.rate = Long.parseLong(rate);
    }

    private void setBroker(String url) {
        this.url = url;
    }

    private void setDuration(String duration) {
        try {
            this.duration = TestDurationBuilder.build(duration);
        } catch (DurationParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setWorkerOptions(WorkerOptions workerOptions) {
        setRate(workerOptions.getRate());
        setDuration(workerOptions.getDuration());
        setBroker(workerOptions.getBrokerURL());
        setMessageSize(workerOptions.getMessageSize());
    }

    private static long waitNanoInterval(final long expectedFireTime, final long intervalInNanos) {
        assert intervalInNanos > 0;
        long now;
        do {
            now = System.nanoTime();
            if (now - expectedFireTime < 0) {
                LockSupport.parkNanos(expectedFireTime - now);
            }
        } while (now - expectedFireTime < 0);
        return now;
    }

    public void start() {
        startedEpochMillis = System.currentTimeMillis();
        logger.info("Starting the test");

        final SenderClient client = this.clientFactory.get();
        try {
            final EpochMicroClock epochMicroClock = EpochClocks.exclusiveMicro();
            client.setUrl(url);
            client.setContentStrategy(contentStrategy);

            workerStateInfo.setState(true, null, null);
            client.start();
            long count = 0;
            final long intervalInNanos = this.rate > 0 ? (1_000_000_000L / rate) : 0;
            if (intervalInNanos > 0) {
                logger.info("JMS Sender [" + Thread.currentThread().getId() + "] - has started firing events with interval= " + intervalInNanos + " ns [ " + rate + " msg/sec ]");
            }
            //it couldn't uses the Epoch in nanos because it could overflow pretty soon (less than 1 day)
            final long startFireEpochMicros = epochMicroClock.microTime();
            //to avoid accumulated approx errors on the expectedSendTimeEpochMillis calculations
            long elapsedIntervalsNanos = 0;
            long nextFireTime = System.nanoTime() + intervalInNanos;
            while (duration.canContinue(this) && isRunning()) {
                if (intervalInNanos > 0) {
                    final long now = waitNanoInterval(nextFireTime, intervalInNanos);
                    assert (now - nextFireTime) >= 0 : "can't wait less than the configured interval in nanos";
                    nextFireTime += intervalInNanos;
                    elapsedIntervalsNanos += intervalInNanos;
                }
                final long sendTimeEpochMicros = epochMicroClock.microTime();
                final long expectedSendTimeEpochMicros;
                if (intervalInNanos > 0) {
                    final long elapsedIntervalsMicros = (elapsedIntervalsNanos / 1_000L);
                    expectedSendTimeEpochMicros = startFireEpochMicros + elapsedIntervalsMicros;
                } else {
                    expectedSendTimeEpochMicros = sendTimeEpochMicros;
                }
                client.sendMessages(sendTimeEpochMicros);
                workerChannel.emitRate(expectedSendTimeEpochMicros, sendTimeEpochMicros);
                count++;
                //update message sent count
                this.messageCount.lazySet(count);
            }

            logger.info("Test completed successfully");
            workerStateInfo.setState(false, WorkerStateInfo.WorkerExitStatus.WORKER_EXIT_SUCCESS, null);
        } catch (InterruptedException e) {
            logger.error("JMS Sender [" + Thread.currentThread().getId() + "] interrupted while sending messages: {}",
                    e.getMessage());

            workerStateInfo.setState(false, WorkerStateInfo.WorkerExitStatus.WORKER_EXIT_FAILURE, e);
        } catch (Exception e) {
            logger.error("Unable to start the sender worker: {}", e.getMessage(), e);

            workerStateInfo.setState(false, WorkerStateInfo.WorkerExitStatus.WORKER_EXIT_FAILURE, e);
        } finally {
            //the test could be considered already stopped here, but cleaning up JMS resources could take some time anyway
            client.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return workerStateInfo.isRunning();
    }

    @Override
    public void stop() {
        workerStateInfo.setState(false, WorkerStateInfo.WorkerExitStatus.WORKER_EXIT_STOPPED, null);
    }

    @Override
    public void halt() {
        stop();
    }

    @Override
    public void run() {
        start();
    }
}
