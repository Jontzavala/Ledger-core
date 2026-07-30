package dev.jonathan.ledgercore.service;

import dev.jonathan.ledgercore.domain.Account;
import dev.jonathan.ledgercore.domain.JournalEntry;
import dev.jonathan.ledgercore.domain.Posting;
import dev.jonathan.ledgercore.repository.AccountRepository;
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

    public LedgerService(JournalEntryRepository entryRepository, PostingRepository postingRepository,
                         AccountRepository accountRepository) {
        this.entryRepository = entryRepository;
        this.postingRepository = postingRepository;
        this.accountRepository = accountRepository;
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
}
