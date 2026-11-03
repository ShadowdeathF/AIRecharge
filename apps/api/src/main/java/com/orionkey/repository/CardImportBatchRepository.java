package com.orionkey.repository;

import com.orionkey.entity.CardImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CardImportBatchRepository extends JpaRepository<CardImportBatch, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from CardImportBatch b where b.id=:id")
    java.util.Optional<CardImportBatch> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    Page<CardImportBatch> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    Page<CardImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
