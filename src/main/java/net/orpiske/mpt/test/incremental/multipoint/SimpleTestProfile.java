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

package net.orpiske.mpt.test.incremental.multipoint;

import net.orpiske.mpt.maestro.Maestro;
import net.orpiske.mpt.maestro.exceptions.MaestroException;
import net.orpiske.mpt.test.MultiPointProfile;
import net.orpiske.mpt.test.SinglePointProfile;
import net.orpiske.mpt.test.incremental.IncrementalTestProfile;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SimpleTestProfile extends IncrementalTestProfile implements MultiPointProfile {
    private static final Logger logger = LoggerFactory.getLogger(SimpleTestProfile.class);
    private List<EndPoint> endPoints = new LinkedList<>();

    @Override
    public void addEndPoint(EndPoint endPoint) {
        endPoints.add(endPoint);
    }

    @Override
    public List<EndPoint> getEndPoints() {
        return endPoints;
    }

    public void apply(Maestro maestro) throws MqttException, IOException, MaestroException {
        for (EndPoint endPoint : endPoints) {
            logger.info("Setting {} end point to {}", endPoint.getName(), endPoint.getBrokerURL());
            logger.debug(" {} end point located at {}", endPoint.getName(), endPoint.getTopic());

            maestro.setBroker(endPoint.getTopic(), endPoint.getBrokerURL());
        }

        logger.info("Setting rate to {}", getRate());
        maestro.setRate(rate);

        logger.info("Rate increment value is {}", getRateIncrement());

        logger.info("Setting parallel count to {}", this.parallelCount);
        maestro.setParallelCount(this.parallelCount);

        logger.info("Parallel count increment value is {}", getParallelCountIncrement());

        logger.info("Setting duration to {}", getDuration());
        maestro.setDuration(this.getDuration().getDuration());

        logger.info("Setting fail-condition-latency to {}", getMaximumLatency());
        maestro.setFCL(getMaximumLatency());

        // Variable message messageSize
        maestro.setMessageSize(getMessageSize());
    }
}
