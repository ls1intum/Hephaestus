package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedCatalogDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedCatalogSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedStatusRequestDTO;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The instance catalog. Areas and practices get the same operations, because they are the same kind
 * of thing: read, add, replace, keep mine, use the Hephaestus one, and stop offering it.
 *
 * <p>Every write carries {@code If-Match}. The tag is derived from the entry's content, so it works
 * the same whether or not anybody has edited the entry before.
 */
@RestController
@RequestMapping("/admin/practice-catalog")
@PreAuthorize("hasAuthority('app_admin')")
@ConditionalOnServerRole
@Tag(name = "Admin Practice Catalog", description = "The practice catalog this instance offers")
@RequiredArgsConstructor
@Validated
public class CuratedCatalogAdminController {

    private final CuratedCatalogService service;

    @GetMapping
    @Operation(
        summary = "Read the instance catalog",
        description = "Every entry with the definition in force, what Hephaestus ships where it differs, and a summary.",
        operationId = "adminGetCuratedCatalog"
    )
    public ResponseEntity<CuratedCatalogDTO> catalog() {
        EffectiveCatalog catalog = service.catalog();
        return ResponseEntity.ok(
            new CuratedCatalogDTO(
                CuratedCatalogSummaryDTO.from(catalog.summary()),
                catalog.areas().stream().map(CuratedAreaDTO::from).toList(),
                catalog.practices().stream().map(CuratedPracticeSummaryDTO::from).toList()
            )
        );
    }

    @GetMapping("/practices/{slug}")
    @Operation(summary = "Read a catalog practice", operationId = "adminGetCuratedPractice")
    @ApiResponse(
        responseCode = "200",
        description = "The practice",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
    )
    public ResponseEntity<CuratedPracticeDTO> getPractice(@PathVariable String slug) {
        return ok(service.practice(slug), CuratedPracticeDTO::from);
    }

    @PostMapping("/practices")
    @Operation(summary = "Add a practice to the catalog", operationId = "adminCreateCuratedPractice")
    @ApiResponse(
        responseCode = "201",
        description = "Practice added",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Slug already exists",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE")
    public ResponseEntity<CuratedPracticeDTO> createPractice(
        @Valid @RequestBody CreateCuratedPracticeRequestDTO request
    ) {
        var entry = service.createPractice(request.slug(), request.definition().definition());
        return created(entry.slug(), entry.etag(), CuratedPracticeDTO.from(entry));
    }

