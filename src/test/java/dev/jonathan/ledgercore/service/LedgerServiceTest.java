package dev.jonathan.ledgercore.service;

import dev.jonathan.ledgercore.domain.Account;
import dev.jonathan.ledgercore.domain.JournalEntry;
import dev.jonathan.ledgercore.domain.Posting;
import dev.jonathan.ledgercore.repository.AccountRepository;
import dev.jonathan.ledgercore.repository.JournalEntryRepository;
import dev.jonathan.ledgercore.repository.PostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class LedgerServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    LedgerService ledgerService;

    @Autowired
    JournalEntryRepository entryRepository;

    @Autowired
    PostingRepository postingRepository;

    @Autowired
    AccountRepository accountRepository;

    @BeforeEach
    void cleanTables() {
        // Children before parents: postings reference both entries and accounts.
        postingRepository.deleteAll();
        entryRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void balancedEntryPersistsWithAllPostings() {
        Account alice = accountRepository.save(new Account("alice"));
        Account bob = accountRepository.save(new Account("bob"));

        JournalEntry entry = ledgerService.postEntry("description",
                List.of(new LegRequest(alice.getId(), -1000L),
                        new LegRequest(bob.getId(), 1000L)));

        assertNotNull(entry.getId());

        List<Posting> postings = postingRepository.findAll();
        assertEquals(2, postings.size());

        long total = 0;
        for (Posting p : postings) {
            total += p.getAmount();
        }
        assertEquals(0, total);
    }

    @Test
    void entryWithLegsNotSummingToZeroIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ledgerService.postEntry("description",
                List.of(new LegRequest(1L, -100L),
                        new LegRequest(2L, 101L))));
    }

    @Test
    void entryWithFewerThanTwoLegsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.postEntry("description",
                        List.of(new LegRequest(1L, 100L))));
    }
}
