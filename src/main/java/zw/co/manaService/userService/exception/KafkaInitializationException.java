package zw.co.manaService.userService.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class KafkaInitializationException extends RuntimeException {
    public KafkaInitializationException(String message) {
        super(message);
    }
}