    @PutMapping("/practices/{slug}")
    @Operation(summary = "Replace a practice definition", operationId = "adminUpdateCuratedPractice")
    @ApiResponse(
        responseCode = "200",
        description = "The practice",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
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
    public ResponseEntity<CuratedPracticeDTO> updatePractice(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody CuratedPracticeRequestDTO request
    ) {
        return ok(service.writePractice(slug, precondition(ifMatch), request.definition()), CuratedPracticeDTO::from);
    }

    @PatchMapping("/practices/{slug}/status")
    @Operation(
        summary = "Stop offering a practice, or offer it again",
        operationId = "adminUpdateCuratedPracticeStatus"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The practice",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
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
    public ResponseEntity<CuratedPracticeDTO> updatePracticeStatus(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody UpdateCuratedStatusRequestDTO request
    ) {
        return ok(service.setPracticeStatus(slug, precondition(ifMatch), request.status()), CuratedPracticeDTO::from);
    }

    @DeleteMapping("/practices/{slug}/override")
    @Operation(
        summary = "Use the Hephaestus definition of a practice",
        description = "Discards this instance's definition, so the practice follows Hephaestus again.",
        operationId = "adminDeleteCuratedPracticeOverride"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The practice",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Hephaestus ships no definition for this slug",
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
    public ResponseEntity<CuratedPracticeDTO> resetPractice(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch
    ) {
        return ok(service.resetPractice(slug, precondition(ifMatch)), CuratedPracticeDTO::from);
    }

    @PostMapping("/practices/{slug}/keep")
    @Operation(
        summary = "Keep this instance's definition of a practice",
        description = "Records that the newer Hephaestus definition has been seen and this instance's own is kept.",
        operationId = "adminKeepCuratedPractice"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The practice",
        content = @Content(schema = @Schema(implementation = CuratedPracticeDTO.class))
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
    public ResponseEntity<CuratedPracticeDTO> keepPractice(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch
    ) {
        return ok(service.keepPractice(slug, precondition(ifMatch)), CuratedPracticeDTO::from);
    }

    @GetMapping("/areas/{slug}")
    @Operation(summary = "Read a catalog area", operationId = "adminGetCuratedArea")
    @ApiResponse(
        responseCode = "200",
        description = "The area",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
    )
    public ResponseEntity<CuratedAreaDTO> getArea(@PathVariable String slug) {
        return ok(service.area(slug), CuratedAreaDTO::from);
    }

    @GetMapping("/areas/{slug}/practices")
    @Operation(
        summary = "Practices an area would withhold if it stopped being offered",
        operationId = "adminListCuratedAreaPractices"
    )
    public ResponseEntity<List<String>> getAreaPractices(@PathVariable String slug) {
        service.area(slug);
        return ResponseEntity.ok(service.catalog().offeredPracticesIn(slug));
    }

    @PostMapping("/areas")
    @Operation(summary = "Add an area to the catalog", operationId = "adminCreateCuratedArea")
    @ApiResponse(
        responseCode = "201",
        description = "Area added",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Slug already exists",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE_AREA")
    public ResponseEntity<CuratedAreaDTO> createArea(@Valid @RequestBody CreateCuratedAreaRequestDTO request) {
        var entry = service.createArea(request.slug(), request.definition().definition());
        return created(entry.slug(), entry.etag(), CuratedAreaDTO.from(entry));
    }

    @PutMapping("/areas/{slug}")
    @Operation(summary = "Replace an area definition", operationId = "adminUpdateCuratedArea")
    @ApiResponse(
        responseCode = "200",
        description = "The area",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
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
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE_AREA")
    public ResponseEntity<CuratedAreaDTO> updateArea(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody CuratedAreaRequestDTO request
    ) {
        return ok(service.writeArea(slug, precondition(ifMatch), request.definition()), CuratedAreaDTO::from);
    }

    @PatchMapping("/areas/{slug}/status")
    @Operation(
        summary = "Stop offering an area, or offer it again",
        description = "An area that is not offered withholds the practices filed under it; nothing already installed changes.",
        operationId = "adminUpdateCuratedAreaStatus"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The area",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
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
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE_AREA")
    public ResponseEntity<CuratedAreaDTO> updateAreaStatus(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch,
        @Valid @RequestBody UpdateCuratedStatusRequestDTO request
    ) {
        return ok(service.setAreaStatus(slug, precondition(ifMatch), request.status()), CuratedAreaDTO::from);
    }

    @DeleteMapping("/areas/{slug}/override")
    @Operation(summary = "Use the Hephaestus definition of an area", operationId = "adminDeleteCuratedAreaOverride")
    @ApiResponse(
        responseCode = "200",
        description = "The area",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Hephaestus ships no definition for this slug",
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
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE_AREA")
    public ResponseEntity<CuratedAreaDTO> resetArea(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch
    ) {
        return ok(service.resetArea(slug, precondition(ifMatch)), CuratedAreaDTO::from);
    }

    @PostMapping("/areas/{slug}/keep")
    @Operation(summary = "Keep this instance's definition of an area", operationId = "adminKeepCuratedArea")
    @ApiResponse(
        responseCode = "200",
        description = "The area",
        content = @Content(schema = @Schema(implementation = CuratedAreaDTO.class))
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
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "CURATED_PRACTICE_AREA")
    public ResponseEntity<CuratedAreaDTO> keepArea(
        @PathVariable String slug,
        @Parameter(required = true) @RequestHeader(
            name = HttpHeaders.IF_MATCH,
            required = false
        ) @Nullable String ifMatch
    ) {
        return ok(service.keepArea(slug, precondition(ifMatch)), CuratedAreaDTO::from);
    }

    private static <D extends CatalogDefinition, T> ResponseEntity<T> ok(
        CatalogEntry<D> entry,
        java.util.function.Function<CatalogEntry<D>, T> body
    ) {
        return ResponseEntity.ok().eTag(etag(entry.etag())).body(body.apply(entry));
    }

    private static <T> ResponseEntity<T> created(String slug, String tag, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{slug}").buildAndExpand(slug).toUri();
        return ResponseEntity.created(location).eTag(etag(tag)).body(body);
    }

    private static String etag(String tag) {
        return new ETag(tag, false).formattedTag();
    }

    private static @Nullable CuratedVersionPrecondition precondition(@Nullable String ifMatch) {
        return ifMatch == null ? null : CuratedVersionPrecondition.parse(ifMatch);
    }
}
