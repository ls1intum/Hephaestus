package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultSort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResultQueries {
    Page<Result> list(
            long workspaceId, ResultQueryFilter filter, ResultSort sort, boolean displayableOnly, Pageable page);

    Page<Problem> problems(long workspaceId, ResultQueryFilter filter, ProblemQueryFilter child, Pageable page);

    List<Result> forDevelopersSince(long workspaceId, Collection<Long> developers, Instant since, boolean currentOnly);

    List<Result> ordered(long workspaceId, List<UUID> ids);
}
