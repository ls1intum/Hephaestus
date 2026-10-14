package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultSort;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultReadQuery {
    private final JdbcClient jdbc;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private static final String JOIN =
            " FROM result r JOIN practice p ON p.id=r.practice_id AND p.workspace_id=r.workspace_id LEFT JOIN"
                    + " practice_group g ON g.id=p.group_id AND g.workspace_id=p.workspace_id JOIN item i ON"
                    + " i.id=r.item_id AND i.workspace_id=r.workspace_id LEFT JOIN practice_version v ON"
                    + " v.id=r.practice_version_id AND v.workspace_id=r.workspace_id JOIN practice_version cv ON"
                    + " cv.id=p.current_version_id AND cv.workspace_id=p.workspace_id ";
    private static final String INCORRECT =
            "EXISTS(SELECT 1 FROM result_mark m WHERE m.workspace_id=r.workspace_id AND m.result_id=r.id AND"
                    + " m.kind='INCORRECT' AND m.removed_at IS NULL)";
    private static final String ACCEPTED =
            "EXISTS(SELECT 1 FROM result_mark m WHERE m.workspace_id=r.workspace_id AND m.result_id=r.id AND"
                    + " m.kind='ACCEPTED' AND m.removed_at IS NULL)";
    private static final String DISAGREED =
            "EXISTS(SELECT 1 FROM feedback_basis b JOIN feedback_response s ON s.feedback_id=b.feedback_id AND"
                + " s.workspace_id=b.workspace_id WHERE b.workspace_id=r.workspace_id AND b.result_id=r.id AND"
                + " s.resolution='DISAGREE' AND s.id=(SELECT latest.id FROM feedback_response latest WHERE"
                + " latest.workspace_id=s.workspace_id AND latest.feedback_id=s.feedback_id ORDER BY latest.created_at"
                + " DESC,latest.id::text DESC LIMIT 1))";
    private static final String CURRENTNESS =
            "CASE WHEN r.ended_at IS NOT NULL THEN 'STALE' WHEN r.evidence_version=0 OR v.id IS NULL OR r.summary IS"
                + " NULL OR r.explanation IS NULL OR r.evidence IS NULL OR r.coverage IS NULL THEN 'UNVERIFIABLE' WHEN"
                + " v.review_digest=cv.review_digest THEN 'CURRENT' ELSE 'STALE' END";

    public Page<Result> list(
            long workspaceId, ResultQueryFilter filter, ResultSort sort, boolean displayableOnly, Pageable page) {
        var selection = selection(workspaceId, filter);
        String where = selection.where() + (displayableOnly ? " AND r.outcome <> 'DOES_NOT_APPLY'" : "");
        String order = sort == ResultSort.ACTIONABILITY
                ? "CASE r.severity WHEN 'CRITICAL' THEN 0 WHEN 'MAJOR' THEN 1 WHEN 'MINOR' THEN 2 WHEN 'TRIVIAL' THEN"
                        + " 3 ELSE 4 END, "
                : "";
        long total = jdbc.sql("SELECT count(*)" + JOIN + where)
                .params(selection.params())
                .query((rs, n) -> rs.getLong(1))
                .single();
        var params = new LinkedHashMap<>(selection.params());
        params.put("limit", page.getPageSize());
        params.put("offset", page.getOffset());
        List<UUID> ids = jdbc.sql("SELECT r.id" + JOIN + where + " ORDER BY " + order
                        + "r.recorded_at DESC,r.id::text DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        return new PageImpl<>(ordered(workspaceId, ids), page, total);
    }

    public Page<Problem> problems(long workspaceId, ResultQueryFilter filter, ProblemQueryFilter child, Pageable page) {
        var s = selection(workspaceId, filter);
        var where = new StringBuilder(s.where());
        var params = new LinkedHashMap<>(s.params());
        add(where, params, "child.result_id=:resultId", "resultId", child.resultId());
        add(
                where,
                params,
                "EXISTS(SELECT 1 FROM jsonb_array_elements(child.citations) c WHERE c->>'path'=:path)",
                "path",
                child.path());
        many(
                where,
                params,
                "child.severity",
                "problemSeverity",
                child.severities() == null
                        ? null
                        : child.severities().stream().map(Enum::name).toList());
        if (child.openOnly())
            where.append(
                    " AND r.ended_at IS NULL AND r.outcome='NOT_MET' AND NOT " + INCORRECT + " AND NOT " + ACCEPTED);
        String joined = JOIN + " JOIN problem child ON child.result_id=r.id AND child.workspace_id=r.workspace_id ";
        long total = jdbc.sql("SELECT count(*)" + joined + where)
                .params(params)
                .query((rs, n) -> rs.getLong(1))
                .single();
        params.put("limit", page.getPageSize());
        params.put("offset", page.getOffset());
        var ids = jdbc.sql("SELECT child.id" + joined + where
                        + " ORDER BY r.recorded_at DESC,r.id::text DESC,child.position ASC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        Map<UUID, Problem> loaded = problems.findByWorkspaceIdAndIdIn(workspaceId, ids).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
        return new PageImpl<>(
                ids.stream().map(loaded::get).filter(Objects::nonNull).toList(), page, total);
    }

    public List<Result> forDevelopersSince(
            long workspaceId, Collection<Long> developers, Instant since, boolean currentOnly) {
        if (developers.isEmpty()) return List.of();
        var ids = jdbc.sql("SELECT r.id" + JOIN
                        + " WHERE r.workspace_id=:ws AND r.developer_id IN (:developers) AND r.recorded_at>=:since"
                        + (currentOnly ? " AND r.ended_at IS NULL AND NOT " + INCORRECT : "")
                        + " ORDER BY r.recorded_at DESC,r.id::text DESC")
                .param("ws", workspaceId)
                .param("developers", developers)
                .param("since", since)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        return ordered(workspaceId, ids);
    }

    public List<Result> ordered(long workspaceId, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        var loaded = results.findByWorkspaceIdAndIdIn(workspaceId, ids).stream()
                .collect(Collectors.toMap(Result::getId, r -> r));
        return ids.stream().map(loaded::get).filter(Objects::nonNull).toList();
    }

    private static Selection selection(long ws, ResultQueryFilter f) {
        var where = new StringBuilder(" WHERE r.workspace_id=:ws");
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        add(where, params, "r.review_id=:reviewId", "reviewId", f.reviewId());
        add(where, params, "r.item_id=:itemId", "itemId", f.itemId());
        add(where, params, "i.kind=:itemKind", "itemKind", f.itemKind());
        add(where, params, "p.slug=:practiceSlug", "practiceSlug", f.practiceSlug());
        add(where, params, "g.slug=:groupSlug", "groupSlug", f.groupSlug());
        add(where, params, "r.developer_id=:developerId", "developerId", f.developerId());
        add(
                where,
                params,
                CURRENTNESS + "=:currentness",
                "currentness",
                f.currentness() == null ? null : f.currentness().name());
        add(where, params, "r.recorded_at>=:from", "from", f.from());
        add(where, params, "r.recorded_at<:to", "to", f.to());
        many(
                where,
                params,
                "r.outcome",
                "outcomes",
                f.outcomes() == null
                        ? null
                        : f.outcomes().stream().map(Enum::name).toList());
        many(
                where,
                params,
                "r.severity",
                "severities",
                f.severities() == null
                        ? null
                        : f.severities().stream().map(Enum::name).toList());
        if (f.markedIncorrect() != null) where.append(" AND " + (f.markedIncorrect() ? "" : "NOT ") + INCORRECT);
        if (f.accepted() != null) where.append(" AND " + (f.accepted() ? "" : "NOT ") + ACCEPTED);
        if (f.disagreed() != null) where.append(" AND " + (f.disagreed() ? "" : "NOT ") + DISAGREED);
        return new Selection(where.toString(), params);
    }

    private static void add(
            StringBuilder where, Map<String, Object> params, String clause, String name, @Nullable Object value) {
        if (value != null) {
            where.append(" AND " + clause);
            params.put(name, value);
        }
    }

    private static void many(
            StringBuilder where, Map<String, Object> params, String column, String name, @Nullable List<?> values) {
        if (values != null && !values.isEmpty()) add(where, params, column + " IN (:" + name + ")", name, values);
    }

    private record Selection(String where, Map<String, Object> params) {}
}
