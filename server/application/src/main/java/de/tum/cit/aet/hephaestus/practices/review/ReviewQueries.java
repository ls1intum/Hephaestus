package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewQueries {
    Page<Review> search(long workspaceId, ReviewFilterParams filter, Pageable page);
}
