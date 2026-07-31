package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDetailDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedPracticeStatusRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/practice-catalog")
@PreAuthorize("hasAuthority('app_admin')")
@ConditionalOnServerRole
@Tag(name = "Admin Practice Catalog", description = "Instance-managed curated practice definitions")
@RequiredArgsConstructor
@Validated
public class CuratedPracticeAdminController {

    private final CuratedPracticeService service;

    @GetMapping("/areas")
    @Operation(summary = "List curated practice areas", operationId = "adminListCuratedPracticeAreas")
    public ResponseEntity<List<CuratedPracticeAreaDTO>> listAreas() {
        return ResponseEntity.ok(service.listAreas().stream().map(CuratedPracticeAreaDTO::from).toList());
    }

    @GetMapping("/practices")
    @Operation(summary = "List curated practices", operationId = "adminListCuratedPractices")
    public ResponseEntity<List<CuratedPracticeSummaryDTO>> list(
        @RequestParam(defaultValue = "true") boolean includeRetired
    ) {
        return ResponseEntity.ok(service.list(includeRetired).stream().map(CuratedPracticeSummaryDTO::from).toList());
    }

    @GetMapping("/practices/{slug}")
    @Operation(summary = "Get a curated practice", operationId = "adminGetCuratedPractice")
    public ResponseEntity<CuratedPracticeDetailDTO> get(@PathVariable String slug) {
        CuratedPractice practice = service.get(slug, true);
        return withEtag(practice, CuratedPracticeDetailDTO.from(practice));
    }

    @PostMapping("/practices")
    @Operation(summary = "Create a curated practice", operationId = "adminCreateCuratedPractice")
    @ApiResponse(responseCode = "201", description = "Curated practice created")
    @ApiResponse(
        responseCode = "409",
        description = "Slug already exists",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE")
    public ResponseEntity<CuratedPracticeDetailDTO> create(
        @Valid @RequestBody CreateCuratedPracticeRequestDTO request
    ) {
        CuratedPractice practice = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{slug}")
            .buildAndExpand(practice.getSlug())
            .toUri();
        return ResponseEntity.created(location).eTag(etag(practice)).body(CuratedPracticeDetailDTO.from(practice));
    }

    @PutMapping("/practices/{slug}")
    @Operation(summary = "Replace a curated practice definition", operationId = "adminUpdateCuratedPractice")
    @ApiResponse(responseCode = "200", description = "Curated practice updated")
    @ApiResponse(
        responseCode = "412",
        description = "The supplied ETag is stale",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
        responseCode = "428",
        description = "If-Match is required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE")
    public ResponseEntity<CuratedPracticeDetailDTO> update(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody UpdateCuratedPracticeRequestDTO request
    ) {
        CuratedPractice practice = service.update(slug, precondition(ifMatch), request);
        return withEtag(practice, CuratedPracticeDetailDTO.from(practice));
    }

    @PatchMapping("/practices/{slug}/status")
    @Operation(summary = "Retire or restore a curated practice", operationId = "adminUpdateCuratedPracticeStatus")
    @ApiResponse(responseCode = "200", description = "Curated practice status updated")
    @ApiResponse(
        responseCode = "412",
        description = "The supplied ETag is stale",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
        responseCode = "428",
        description = "If-Match is required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE")
    public ResponseEntity<CuratedPracticeDetailDTO> updateStatus(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody UpdateCuratedPracticeStatusRequestDTO request
    ) {
        CuratedPractice practice = service.setStatus(slug, precondition(ifMatch), request.status());
        return withEtag(practice, CuratedPracticeDetailDTO.from(practice));
    }

    @DeleteMapping("/practices/{slug}/override")
    @Operation(summary = "Restore the bundled practice definition", operationId = "adminDeleteCuratedPracticeOverride")
    @ApiResponse(responseCode = "200", description = "Bundled practice definition restored")
    @ApiResponse(
        responseCode = "409",
        description = "No bundled definition is available",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
        responseCode = "412",
        description = "The supplied ETag is stale",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
        responseCode = "428",
        description = "If-Match is required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE")
    public ResponseEntity<CuratedPracticeDetailDTO> resetOverride(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch
    ) {
        CuratedPractice practice = service.resetOverride(slug, precondition(ifMatch));
        return withEtag(practice, CuratedPracticeDetailDTO.from(practice));
    }

    private static <T> ResponseEntity<T> withEtag(CuratedPractice practice, T body) {
        return ResponseEntity.ok().eTag(etag(practice)).body(body);
    }

    private static String etag(CuratedPractice practice) {
        return new ETag("v" + practice.getVersion(), false).formattedTag();
    }

    private static CuratedPracticeVersionPrecondition precondition(@Nullable String ifMatch) {
        if (ifMatch == null) {
            throw new CuratedPracticePreconditionRequiredException();
        }
        return CuratedPracticeVersionPrecondition.parse(ifMatch);
    }
}
