package zw.co.manaService.userService.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import zw.co.manaService.userService.model.dto.UserRegistrationDto;
import zw.co.manaService.userService.model.dto.UserResponseDto;
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

//    @Override
//    @Transactional
//    public UserResponseDto register(UserRegistrationDto registrationDto) {
//        if (userRepository.existsByEmail(registrationDto.getEmail())) {
//            throw new UserAlreadyExistsException("User with email " + registrationDto.getEmail() + " already exists");
//        }
//
//        // Initialize roles set
//        Set<Role> roles = new HashSet<>();
//
//        // Check for roles in the registration DTO
//        if (registrationDto.getRoles() == null || registrationDto.getRoles().length == 0) {
//            // Fetch the default CLIENT role from the database
//            Role defaultRole = roleRepository.findByName("CLIENT")
//                    .orElseThrow(() -> new RuntimeException("Default role CLIENT not found"));
//            roles.add(defaultRole);
//        } else {
//            // Fetch existing roles from the database
//            for (String roleName : registrationDto.getRoles()) {
//                Role role = roleRepository.findByName(roleName)
//                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
//                roles.add(role);
//            }
//        }
//
//        // Create the User entity
//        User user = User.builder()
//                .email(registrationDto.getEmail())
//                .password(passwordEncoder.encode(registrationDto.getPassword()))
//                .firstName(registrationDto.getFirstName())
//                .lastName(registrationDto.getLastName())
//                .phoneNumber(registrationDto.getPhoneNumber())
//                .roles(roles)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        // Save the user
//        User savedUser = userRepository.save(user);
//
//        // Publish user created event to Kafka
//        userEventProducer.publishUserCreatedEvent(
//                new UserCreatedEvent(savedUser.getId(), savedUser.getEmail(), savedUser.getFirstName(), savedUser.getLastName())
//        );
//
//        return mapUserToDto(savedUser);
//    }


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
                throw new IllegalStateException("Unexpected principal type. Expected CustomUserDetails.");
            }
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Invalid email or password");
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

//    @Override
//    public String refreshToken(String refreshToken) {
//        if (jwtTokenProvider.validateRefreshToken(refreshToken)) {
//            String email = jwtTokenProvider.getUsernameFromRefreshToken(refreshToken);
//            User user = userRepository.findByEmail(email)
//                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
//            return jwtTokenProvider.generateToken(
//                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
//            );
//        }
//        throw new IllegalArgumentException("Invalid refresh token");
//    }


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