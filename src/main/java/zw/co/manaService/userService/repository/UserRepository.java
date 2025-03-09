package zw.co.manaService.userService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.co.manaService.userService.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}