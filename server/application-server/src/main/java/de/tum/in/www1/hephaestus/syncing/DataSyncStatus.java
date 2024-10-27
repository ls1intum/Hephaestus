package de.tum.in.www1.hephaestus.syncing;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "data_sync_status")
@Getter
@Setter
@NoArgsConstructor
public class DataSyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @NonNull
    private OffsetDateTime startTime;

    @NonNull
    private OffsetDateTime endTime;

    public enum Status {
        SUCCESS,
        FAILED,
        IN_PROGRESS
    }
}
