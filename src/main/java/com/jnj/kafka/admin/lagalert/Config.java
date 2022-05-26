package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class Config {

    private static final String CONSUMER_THREADS = "consumer.threads";
    private static final int CONSUMER_THREADS_DEFAULT = 10;

    @Bean
    @ConfigurationProperties(prefix = "app")
    public Properties appProperties() {
        return new Properties();
    }

    @Bean
    @ConfigurationProperties(prefix = "admin")
    public Properties adminProperties() {
        return new Properties();
    }

    @Bean
    @ConfigurationProperties(prefix = "consumer")
    public Properties consumerProperties() {
        return new Properties();
    }

    @Bean
    public AdminClient adminClient() {
        // The Kafka Admin client is thread-safe and therefore can be used by multiple threads concurrently
        return AdminClient.create(adminProperties());
    }

    @Bean
    public ExecutorService executorService() {
        Properties props = appProperties();
        int threads = props.getProperty(CONSUMER_THREADS) != null ?
                Integer.parseInt(props.getProperty(CONSUMER_THREADS)) : CONSUMER_THREADS_DEFAULT;
        return Executors.newFixedThreadPool(threads);
    }
}