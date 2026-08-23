package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record CatalogAreaAdoptionResultDTO(@NonNull List<PracticeDTO> added, @NonNull List<PracticeDTO> moved) {}
