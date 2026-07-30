package dev.jonathan.ledgercore.repository;

import dev.jonathan.ledgercore.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
}
