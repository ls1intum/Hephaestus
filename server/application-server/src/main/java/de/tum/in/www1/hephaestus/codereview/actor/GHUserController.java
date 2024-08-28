package de.tum.in.www1.hephaestus.codereview.actor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ghuser")
public class GHUserController {
    private final GHUserService actorService;

    public GHUserController(GHUserService actorService) {
        this.actorService = actorService;
    }

    @GetMapping("/{login}")
    public GHUser getActor(@PathVariable String login) {
        return actorService.getActor(login);
    }

}
