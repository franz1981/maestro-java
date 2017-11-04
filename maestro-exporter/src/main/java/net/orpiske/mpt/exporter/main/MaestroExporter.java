package net.orpiske.mpt.exporter.main;

import io.prometheus.client.exporter.HTTPServer;
import net.orpiske.mpt.exporter.collectors.ConnectionCount;
import net.orpiske.mpt.exporter.collectors.MessageCount;
import net.orpiske.mpt.exporter.collectors.RateCount;
import net.orpiske.mpt.maestro.Maestro;
import net.orpiske.mpt.maestro.client.MaestroCollector;
import net.orpiske.mpt.maestro.notes.MaestroNote;
import net.orpiske.mpt.maestro.notes.StatsResponse;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class MaestroExporter {
    private static final Logger logger = LoggerFactory.getLogger(MaestroExporter.class);

    private boolean running = true;
    private Maestro maestro = null;
    private MaestroCollector maestroCollector;

    private static final MessageCount messagesSent = new MessageCount("sent");
    private static final MessageCount messagesReceived = new MessageCount("received");
    private static final RateCount senderRate = new RateCount("sender");
    private static final RateCount receiverRate = new RateCount("receiver");

    private static final ConnectionCount senderChildCount = new ConnectionCount("sender");
    private static final ConnectionCount receiverChildCount = new ConnectionCount("receiver");

    public MaestroExporter(final String maestroUrl, final String exportUrl) throws MqttException {
        maestro = new Maestro(maestroUrl);
        maestroCollector = new MaestroCollector(maestroUrl);

        messagesSent.register();
        messagesReceived.register();
        senderRate.register();
        receiverRate.register();

        senderChildCount.register();
        receiverChildCount.register();
    }

    private void processNotes(List<MaestroNote> notes) {

        senderChildCount.reset();;
        receiverChildCount.reset();
        senderRate.reset();
        receiverRate.reset();

        for (MaestroNote note : notes) {
            if (note instanceof StatsResponse) {
                StatsResponse statsResponse = (StatsResponse) note;

                if (statsResponse.getRole().equals("sender")) {
                    messagesSent.incrementCount(statsResponse.getCount());
                    senderChildCount.incrementCount(statsResponse.getChildCount());
                    senderRate.incrementCount(statsResponse.getRate());
                }
                else {
                    if (statsResponse.getRole().equals("receiver")) {
                        messagesReceived.incrementCount(statsResponse.getCount());
                        receiverChildCount.incrementCount(statsResponse.getChildCount());
                        receiverRate.incrementCount(statsResponse.getRate());
                    }
                }
            }

            logger.trace("Note: {}", note.toString());
        }


    }

    public int run() throws MqttException, IOException {
        HTTPServer server = new HTTPServer(9200);

        while (running) {
            logger.debug("Sending requests");
            maestro.statsRequest();

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            List<MaestroNote> notes = maestro.collect();
            processNotes(notes);

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return 0;
    }
}