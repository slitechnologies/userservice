package zw.co.manaService.userService.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {

    public boolean isCurrentUser(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof zw.co.manaService.userService.model.User) {
            zw.co.manaService.userService.model.User user = (zw.co.manaService.userService.model.User) principal;
            return user.getId().equals(userId);
        }

        return false;
    }
}