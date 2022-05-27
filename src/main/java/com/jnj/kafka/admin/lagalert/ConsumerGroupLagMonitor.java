package com.jnj.kafka.admin.lagalert;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class ConsumerGroupLagMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupLagMonitor.class);
    private static final String ADMIN_CLIENT_TIMEOUT_SECS = "admin.client.timeout.secs";
    private static final long ADMIN_CLIENT_TIMEOUT_SECS_DEFAULT = 15;
    private static final String CONSUMER_THREADS = "consumer.threads";
    private static final int CONSUMER_THREADS_DEFAULT = 10;
    private static final String WARNINGS_FILEPATH = "warnings.filepath";

    private ResultsProcessor resultsProcessor;
    private final ExecutorService executorService;
    private final AdminClient adminClient;
    private final ConsumerGroupLagCheckerFactory checkerFactory;
    private final long adminClientTimeout;
    private final String warningsFilepath;
    private final ConsumerGroupFilter consumerGroupFilter;
    private final BlockingQueue<KafkaConsumer<?, ?>> availableConsumers;
    private final List<DataLossWarning> warnings = new ArrayList<>();

    @Autowired
    public ConsumerGroupLagMonitor(ExecutorService executorService,
                                   AdminClient adminClient,
                                   ConsumerGroupFilter consumerGroupFilter,
                                   KafkaConsumerFactory consumerFactory,
                                   ConsumerGroupLagCheckerFactory checkerFactory,
                                   Properties appProperties,
                                   Properties consumerProperties) {

        this.executorService = executorService;
        this.adminClient = adminClient;
        this.checkerFactory = checkerFactory;
        this.adminClientTimeout = appProperties.getProperty(ADMIN_CLIENT_TIMEOUT_SECS) != null ?
                Long.parseLong(appProperties.getProperty(ADMIN_CLIENT_TIMEOUT_SECS)) :
                ADMIN_CLIENT_TIMEOUT_SECS_DEFAULT;
        this.consumerGroupFilter = consumerGroupFilter;
        this.warningsFilepath = appProperties.getProperty(WARNINGS_FILEPATH);

        int consumerThreads = appProperties.getProperty(CONSUMER_THREADS) != null ?
                Integer.parseInt(appProperties.getProperty(CONSUMER_THREADS)) : CONSUMER_THREADS_DEFAULT;
        this.availableConsumers = new ArrayBlockingQueue<>(consumerThreads);
        while(availableConsumers.size() < consumerThreads) {
            availableConsumers.add(consumerFactory.getConsumer(consumerProperties));
        }
    }

    @PostConstruct
    public void start() throws ConsumerLagMonitorException {

        log.info("Retrieving consumer groups");
        List<ConsumerGroupListing> consumerGroups = getConsumerGroups();
        log.info("{} consumer groups retrieved", consumerGroups.size());

        List<Future<ConsumerGroupLagResult>> futureResults = new ArrayList<>();
        // start results processor in a separate thread
        resultsProcessor = new ResultsProcessor(futureResults, availableConsumers, consumerGroups.size());
        new Thread(resultsProcessor).start();

        // for each consumer group get a consumer client and submit a consumer group lag checker process to the executor
        // service
        log.info("Creating consumer group lag checker processes for consumer groups");
        for (ConsumerGroupListing group : consumerGroups) {
            // waits until a consumer client is available
            KafkaConsumer<?, ?> consumerClient = availableConsumers.poll();
            ConsumerGroupLagChecker consumerGroupChecker = checkerFactory.getConsumerGroupLagChecker(group.groupId(), consumerClient);
            Future<ConsumerGroupLagResult> futureResult = executorService.submit(consumerGroupChecker);
            synchronized (futureResults) {
                futureResults.add(futureResult);
                futureResults.notifyAll();
            }
        }
        log.info("Finished creating consumer group lag checker processes for consumer groups");

        log.info("All consumer group lag checker processes launched, waiting for completion");
        try {
            synchronized (resultsProcessor) {
                if (resultsProcessor.isRunning()) {
                    resultsProcessor.wait();
                }
            }
        } catch (InterruptedException ignored) {}

        log.info("All consumer group lag checker processes finished");
        if (!resultsProcessor.getResults().isEmpty()) {
            log.warn("Found {} warnings", resultsProcessor.getResults().size());
            for (DataLossWarning warning : resultsProcessor.getResults()) {
                log.warn(warning.toString());
                warnings.add(warning);
            }

            // write warnings to filepath if specified in application properties
            try {
                writeWarningsToFile();
            } catch (IOException e) {
                throw new ConsumerLagMonitorException(format("Error writing warnings to file \"%s\"", warningsFilepath), e);
            }
        }
    }

    public List<DataLossWarning> getWarnings() {
        return warnings;
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down consumer lag monitor");
        executorService.shutdown();
        resultsProcessor.stop();
    }

    private List<ConsumerGroupListing> getConsumerGroups() throws ConsumerLagMonitorException {

        // get consumer groups
        final ListConsumerGroupsResult listResult = adminClient.listConsumerGroups();
        List<ConsumerGroupListing> consumerGroups;
        try {

            Collection<ConsumerGroupListing> listResults = listResult.all().get(adminClientTimeout, TimeUnit.SECONDS);
            consumerGroups = listResults.stream()
                    .filter(g -> consumerGroupFilter.filter(g.groupId()))
                    .collect(Collectors.toList());
            consumerGroups.sort(Comparator.comparing(ConsumerGroupListing::groupId));

        } catch (Exception e) {
            log.error("Error retrieving consumer groups", e);
            throw new ConsumerLagMonitorException("Error retrieving consumer groups", e);
        }
        return consumerGroups;
    }

    private void writeWarningsToFile() throws IOException {
        if (warningsFilepath != null) {

            final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            PrintWriter printWriter = null;
            try {
                String filename = warningsFilepath + "." + dateFormatter.format(LocalDateTime.now()) + ".log";
                printWriter = new PrintWriter(new FileWriter(filename));
                for (DataLossWarning warning : warnings) {
                    printWriter.println(warning.toString());
                }
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }
            }
        }
    }

    private static class ResultsProcessor implements Runnable {

        private final BlockingQueue<KafkaConsumer<?, ?>> availableConsumers;
        private final List<Future<ConsumerGroupLagResult>> futureResults;
        private final int expectedResults;
        private final List<DataLossWarning> warnings = new ArrayList<>();
        private int count;
        private boolean running;

        public ResultsProcessor(List<Future<ConsumerGroupLagResult>> futureResults,
                                BlockingQueue<KafkaConsumer<?, ?>> availableConsumers,
                                int expectedResults) {
            this.futureResults = futureResults;
            this.availableConsumers = availableConsumers;
            this.expectedResults = expectedResults;
        }

        public List<DataLossWarning> getResults() {
            return warnings;
        }

        @Override
        public void run() {

            running = true;
            log.info("Started consumer lag monitor results processor");
            while (count < expectedResults && running) {

                log.debug("{} consumer lag monitor results received, {} remaining to collect", count, (expectedResults - count));
                synchronized (futureResults) {
                    if (futureResults.isEmpty()) {
                        try {
                            futureResults.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                synchronized (futureResults) {

                    ListIterator<Future<ConsumerGroupLagResult>> futureResultsIter = futureResults.listIterator();
                    while (futureResultsIter.hasNext()) {
                        try {
                            Future<ConsumerGroupLagResult> futureResult = futureResultsIter.next();
                            ConsumerGroupLagResult result = futureResult.get(10, TimeUnit.SECONDS);
                            if (result != null) {
                                log.debug("{}: Found consumer group lag result with {} warnings", result.getGroupId(),
                                        result.getWarnings().size());
                                availableConsumers.add(result.getConsumerClient());
                                warnings.addAll(result.getWarnings());
                                futureResultsIter.remove();
                                count++;
                            }
                        } catch (InterruptedException | TimeoutException ignored) {
                        } catch (ExecutionException e) {
                            log.error("Error in consumer group lag checker", e);
                        }
                    }
                }
            }

            log.info("Stopped consumer lag monitor results processor");
            synchronized (this) {
                this.notifyAll();
                running = false;
            }
        }

        public boolean isRunning() {
            return running;
        }

        public void stop() {
            running = false;
        }
    }
}
