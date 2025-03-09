package zw.co.manaService.userService.exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.kafka.KafkaException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import zw.co.manaService.userService.config.KafkaAvailabilityTracker;

@Configuration
public class KafkaExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(KafkaExceptionHandler.class);

    @Bean
    public ApplicationListener<ApplicationFailedEvent> applicationFailedEventListener() {
        return event -> {
            Throwable exception = event.getException();

            // Check if the exception is related to Kafka
            if (isKafkaRelatedFailure(exception)) {
                logger.warn("Kafka connection issue detected: {}. Application will continue to run with limited functionality.",
                        exception.getMessage());

                // Here you could set some application-wide flag to indicate Kafka is unavailable
                KafkaAvailabilityTracker.setKafkaAvailable(false);

                // If you want to restart the application without Kafka, you can do:
                // restartApplicationWithoutKafka(event.getApplicationContext());
            } else {
                // Log other failures
                logger.error("Application failed for non-Kafka reason:", exception);
            }
        };
    }

    private boolean isKafkaRelatedFailure(Throwable exception) {
        // Check current exception
        if (exception instanceof KafkaException ||
                exception instanceof java.util.concurrent.TimeoutException) {
            return true;
        }

        // Check cause chain
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof KafkaException ||
                    cause instanceof java.util.concurrent.TimeoutException ||
                    (cause.getMessage() != null && cause.getMessage().contains("Kafka"))) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /*
    // Optional method if you want to restart the application without Kafka
    private void restartApplicationWithoutKafka(ConfigurableApplicationContext context) {
        logger.info("Attempting to restart application with Kafka disabled...");

        // Get the original arguments used to start the application
        String[] args = SpringApplication.getStartupInfoLogger()
                               .getArgs();

        // Add property to disable Kafka
        String[] newArgs = new String[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--spring.kafka.enabled=false";

        // Close the current context
        if (context != null) {
            context.close();
        }

        // Start a new application context
        SpringApplication.run(context.getClass(), newArgs);
    }
    */
}
