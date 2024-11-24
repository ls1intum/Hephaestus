
package de.tum.in.www1.hephaestus.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryToMonitorRepository extends JpaRepository<RepositoryToMonitor, Long>  {
  
}
