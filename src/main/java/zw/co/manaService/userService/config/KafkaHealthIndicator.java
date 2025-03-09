package zw.co.manaService.userService.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KafkaHealthIndicator implements HealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationContext applicationContext;

    private boolean kafkaAvailable = true;

    @Override
    public Health health() {
        if (kafkaAvailable) {
            return Health.up().withDetail("status", "Kafka broker is available").build();
        } else {
            return Health.down()
                    .withDetail("status", "Kafka broker is unavailable")
                    .withDetail("message", "Application running with limited functionality")
                    .build();
        }
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkKafkaAvailability() {
        try {
            // Try to initialize Kafka admin - if this succeeds, Kafka is available
            kafkaAdmin.initialize();

            // If we reach here, Kafka is available
            if (!kafkaAvailable) {
                logger.info("Kafka connection restored!");
                kafkaAvailable = true;
                // Notify application that Kafka is now available
                AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
            }
        } catch (Exception e) {
            // Kafka is unavailable
            if (kafkaAvailable) {
                kafkaAvailable = false;
                logger.warn("Kafka connection lost: {}. Some features will be disabled.", e.getMessage());
                // Notify application that we're in a degraded state
                AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
            }
        }
    }

    public boolean isKafkaAvailable() {
        return kafkaAvailable;
    }
}
