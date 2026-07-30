package dev.jonathan.ledgercore.repository;

import dev.jonathan.ledgercore.domain.Posting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRepository extends JpaRepository<Posting, Long> {
}
