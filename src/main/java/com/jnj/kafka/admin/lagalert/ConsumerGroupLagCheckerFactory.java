package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class ConsumerGroupLagCheckerFactory {

    private AdminClient adminClient;
    private Properties appProperties;

    @Autowired
    public ConsumerGroupLagCheckerFactory(AdminClient adminClient,
                                          Properties appProperties) {
        this.adminClient = adminClient;
        this.appProperties = appProperties;
    }

    public ConsumerGroupLagChecker getConsumerGroupLagChecker(String groupId, KafkaConsumer<?,?> consumerClient) {
        return new ConsumerGroupLagChecker(groupId, adminClient, consumerClient, appProperties);
    }
}
