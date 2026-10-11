package de.tum.cit.aet.hephaestus.activity.overview.dto;

import java.util.List;
import org.jspecify.annotations.NonNull;

/** The repositories and teams that workspace activity can be narrowed to: the lists the people read also carries. */
public record ActivityFacetsDTO(
        @NonNull List<ActivityRepositoryDTO> repositories,
        @NonNull List<ActivityTeamDTO> teams) {}
