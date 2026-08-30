package de.tum.cit.aet.hephaestus.core.auth.consent;

import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnServerRole
@RestController
@RequestMapping("/user/consent")
@Tag(name = "Consent", description = "Current account transparency and research consent")
@PreAuthorize("isAuthenticated()")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get the current consent notice and status", operationId = "getConsentStatus")
    public ResponseEntity<ConsentService.ConsentStatusDTO> status() {
        return ResponseEntity.ok(service.status(CurrentAccount.requireId()));
    }

    @PutMapping
    @Operation(summary = "Complete the first-login transparency step", operationId = "completeFirstLoginConsent")
    public ResponseEntity<ConsentService.ConsentStatusDTO> complete(
            @Valid @RequestBody ConsentService.FirstLoginConsentDTO request) {
        return ResponseEntity.ok(service.completeFirstLogin(CurrentAccount.requireId(), request));
    }

    @PutMapping("/research")
    @Operation(summary = "Grant or withdraw research consent", operationId = "updateResearchConsent")
    public ResponseEntity<ConsentService.ConsentStatusDTO> research(
            @Valid @RequestBody ConsentService.ResearchConsentDTO request) {
        return ResponseEntity.ok(service.setResearchParticipation(CurrentAccount.requireId(), request));
    }
}
