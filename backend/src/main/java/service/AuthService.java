package service;

import dto.auth.AuthResponse;
import dto.auth.LoginRequest;
import dto.auth.RegisterRequest;
import exception.UserAlreadyExistsException;
import exception.UserNotFoundException;
import model.Role;
import model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already exists");
        }
        if (userRepository.existsByPersonalIdNumber(request.personalIdNumber())) {
            throw new UserAlreadyExistsException("Personal ID number already exists");
        }
        String hashedPassword = passwordEncoder.encode(request.password());
        User user = User.builder()
                .email(request.email())
                .password(hashedPassword)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .personalIdNumber(request.personalIdNumber())
                .phoneNumber(request.phoneNumber())
                .role(Role.USER)
                .build();
        userRepository.save(user);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, "Bearer");
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UserAlreadyExistsException("Invalid email or password");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, "Bearer");
    }
}
