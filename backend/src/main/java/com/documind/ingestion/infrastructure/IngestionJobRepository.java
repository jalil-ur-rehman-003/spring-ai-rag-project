package com.documind.ingestion.infrastructure;

import com.documind.ingestion.domain.IngestionJob;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    /**
     * Claims up to `limit` PENDING jobs for this worker using
     * "FOR UPDATE SKIP LOCKED" (via Pessimistic write lock + the
     * jakarta.persistence.lock.timeout=-2 hint, which Hibernate translates to
     * SKIP LOCKED on PostgreSQL) so multiple worker threads or instances
     * never double-process the same job.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT job FROM IngestionJob job WHERE job.status = com.documind.ingestion.domain.IngestionJobStatus.PENDING ORDER BY job.createdAt")
    List<IngestionJob> findAvailablePendingJobs(Limit limit);
}
