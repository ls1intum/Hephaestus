package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService actorService) {
        this.userService = actorService;
    }

    @GetMapping("/{login}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        Optional<UserDTO> user = userService.getUserDTO(login);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{login}/full")
    public ResponseEntity<User> getFullUser(@PathVariable String login) {
        Optional<User> user = userService.getUser(login);
        System.out.println(user);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{login}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String login) {
        Optional<UserProfileDTO> userProfile = userService.getUserProfileDTO(login);
        return userProfile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
