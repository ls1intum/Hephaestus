package de.tum.in.www1.hephaestus.gitprovider.user;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserProfileDTO;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService actorService) {
        this.userService = actorService;
    }

    @GetMapping("/{login}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String login) {
        Optional<UserProfileDTO> userProfile = userService.getUserProfile(login);
        return userProfile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
