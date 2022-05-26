package com.jnj.kafka.admin.lagalert;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class DataLossWarning {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String groupId;
    private final String topic;
    private final int partition;
    private final long offset;
    private final long msRemaining;
    private final LocalDateTime recordTime;
    private final ZoneId zoneId;
    private final String timeZone;

    public DataLossWarning(String groupId, String topic, int partition, long offset, long msRemaining, long recordTime) {
        this.groupId = groupId;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.msRemaining = msRemaining;

        this.zoneId = ZoneId.systemDefault();
        this.timeZone = zoneId.getDisplayName(TextStyle.SHORT, Locale.getDefault());
        this.recordTime = Instant.ofEpochMilli(recordTime).atZone(zoneId).toLocalDateTime();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTopic() {
        return topic;
    }

    public int getPartition() {
        return partition;
    }

    public long getOffset() {
        return offset;
    }

    public long getMsRemaining() {
        return msRemaining;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    @Override
    public String toString() {
        return "WARNING: consumer group \"" + groupId +
                "\" will lose data on topic partition \"" + topic + '-' + partition + "\" in " +
                ((double) msRemaining/3600000.0) + " hours (currently at offset " + offset +
                " created on " + DATE_FORMATTER.format(recordTime) + ' ' + timeZone + ')';
    }
}
