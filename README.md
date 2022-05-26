# kafka-consumer-lag-alert
To run the script make sure that you have the compiled jar file "kafka-consumer-lag-alert-0.0.1.jar" in the same
directory as the shell script "kafka-consumer-lag-alert.sh". In that same directory create a "conf" directory and add
an "application-<env>.yaml" to that directory, where <env> is an arbitrary name you want to give the Kafka environment
you are capturing consumer group lag data from. See the example YAML file, "application-dev.yaml" in this project's
"conf" folder for an example of how to setup the file. In the YAML file, all properties under "admin" are Kafka Admin 
client properties as defined
[here](https://docs.confluent.io/platform/current/installation/configuration/admin-configs.html). Also, all properties 
under "consumer" are Kafka consumer properties as defined
[here](https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html). Finally, all 
properties under "app" are the application-specific properties that can be used to control the functionality and 
performance of this application. The properties under this section include:

```
"admin.client.timeout.secs"
```
  * The number of seconds the Kafka Admin client should wait before timing out while executing commands.
  * type: int
  * default: 15
```
"consumer.poll.timeout.secs"
```
  * The number of seconds the Kafka Consumer client should wait before timing out while polling for new records.
  * type: int
  * default: 15
```
"consumer.threads"
```
  * The number of concurrent consumer clients to use for processing.
  * type: int
  * default: 10
```
"expiry.alert.offset.hours"
```
  * The number of hours before before topic data will expire where a warning should be sent. For example, if a topic has
  a retention period of 24 hours and this value is set to 4 hours, if a record is produced to the topic but not consumed
  by a consumer group for 20 hours, an alert will be sent. It would not be sent if the unconsumed message had only been
  on the topic for 19 hours.
  * type: int
  * default: 24
```
"default.topic.retention.ms"
```
  * The value of the property "log.retention.hours" (see 
  [this](https://docs.confluent.io/platform/current/installation/configuration/broker-configs.html#brokerconfigs_log.retention.hours) 
  documentation) on the brokers in the Kafka cluster, or the default log retention time on a topic if there is no topic 
  level configutaration of "retention.ms" (see 
  [this](https://docs.confluent.io/platform/current/installation/configuration/topic-configs.html#topicconfigs_retention.ms) 
  documentation). 
  * type: long
  * default: 604800000 (7 days)
```
"included.consumer.groups"
```
  * A comma-delimited list of consumer groups to include in the consumer group lag alert. If this property is not specified
  or blank, the default behavior is to retrieve all consumer groups except those specified in the 
  "excluded.consumer.groups" property.
  * type: string (comma-delimited values)
  * default: n/a
```
"excluded.consumer.groups"
```
  * A comma-delimited list of consumer groups to exclude in the consumer group lag alert. If this property is not specified
  or blank, the default behavior is to retrieve all consumer groups unless the "included.consumer.groups" property is set.
  * type: string (comma-delimited values)
  * default: n/a

When running the script you specify the options like show below:

```
kafka-consumer-lag-alert.sh --env dev

    env: the name of the environment (conf/application-<env>.yaml) to load
```