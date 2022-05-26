package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.List;

public class ConsumerGroupLagResult {

    private final String groupId;
    private final KafkaConsumer<?,?> consumerClient;
    private final List<DataLossWarning> warnings;

    public ConsumerGroupLagResult(String groupId, KafkaConsumer<?, ?> consumerClient, List<DataLossWarning> warnings) {
        this.groupId = groupId;
        this.consumerClient = consumerClient;
        this.warnings = warnings;
    }

    public String getGroupId() {
        return groupId;
    }

    public KafkaConsumer<?, ?> getConsumerClient() {
        return consumerClient;
    }

    public List<DataLossWarning> getWarnings() {
        return warnings;
    }
}
