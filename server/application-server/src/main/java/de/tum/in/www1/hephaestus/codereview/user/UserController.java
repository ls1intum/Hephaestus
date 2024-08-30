package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.errors.EntityNotFoundException;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService actorService) {
        this.userService = actorService;
    }

    @GetMapping("/{login}")
    public UserDTO getUser(@PathVariable String login) {
        Optional<UserDTO> user = userService.getUserDTO(login);
        if (user.isEmpty()) {
            throw new EntityNotFoundException("Actor with login " + login + " not found!");
        }
        return user.get();
    }

}
