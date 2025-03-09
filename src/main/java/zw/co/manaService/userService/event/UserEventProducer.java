package zw.co.manaService.userService.event;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import zw.co.manaService.userService.config.KafkaConfig;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        try {
            kafkaTemplate.send(KafkaConfig.USER_CREATED_TOPIC, event.getEmail(), event);
            log.info("UserCreatedEvent published for user: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish UserCreatedEvent for user: {}", event.getEmail(), e);
            // Consider implementing a retry mechanism or dead letter queue
        }
    }
}
