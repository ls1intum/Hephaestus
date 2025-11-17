package de.tum.in.www1.hephaestus.gitprovider.discussion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionCategoryRepository extends JpaRepository<DiscussionCategory, Long> {}
