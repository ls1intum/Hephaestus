package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.errors.EntityNotFoundException;

@RestController
@RequestMapping("/ghuser")
public class GHUserController {
    private final GHUserService ghUserService;

    public GHUserController(GHUserService actorService) {
        this.ghUserService = actorService;
    }

    @GetMapping("/{login}")
    public GHUserDTO getUser(@PathVariable String login) {
        Optional<GHUserDTO> user = ghUserService.getGHUserDTO(login);
        if (user.isEmpty()) {
            throw new EntityNotFoundException("Actor with login " + login + " not found!");
        }
        return user.get();
    }

}
