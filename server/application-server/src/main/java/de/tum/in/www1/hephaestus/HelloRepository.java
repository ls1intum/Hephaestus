package de.tum.in.www1.hephaestus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HelloRepository extends JpaRepository<Hello, Long> {

}
