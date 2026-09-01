package com.tenxengage.app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String REDEMPTION_EVENTS_TOPIC = "redemption-events";
    public static final String RETURN_EVENTS_TOPIC = "return-events";

    @Bean
    public NewTopic redemptionEventsTopic() {
        return TopicBuilder.name(REDEMPTION_EVENTS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name("transaction-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic batchJobsTopic() {
        return TopicBuilder.name("batch-jobs")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic approvalEventsTopic() {
        return TopicBuilder.name("approval-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic completionEventsTopic() {
        return TopicBuilder.name("completion-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic trainingSyncEventsTopic() {
        return TopicBuilder.name("training-sync-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic returnEventsTopic() {
        return TopicBuilder.name(RETURN_EVENTS_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
