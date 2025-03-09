package zw.co.manaService.userService.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import zw.co.manaService.userService.config.KafkaConfig;
import zw.co.manaService.userService.config.KafkaConfig.KafkaHealthIndicator;
import zw.co.manaService.userService.event.UserCreatedEvent;


import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class KafkaMessageService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageService.class);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaHealthIndicator kafkaHealthIndicator;

    /**
     * Sends a message to Kafka topic with fallback behavior if Kafka is unavailable
     */
    public <T> void sendMessage(String topic, String key, T data, Consumer<T> fallbackAction) {
        // First check if Kafka is available
        if (!kafkaHealthIndicator.isKafkaAvailable()) {
            logger.warn("Kafka is unavailable. Executing fallback action for message: {}", key);
            if (fallbackAction != null) {
                fallbackAction.accept(data);
            }
            return;
        }

        try {
            // Attempt to send message to Kafka
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, data);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    // Handle send failure
                    logger.error("Unable to send message to topic {}: {}", topic, ex.getMessage());
                    if (fallbackAction != null) {
                        fallbackAction.accept(data);
                    }
                } else {
                    logger.info("Message sent successfully to topic {}: {}", topic, key);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to send message to Kafka: {}", e.getMessage());
            if (fallbackAction != null) {
                fallbackAction.accept(data);
            }
        }
    }

    // Example method for user events
    public void publishUserCreated(String userId, UserCreatedEvent event) {
        // Example fallback is to save to a local database
        sendMessage(KafkaConfig.USER_CREATED_TOPIC, userId, event,
                userData -> {
                    logger.info("Fallback: Storing user created event locally for user {}", userId);
                    // Here you would implement local storage/database fallback
                });
    }
}