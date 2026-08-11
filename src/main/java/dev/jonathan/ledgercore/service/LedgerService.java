package dev.jonathan.ledgercore.service;

import dev.jonathan.ledgercore.domain.Account;
import dev.jonathan.ledgercore.domain.IdempotencyKey;
import dev.jonathan.ledgercore.domain.JournalEntry;
import dev.jonathan.ledgercore.domain.Posting;
import dev.jonathan.ledgercore.repository.AccountRepository;
import dev.jonathan.ledgercore.repository.IdempotencyKeyRepository;
import dev.jonathan.ledgercore.repository.JournalEntryRepository;
import dev.jonathan.ledgercore.repository.PostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {

    private final JournalEntryRepository entryRepository;
    private final PostingRepository postingRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public LedgerService(JournalEntryRepository entryRepository, PostingRepository postingRepository,
                         AccountRepository accountRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.entryRepository = entryRepository;
        this.postingRepository = postingRepository;
        this.accountRepository = accountRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public JournalEntry postEntry(String description, List<LegRequest> legs) {
        if (legs.size() < 2) {
            throw new IllegalArgumentException("Entry must have at least two legs");
        }
        long total = 0;
        for (LegRequest leg : legs) {
            total += leg.amount();
        }
        if (total != 0) {
            throw new IllegalArgumentException("Total must equal 0, got " + total);
        }
        JournalEntry entry = entryRepository.save(new JournalEntry(description));
        for (LegRequest leg : legs) {
            Account account = accountRepository.findById(leg.accountId())
                    .orElseThrow(() -> new IllegalArgumentException("no account " + leg.accountId()));

            postingRepository.save(new Posting(entry, account, leg.amount()));
        }
        return entry;
    }

    @Transactional
    public PostResult postEntryIdempotent(String key, String description, List<LegRequest> legs) {
        String hash = RequestHasher.hashRequest(description, legs);
        int inserted = idempotencyKeyRepository.insertIfAbsent(key, hash);
        if (inserted == 1) {
            JournalEntry entry = postEntry(description, legs);
            IdempotencyKey keyRow = idempotencyKeyRepository.findByIdempotencyKey(key).orElseThrow(()
                    -> new IllegalStateException("key row missing after insert"));
            keyRow.setEntry(entry);
            idempotencyKeyRepository.save(keyRow);
            return new PostResult(entry, true);
        } else {
            IdempotencyKey keyRow = idempotencyKeyRepository.findByIdempotencyKey(key).orElseThrow(()
                    -> new IllegalStateException("key row missing after insert"));
            if (keyRow.getEntry() == null) {
                throw new IdempotencyConflictException("A request with key " + key + " is currently being processed; retry shortly");
            }
            if (hash.equals(keyRow.getRequestHash())) {
                return new PostResult(keyRow.getEntry(), false);
            } else {
                throw new IdempotencyConflictException("Idempotency key " + key +
                        " was already used with a different request payload");
            }
        }
    }
}
