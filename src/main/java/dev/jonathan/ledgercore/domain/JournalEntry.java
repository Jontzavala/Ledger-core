package dev.jonathan.ledgercore.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "description", nullable = false)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

}
