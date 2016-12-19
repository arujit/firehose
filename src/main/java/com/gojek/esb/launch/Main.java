package com.gojek.esb.launch;

import com.gojek.esb.consumer.LogConsumer;
import com.gojek.esb.consumer.StreamingClient;
import com.gojek.esb.factory.FactoryUtils;
import com.gojek.esb.factory.LogConsumerFactory;
import com.gojek.esb.factory.StreamingClientFactory;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        if (!FactoryUtils.appConfig.isStreaming()) {
            LogConsumer logConsumer = LogConsumerFactory.getLogConsumer();

            while (true) {
                logConsumer.processPartitions();
            }
        } else {
            StreamingClient streamingClient = StreamingClientFactory.getStreamingClient();
            streamingClient.start();
        }
    }
}
