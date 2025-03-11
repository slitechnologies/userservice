package zw.co.manaService.userService.service;

import zw.co.manaService.userService.model.dto.AuthResponseDto;
import zw.co.manaService.userService.model.dto.UserRegistrationDto;
import zw.co.manaService.userService.model.dto.UserResponseDto;

import java.util.List;

public interface UserService {
    UserResponseDto register(UserRegistrationDto registrationDto);
    AuthResponseDto login(String email, String password);
    UserResponseDto getUserById(Long id);
    UserResponseDto getUserByEmail(String email);
    List<UserResponseDto> getAllUsers();
    UserResponseDto updateUser(Long id, UserResponseDto userDto);
    void deleteUser(Long id);
    void verifyUser(Long id);
    void disableUser(Long id);
    void enableUser(Long id);
    String refreshToken(String refreshToken);
    UserResponseDto getCurrentUserProfile();

    // Existing methods...

    /**
     * Initiates the password reset process for a user with the given email
     * @param email User's email
     */
    void requestPasswordReset(String email);

    /**
     * Validates if a password reset token is valid and not expired
     * @param token The token to validate
     * @return true if token is valid
     */
    boolean validatePasswordResetToken(String token);

    /**
     * Resets a user's password using a valid token
     * @param token Reset token
     * @param newPassword New password
     */
    void resetPassword(String token, String newPassword);
}