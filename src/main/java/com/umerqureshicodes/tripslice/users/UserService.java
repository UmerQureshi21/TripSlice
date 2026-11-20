package com.umerqureshicodes.tripslice.users;

import com.umerqureshicodes.tripslice.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepo userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDTO createUser(UserRequestDTO userRequestDTO) {
        // One message for both cases, so sign up says as little as possible about who has an account
        if (userRepo.findByUsername(userRequestDTO.username()).isPresent()
                || userRepo.findByDisplayedName(userRequestDTO.displayedName()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "That email or display name is already in use.");
        }
        AppUser user = userRepo.save(new AppUser(
                userRequestDTO.username(),
                passwordEncoder.encode(userRequestDTO.password()),
                userRequestDTO.displayedName()
        ));
        return new UserResponseDTO(user.getUsername(), user.getDisplayedName());
    }

    public UserResponseDTO getUser(String email) {
        Optional<AppUser> user = userRepo.findByUsername(email);
        return user.map(u -> new UserResponseDTO(u.getUsername(), u.getDisplayedName())).orElse(null);
    }

    @Override
    public AppUser loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
