package zw.co.manaService.userService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.KafkaException;


@SpringBootApplication
@EnableKafka
@EnableScheduling
public class UserServiceApplication {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceApplication.class);

	public static void main(String[] args) {
		try {
			SpringApplication.run(UserServiceApplication.class, args);
		} catch (Exception e) {
			// Check if this is a Kafka-related exception
			if (isKafkaException(e)) {
				logger.error("Failed to start with Kafka. Attempting restart without Kafka...");

				// Restart application with Kafka disabled
				new SpringApplicationBuilder(UserServiceApplication.class)
						.properties("spring.kafka.enabled=false")
						.properties("spring.kafka.bootstrap-servers.auto-startup=false")
						.run(args);
			} else {
				// For other exceptions, log and rethrow
				logger.error("Application failed to start due to non-Kafka error", e);
				throw e;
			}
		}
	}

	private static boolean isKafkaException(Throwable e) {
		if (e instanceof KafkaException) {
			return true;
		}

		if (e instanceof java.util.concurrent.TimeoutException) {
			return true;
		}

		// Check for Kafka exception in cause chain
		Throwable cause = e.getCause();
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

	// Enable conditional beans based on Kafka availability
	@Bean
	public boolean kafkaEnabled() {
		try {
			Class.forName("org.apache.kafka.clients.admin.AdminClient");
			return true;
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			logger.warn("Kafka classes not found. Kafka functionality will be disabled.");
			return false;
		}
	}

//		SpringApplication.run(UserServiceApplication.class, args);

	}

