package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ResultItemLockRepository extends Repository<Result, UUID> {
    @Query(value = "SELECT id FROM item WHERE workspace_id=:ws AND id=:id FOR UPDATE", nativeQuery = true)
    long lock(long ws, long id);
}
