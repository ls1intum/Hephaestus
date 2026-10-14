package de.tum.cit.aet.hephaestus.integration.scm.report;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExport;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanExportLocation;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanReportExporter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnServerRole
public class ScmSarifExporter implements ScanReportExporter {
    private final ObjectMapper mapper;
    private final String version;

    public ScmSarifExporter(ObjectMapper mapper, @Value("${spring.application.version}") String version) {
        this.mapper = mapper;
        this.version = version;
    }

    @Override
    public byte[] export(ScanExport report) {
        if (report.active()) throw new ResponseStatusException(HttpStatus.CONFLICT, "The scan is still running");
        if (report.expired()) throw new ResponseStatusException(HttpStatus.GONE, "The scan's raw proof has expired");
        var root = mapper.createObjectNode();
        root.put("version", "2.1.0");
        root.put(
                "$schema",
                "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json");
        var run = root.putArray("runs").addObject();
        var driver = run.putObject("tool").putObject("driver");
        driver.put("name", "Hephaestus");
        driver.put("version", version);
        var rules = driver.putArray("rules");
        var results = run.putArray("results");
        Set<String> seen = new HashSet<>();
        for (var problem : report.problems()) {
            if (seen.add(problem.practiceSlug())) {
                var rule = rules.addObject();
                rule.put("id", problem.practiceSlug());
                rule.put("name", problem.practiceSlug());
                rule.putObject("shortDescription").put("text", problem.practiceName());
                rule.putObject("fullDescription").put("text", problem.criteria());
            }
            var result = results.addObject();
            result.put("ruleId", problem.practiceSlug());
            result.put("kind", "fail");
            result.put(
                    "level",
                    switch (problem.severity()) {
                        case "CRITICAL", "MAJOR" -> "error";
                        case "MINOR" -> "warning";
                        case "TRIVIAL" -> "note";
                        default -> throw new IllegalArgumentException("Unknown severity");
                    });
            result.putObject("message").put("text", problem.summary());
            result.putObject("partialFingerprints").put("hephaestus/problem/v1", problem.stableIdentity());
            if (report.baselineComparable() && report.baselineScanId() != null && problem.baselineState() != null)
                result.put("baselineState", problem.baselineState().toLowerCase(Locale.ROOT));
            var primary = result.putArray("locations");
            var related = result.putArray("relatedLocations");
            int relatedId = 1;
            for (var citation : problem.citations()) {
                ObjectNode location =
                        citation.path().equals(problem.path()) ? primary.addObject() : related.addObject();
                if (!citation.path().equals(problem.path())) location.put("id", relatedId++);
                location(location.putObject("physicalLocation"), citation);
            }
            if (primary.isEmpty())
                location(
                        primary.addObject().putObject("physicalLocation"),
                        new ScanExportLocation(problem.path(), null, null, null));
            if (related.isEmpty()) result.remove("relatedLocations");
            var facts = result.putObject("properties");
            facts.put("problemId", problem.problemId().toString());
            facts.put("resultId", problem.resultId().toString());
            facts.put("itemId", Long.toString(problem.itemId()));
            facts.put("practiceVersionId", Long.toString(problem.practiceVersionId()));
            facts.put("reviewDigest", problem.reviewDigest());
            facts.put("itemVersion", problem.itemVersion());
            facts.put("severity", problem.severity());
        }
        if (report.baselineComparable() && report.baselineScanId() != null)
            run.put("baselineGuid", report.baselineScanId().toString());
        run.putObject("automationDetails")
                .put(
                        "id",
                        "hephaestus/"
                                + report.workspaceId()
                                + "/"
                                + report.repositoryItemId()
                                + "/default/"
                                + report.selectorDigest()
                                + "/"
                                + report.modelDigest()
                                + "/"
                                + report.epoch()
                                + "/");
        var invocation = run.putArray("invocations").addObject();
        invocation.put("executionSuccessful", report.complete() && report.listingComplete());
        if (!report.complete() || !report.listingComplete()) {
            var notice = invocation.putArray("toolExecutionNotifications").addObject();
            notice.put("level", "warning");
            notice.putObject("message").put("text", "The scan did not review the complete selected scope.");
        }
        var provenance = run.putArray("versionControlProvenance").addObject();
        provenance.put("repositoryUri", repositoryUri(report.repositoryUrl()));
        provenance.put("revisionId", report.commit());
        var facts = run.putObject("properties").putObject("hephaestus");
        facts.put("scanId", report.scanId().toString());
        facts.put("repositoryItemId", Long.toString(report.repositoryItemId()));
        facts.put("commit", report.commit());
        facts.put("listingComplete", report.listingComplete());
        facts.put("complete", report.complete());
        facts.put("epoch", report.epoch());
        facts.set("counts", mapper.valueToTree(report.counts()));
        facts.set("comparisonReasons", mapper.valueToTree(report.comparisonReasons()));
        return mapper.writeValueAsBytes(root);
    }

    private static void location(ObjectNode target, ScanExportLocation citation) {
        target.putObject("artifactLocation")
                .put("uri", relativeUri(citation.path()))
                .put("uriBaseId", "%SRCROOT%");
        if (citation.startLine() != null) {
            var region = target.putObject("region");
            region.put("startLine", citation.startLine());
            if (citation.endLine() != null) region.put("endLine", citation.endLine());
            if (citation.quote() != null) region.putObject("snippet").put("text", citation.quote());
        }
    }

    private static String relativeUri(String path) {
        try {
            return new URI(null, null, "/" + path, null)
                    .toASCIIString()
                    .substring(1)
                    .replace(":", "%3A");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid repository file path", e);
        }
    }

    private static String repositoryUri(String url) {
        try {
            URI uri = new URI(url);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid repository URL");
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
                    .toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid repository URL", e);
        }
    }
}
