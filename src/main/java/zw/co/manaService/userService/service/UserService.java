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
}