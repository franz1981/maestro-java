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

import net.orpiske.mpt.common.duration.TestDuration;
import net.orpiske.mpt.common.duration.TestDurationBuilder;
import net.orpiske.mpt.common.exceptions.DurationParseException;
import net.orpiske.mpt.common.worker.MaestroReceiverWorker;
import net.orpiske.mpt.common.worker.MessageInfo;
import net.orpiske.mpt.common.worker.WorkerOptions;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class JMSReceiverWorker implements MaestroReceiverWorker {
    private static final Logger logger = LoggerFactory.getLogger(JMSReceiverWorker.class);

    private TestDuration duration;
    private String url;
    private boolean running = false;
    private final AtomicLong messageCount = new AtomicLong(0);
    private volatile long startedEpochMillis = Long.MIN_VALUE;
    //TODO it could be injected by outside because the precision could be improved using ad-hoc clock timers
    private final SingleWriterRecorder latencyRecorder = new SingleWriterRecorder(TimeUnit.HOURS.toMillis(1), 3);

    @Override
    public long messageCount() {
        return messageCount.get();
    }

    @Override
    public long startedEpochMillis() {
        return startedEpochMillis;
    }

    private void setFCL(String fcl) {

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
        setBroker(workerOptions.getBrokerURL());
        setDuration(workerOptions.getDuration());
    }

    public void start() {
        running = true;
        startedEpochMillis = System.currentTimeMillis();
        logger.info("Starting the test");

        try {
            JMSReceiverClient client = new JMSReceiverClient();

            client.setUrl(url);

            client.start();

            long count = 0;

            while (duration.canContinue(this) && isRunning()) {
                final MessageInfo info = client.receiveMessages();
                //TODO would be better to use a general purpose Clock API
                final long now = System.currentTimeMillis();
                final long elapsedMillis = now - info.getCreationTime().toEpochMilli();
                //we can perform fnc check here to fail the test
                //TODO use Histogram:recordValueWithExpectedInterval if the rate is known
                latencyRecorder.recordValue(elapsedMillis);
                count++;
                messageCount.lazySet(count);
            }
        } catch (Exception e) {
            logger.error("Unable to start the worker: {}", e.getMessage(), e);
        } finally {
            running = false;
        }

    }

    @Override
    public Histogram takeLatenciesSnapshot(Histogram intervalHistogram) {
        return latencyRecorder.getIntervalHistogram(intervalHistogram);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        running = false;
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
