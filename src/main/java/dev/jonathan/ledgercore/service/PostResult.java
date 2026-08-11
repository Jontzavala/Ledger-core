package dev.jonathan.ledgercore.service;

import dev.jonathan.ledgercore.domain.JournalEntry;

public record PostResult(JournalEntry entry, boolean created) {
}
