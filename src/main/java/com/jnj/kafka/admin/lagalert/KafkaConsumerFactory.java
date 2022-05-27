package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class KafkaConsumerFactory {

    public KafkaConsumer<?,?> getConsumer(Properties consumerProperties) {
        return new KafkaConsumer<>(consumerProperties);
    }
}
