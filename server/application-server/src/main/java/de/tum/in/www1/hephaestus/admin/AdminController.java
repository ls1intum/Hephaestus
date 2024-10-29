package de.tum.in.www1.hephaestus.admin;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public String admin() {
        return "Welcome to the admin page!";
    }

    @GetMapping("/me")
    public AuthUserInfoDTO getGretting(JwtAuthenticationToken auth) {
        return new AuthUserInfoDTO(
            auth.getToken().getClaimAsString(StandardClaimNames.PREFERRED_USERNAME),
            auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    public static record AuthUserInfoDTO(String name, List<String> roles) {
    }
}
