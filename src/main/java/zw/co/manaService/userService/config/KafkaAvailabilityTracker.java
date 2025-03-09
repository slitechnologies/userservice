package zw.co.manaService.userService.config;

public class KafkaAvailabilityTracker {
    private static boolean kafkaAvailable = true;

    public static boolean isKafkaAvailable() {
        return kafkaAvailable;
    }

    public static void setKafkaAvailable(boolean available) {
        kafkaAvailable = available;
    }
}