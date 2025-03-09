package zw.co.manaService.userService.config;

import lombok.Getter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
@SuppressWarnings("unused")
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    public static final String USER_CREATED_TOPIC = "user-created";
    public static final String USER_UPDATED_TOPIC = "user-updated";
    public static final String USER_DELETED_TOPIC = "user-deleted";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.admin.operation-timeout:30000}")
    private int operationTimeout;

    @Value("${spring.kafka.admin.client-timeout:30000}")
    private int clientTimeout;

    @Value("${spring.kafka.fail-on-unavailable:false}")
    private boolean failOnKafkaUnavailable;



    @Bean
    @Primary
    public KafkaAdmin kafkaAdmin(KafkaProperties properties) {
        // Create configs manually since we're having issues with the buildProperties methods
        Map<String, Object> configs = new HashMap<>();

        // Add basic connection properties
        configs.put("bootstrap.servers", bootstrapServers);

        // Add any other properties from your application.properties/yaml
        if (properties.getProperties() != null) {
            configs.putAll(properties.getProperties());
        }

        // Add your custom timeout settings
        configs.put("request.timeout.ms", operationTimeout);
        configs.put("admin.client.timeout.ms", clientTimeout);

        KafkaAdmin kafkaAdmin = new KafkaAdmin(configs);
        kafkaAdmin.setFatalIfBrokerNotAvailable(failOnKafkaUnavailable);

        // Note: Removed the setBootstrapServersExceptionHandler as it's not available
        // We'll handle exceptions via the KafkaHealthIndicator instead

        return kafkaAdmin;
    }

    @Bean(destroyMethod = "")
    public KafkaHealthIndicator kafkaHealthCheck(KafkaAdmin kafkaAdmin) {
        return new KafkaHealthIndicator(kafkaAdmin, failOnKafkaUnavailable);
    }

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name(USER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userUpdatedTopic() {
        return TopicBuilder.name(USER_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userDeletedTopic() {
        return TopicBuilder.name(USER_DELETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // This class monitors Kafka health and handles connection failures
    public static class KafkaHealthIndicator {
        private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);
        private final KafkaAdmin kafkaAdmin;
        private final boolean failOnUnavailable;
        @Getter
        private volatile boolean kafkaAvailable = false;

        public KafkaHealthIndicator(KafkaAdmin kafkaAdmin, boolean failOnUnavailable) {
            this.kafkaAdmin = kafkaAdmin;
            this.failOnUnavailable = failOnUnavailable;
            checkKafkaConnection();
        }

        private void checkKafkaConnection() {
            try {
                logger.info("Checking Kafka connection...");
                kafkaAdmin.initialize();
                kafkaAvailable = true;
                logger.info("Kafka connection successful. Topics initialized.");
            } catch (Exception e) {
                kafkaAvailable = false;
                logger.error("Failed to connect to Kafka: {}", e.getMessage());
                if (failOnUnavailable) {
                    throw new RuntimeException("Critical error: Kafka connection failed and failOnKafkaUnavailable=true", e);
                } else {
                    logger.warn("Continuing without Kafka. Messaging features will be disabled.");
                }
            }
        }

    }}