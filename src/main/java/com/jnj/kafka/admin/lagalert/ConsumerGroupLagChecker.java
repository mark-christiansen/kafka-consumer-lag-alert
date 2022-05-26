package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class ConsumerGroupLagChecker implements Callable<ConsumerGroupLagResult> {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupLagChecker.class);

    private static final String DEFAULT_TOPIC_RETENTION_MS = "default.topic.retention.ms";
    private static final long DEFAULT_TOPIC_RETENTION_MS_DEFAULT = 604800000;
    private static final String ADMIN_CLIENT_TIMEOUT_SECS = "admin.client.timeout.secs";
    private static final long ADMIN_CLIENT_TIMEOUT_SECS_DEFAULT = 15;
    private static final String CONSUMER_POLL_TIMEOUT_SECS = "consumer.poll.timeout.secs";
    private static final long CONSUMER_POLL_TIMEOUT_SECS_DEFAULT = 15;
    private static final String EXPIRY_ALERT_OFFSET_HOURS = "expiry.alert.offset.hours";
    private static final long EXPIRY_ALERT_OFFSET_MS_DEFAULT = 24 * 3600000;
    private static final String TOPIC_RETENTION = "retention.ms";

    private final String groupId;
    private final AdminClient adminClient;
    private final KafkaConsumer<?,?> consumerClient;
    private final long defaultTopicRetentionMs;
    private final long adminClientTimeout;
    private final long consumerPollTimeout;
    private final long expiryAlertOffsetMs;

    public ConsumerGroupLagChecker(String groupId,
                                   AdminClient adminClient,
                                   KafkaConsumer<?, ?> consumerClient,
                                   Properties adminProperties) {

        this.groupId = groupId;
        this.adminClient = adminClient;
        this.consumerClient = consumerClient;
        this.defaultTopicRetentionMs = adminProperties.get(DEFAULT_TOPIC_RETENTION_MS) != null ?
                Long.parseLong(adminProperties.getProperty(DEFAULT_TOPIC_RETENTION_MS)) :
                DEFAULT_TOPIC_RETENTION_MS_DEFAULT;
        this.adminClientTimeout = adminProperties.get(ADMIN_CLIENT_TIMEOUT_SECS) != null ?
                Long.parseLong(adminProperties.getProperty(ADMIN_CLIENT_TIMEOUT_SECS)) :
                ADMIN_CLIENT_TIMEOUT_SECS_DEFAULT;
        this.consumerPollTimeout = adminProperties.get(CONSUMER_POLL_TIMEOUT_SECS) != null ?
                Long.parseLong(adminProperties.getProperty(CONSUMER_POLL_TIMEOUT_SECS)) :
                CONSUMER_POLL_TIMEOUT_SECS_DEFAULT;
        this.expiryAlertOffsetMs = adminProperties.get(EXPIRY_ALERT_OFFSET_HOURS) != null ?
                Long.parseLong(adminProperties.getProperty(EXPIRY_ALERT_OFFSET_HOURS))  * 3600000 :
                EXPIRY_ALERT_OFFSET_MS_DEFAULT;
    }

    @Override
    public ConsumerGroupLagResult call() throws ConsumerLagMonitorException {

        log.debug("{}: Retrieving consumer group offsets", groupId);
        Map<TopicPartition, Long> consumerGroupOffsets;
        try {
            consumerGroupOffsets = getConsumerGroupOffsets();
        } catch (Exception e) {
            String message = format("Error getting consumer group offsets for group %s", groupId);
            log.error(message, e);
            throw new ConsumerLagMonitorException(message, e);
        }
        log.debug("{}: {} consumer groups offsets retrieved", groupId, consumerGroupOffsets.size());

        log.debug("{}: Retrieving consumer group topic configurations", groupId);
        Set<ConfigResource> topics = consumerGroupOffsets.keySet()
                .stream()
                .map(p -> new ConfigResource(ConfigResource.Type.TOPIC, p.topic()))
                .collect(Collectors.toSet());
        Map<String, Long> topicRetentions = getTopicRetentions(topics);
        log.debug("{}: {} topic configurations retrieved", groupId, topics.size());

        log.debug("{}: Checking topic data expiry at consumer group offsets", groupId);
        List<DataLossWarning> warnings = getDataExpiryWarnings(consumerGroupOffsets, topicRetentions);
        return new ConsumerGroupLagResult(groupId, consumerClient, warnings);
    }

    private Map<TopicPartition, Long> getConsumerGroupOffsets() throws ConsumerLagMonitorException {

        ListConsumerGroupOffsetsResult info = adminClient.listConsumerGroupOffsets(groupId);
        Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap;
        try {
            topicPartitionOffsetAndMetadataMap = info.partitionsToOffsetAndMetadata().get();
        } catch (Exception e) {
            String message = format("Error retrieving consumer group offsets for consumer group %s", groupId);
            log.error(message, e);
            throw new ConsumerLagMonitorException(message, e);
        }

        Map<TopicPartition, Long> groupOffset = new HashMap<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : topicPartitionOffsetAndMetadataMap.entrySet()) {
            TopicPartition key = entry.getKey();
            OffsetAndMetadata metadata = entry.getValue();
            groupOffset.putIfAbsent(new TopicPartition(key.topic(), key.partition()), metadata.offset());
        }
        return groupOffset;
    }

    private Map<String, Long> getTopicRetentions(Set<ConfigResource> topics) {

        Map<String, Long> topicRetentions = topics.stream().collect(Collectors.toMap(ConfigResource::name, v -> defaultTopicRetentionMs));

        // get the expiry
        DescribeConfigsResult topicsResult = adminClient.describeConfigs(topics);
        Map<ConfigResource, KafkaFuture<Config>> configsByTopic = topicsResult.values();
        String topicName = "";
        try {
            for(Map.Entry<ConfigResource, KafkaFuture<org.apache.kafka.clients.admin.Config>> entry : configsByTopic.entrySet()) {
                topicName = entry.getKey().name();
                org.apache.kafka.clients.admin.Config topicConfigs = entry.getValue().get(adminClientTimeout, TimeUnit.SECONDS);
                if (topicConfigs != null) {
                    ConfigEntry topicRetentionValue = topicConfigs.get(TOPIC_RETENTION);
                    if (topicRetentionValue != null) {
                        topicRetentions.put(topicName, Long.parseLong(topicRetentionValue.value()));
                    }
                }
            }
        } catch (Exception e) {
            log.error(format("Error getting topic description for topic %s and consumer group %s", topicName, groupId), e);
        }

        return topicRetentions;
    }

    private List<DataLossWarning> getDataExpiryWarnings(Map<TopicPartition, Long> consumerGroupOffsets,
                                                        Map<String, Long> topicRetentions) {

        List<DataLossWarning> warnings = new ArrayList<>();

        // assign partitions to consumer and seek to the current offsets of the consumer group
        consumerClient.assign(consumerGroupOffsets.keySet());
        consumerGroupOffsets.forEach(consumerClient::seek);

        // retrieve at least one record from each partition from the current offset set above
        log.debug("{}: Retrieve record at offset for each topic partition", groupId);
        Map<TopicPartition, ConsumerRecord<?, ?>> partitionRecords = new HashMap<>();
        while(partitionRecords.size() < consumerGroupOffsets.size()) {
            ConsumerRecords<?, ?> records = consumerClient.poll(Duration.ofSeconds(consumerPollTimeout));
            if (records.count() == 0) {
                break;
            }
            log.debug("{}: {} records consumed from topic partitions", groupId, records.count());
            Iterator<? extends ConsumerRecord<?, ?>> iter = records.iterator();
            iter.forEachRemaining(r -> partitionRecords.putIfAbsent(new TopicPartition(r.topic(), r.partition()), r));
        }

        // detect if the record at the consumer group's current offset has a create timestamp that is old enough to
        // be eligible to warn for topic data expiration and potential consumer group data loss
        log.debug("{}: Evaluate if the consumer group is in danger of missing expired topic partition data", groupId);
        for (ConsumerRecord<?, ?> record : partitionRecords.values()) {

            long retentionMs = topicRetentions.get(record.topic());
            long messageLifeMs = System.currentTimeMillis() - record.timestamp();

            if (messageLifeMs >= (retentionMs - expiryAlertOffsetMs)) {
                DataLossWarning warning = new DataLossWarning(groupId, record.topic(), record.partition(), record.offset(),
                        retentionMs - messageLifeMs, record.timestamp());
                warnings.add(warning);
                log.warn(warning.toString());
            }
        }

        if (!warnings.isEmpty()) {
            log.warn("{}: Found {} missing expired topic partition data warnings", groupId, warnings.size());
        } else {
            log.debug("{}: No missing expired topic partition data warnings", groupId);
        }

        return warnings;
    }
}
