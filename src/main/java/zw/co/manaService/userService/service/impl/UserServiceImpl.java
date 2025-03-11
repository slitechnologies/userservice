package zw.co.manaService.userService.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.co.manaService.userService.event.UserCreatedEvent;
import zw.co.manaService.userService.event.UserEventProducer;
import zw.co.manaService.userService.exception.*;
import zw.co.manaService.userService.model.Role;
import zw.co.manaService.userService.model.User;
import zw.co.manaService.userService.model.dto.AuthResponseDto;
import zw.co.manaService.userService.model.dto.PasswordResetToken;
import zw.co.manaService.userService.model.dto.UserRegistrationDto;
import zw.co.manaService.userService.model.dto.UserResponseDto;
import zw.co.manaService.userService.repository.PasswordResetTokenRepository;
import zw.co.manaService.userService.repository.RoleRepository;
import zw.co.manaService.userService.repository.UserRepository;
import zw.co.manaService.userService.security.CustomUserDetails;
import zw.co.manaService.userService.security.JwtTokenProvider;
import zw.co.manaService.userService.service.UserService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("unused")
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserEventProducer userEventProducer;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender emailSender;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;


    private final UserDetailsService userDetailsService;


    @Override
    @Transactional
    public UserResponseDto register(UserRegistrationDto registrationDto) {
        // Validate email uniqueness
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + registrationDto.getEmail() + " already exists");
        }

        // Initialize roles set
        Set<Role> roles = new HashSet<>();

        try {
            // Check for roles in the registration DTO
            if (registrationDto.getRoles() == null || registrationDto.getRoles().length == 0) {
                // Fetch the default CLIENT role from the database
                Role defaultRole = roleRepository.findByName("CLIENT")
                        .orElseThrow(() -> new RoleNotFoundException("Default role CLIENT not found. System configuration issue."));
                roles.add(defaultRole);
            } else {
                // Validate all roles exist before assigning them
                List<String> invalidRoles = new ArrayList<>();

                for (String roleName : registrationDto.getRoles()) {
                    Optional<Role> roleOpt = roleRepository.findByName(roleName);
                    if (roleOpt.isPresent()) {
                        roles.add(roleOpt.get());
                    } else {
                        invalidRoles.add(roleName);
                    }
                }

                if (!invalidRoles.isEmpty()) {
                    throw new RoleNotFoundException("The following roles do not exist in the system: " +
                            String.join(", ", invalidRoles) + ". Valid roles must be provided for registration.");
                }

                if (roles.isEmpty()) {
                    Role defaultRole = roleRepository.findByName("CLIENT")
                            .orElseThrow(() -> new RoleNotFoundException("Default role CLIENT not found. System configuration issue."));
                    roles.add(defaultRole);
                }
            }

            // Create the User entity
            User user = User.builder()
                    .email(registrationDto.getEmail())
                    .password(passwordEncoder.encode(registrationDto.getPassword()))
                    .firstName(registrationDto.getFirstName())
                    .lastName(registrationDto.getLastName())
                    .phoneNumber(registrationDto.getPhoneNumber())
                    .roles(roles)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Save the user
            User savedUser = userRepository.save(user);

            // Publish user created event to Kafka
            try {
                userEventProducer.publishUserCreatedEvent(
                        new UserCreatedEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getFirstName(), savedUser.getLastName())
                );
            } catch (Exception e) {
                // Log the error but don't fail the registration if Kafka is unavailable
                log.error("Failed to publish user created event to Kafka: {}", e.getMessage(), e);
            }

            return mapUserToDto(savedUser);

        } catch (RoleNotFoundException e) {
            // Rethrow custom exceptions for proper error handling by the global exception handler
            throw e;
        } catch (Exception e) {
            // Catch any other unexpected exceptions and wrap them
            log.error("Unexpected error during user registration: {}", e.getMessage(), e);
            throw new RegistrationFailedException("Failed to register user due to an internal error: " + e.getMessage(), e);
        }
    }

    @Override
    public AuthResponseDto login(String email, String password) {
        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // Set the authentication in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Extract CustomUserDetails from the Authentication object
            Object principal = authentication.getPrincipal();
            if (principal instanceof CustomUserDetails customUserDetails) {
                // Get the User entity from CustomUserDetails
                User user = customUserDetails.getUser();

                // Generate tokens
                String token = jwtTokenProvider.generateToken(authentication);
                String refreshToken = jwtTokenProvider.generateRefreshToken(user);

                // Return the AuthResponseDto
                return AuthResponseDto.builder()
                        .token(token)
                        .refreshToken(refreshToken)
                        .user(mapUserToDto(user))
                        .build();
            } else {
                throw new AuthenticationException("Authentication failed: Unexpected principal type");
            }
        } catch (BadCredentialsException e) {
            // More specific exception with better message
            throw new AuthenticationException("Invalid credentials: The email or password you entered is incorrect");
        } catch (DisabledException e) {
            throw new AuthenticationException("Account is disabled: Your account has been deactivated");
        } catch (LockedException e) {
            throw new AuthenticationException("Account is locked: Too many failed login attempts");
        } catch (AuthenticationServiceException e) {
            throw new AuthenticationException("Authentication service error: " + e.getMessage());
        } catch (Exception e) {
            // Log the unexpected error for debugging
            log.error("Unexpected error during login", e);
            throw new AuthenticationException("Login failed: An unexpected error occurred");
        }
    }

    @Override
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapUserToDto(user);
    }

    @Override
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapUserToDto(user);
    }

    @Override
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapUserToDto).toList();
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(Long id, UserResponseDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPhoneNumber(userDto.getPhoneNumber());

        User updatedUser = userRepository.save(user);
        return mapUserToDto(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void verifyUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setVerified(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void enableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        String email = jwtTokenProvider.getUsernameFromRefreshToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }


    @Override
    public UserResponseDto getCurrentUserProfile() {
        // Get authentication details from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Validate authentication
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedAccessException("User is not authenticated");
        }

        // Retrieve the username (email in your case) from the authentication principal
        String username = authentication.getName();

        // Use the existing loadUserByUsername method from UserDetailsService
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Safely cast and retrieve the User entity
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            User user = customUserDetails.getUser();
            return mapUserToDto(user);
        }

        // If execution reaches here, it means an unexpected issue occurred
        throw new IllegalStateException("Unexpected principal type. Expected CustomUserDetails.");
    }


    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Processing password reset request for email: {}", email);

        // Security best practice: Don't reveal if email exists
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                // Check if an existing token exists for the user
                Optional<PasswordResetToken> existingToken = tokenRepository.findByUser(user);

                if (existingToken.isPresent()) {
                    // Update the existing token
                    PasswordResetToken token = existingToken.get();
                    token.setToken(UUID.randomUUID().toString());
                    token.setExpiryDate(LocalDateTime.now().plusHours(24)); // 24 hours expiry
                    tokenRepository.save(token);
                    log.info("Updated password reset token for user: {}", user.getEmail());
                } else {
                    // Create a new token
                    String tokenValue = UUID.randomUUID().toString();
                    PasswordResetToken newToken = PasswordResetToken.builder()
                            .token(tokenValue)
                            .user(user)
                            .expiryDate(LocalDateTime.now().plusHours(24)) // 24 hours expiry
                            .build();
                    tokenRepository.save(newToken);
                    log.info("Created new password reset token for user: {}", user.getEmail());
                }

                // Send email with reset link
                sendPasswordResetEmail(user.getEmail(), existingToken.isPresent() ? existingToken.get().getToken() : UUID.randomUUID().toString());

            } catch (Exception e) {
                log.error("Error processing password reset for user: {}", user.getEmail(), e);
                throw new RuntimeException("Failed to process password reset request. Please try again later.");
            }
        });
    }


    @Override
    public boolean validatePasswordResetToken(String token) {
        return tokenRepository.findByToken(token)
                .map(resetToken -> !resetToken.isExpired())
                .orElse(false);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired password reset token"));

        // Check if token is expired
        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new InvalidTokenException("Password reset token has expired");
        }

        // Get user and update password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Delete used token
        tokenRepository.delete(resetToken);

        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    private void sendPasswordResetEmail(String email, String token) {
        try {
            SimpleMailMessage message = getSimpleMailMessage(email, token);

            emailSender.send(message);

            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage(), e);
            // Continue execution - don't expose email sending failure to client
        }
    }

    private SimpleMailMessage getSimpleMailMessage(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, please click on the link below:\n\n" +
                frontendUrl + "/reset-password?token=" + token +
                "\n\nThis link will expire in 24 hours.\n\n" +
                "If you did not request a password reset, please ignore this email and your password will remain unchanged.");
        return message;
    }


    private UserResponseDto mapUserToDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .verified(user.isVerified())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}