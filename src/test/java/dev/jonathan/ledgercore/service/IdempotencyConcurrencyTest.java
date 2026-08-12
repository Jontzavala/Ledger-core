package dev.jonathan.ledgercore.service;

import dev.jonathan.ledgercore.domain.Account;
import dev.jonathan.ledgercore.repository.AccountRepository;
import dev.jonathan.ledgercore.repository.IdempotencyKeyRepository;
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
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class IdempotencyConcurrencyTest {

    private static final int THREADS = 50;

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

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    Long aliceId;
    Long bobId;

    @BeforeEach
    void cleanTablesAndCreateAccounts() {
        // Children before parents: postings reference entries and accounts,
        // idempotency_keys reference entries.
        postingRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        entryRepository.deleteAll();
        accountRepository.deleteAll();

        Account alice = accountRepository.save(new Account("alice"));
        Account bob = accountRepository.save(new Account("bob"));
        aliceId = alice.getId();
        bobId = bob.getId();
    }

    @Test
    void exactlyOneEntryIsCreatedFor50ConcurrentIdenticalRequests() throws Exception {
        String key = "same-key-for-all-threads";
        String description = "concurrent duplicate request";
        List<LegRequest> legs = List.of(
                new LegRequest(aliceId, -1000L),
                new LegRequest(bobId, 1000L));

        // Every task blocks on this latch, so no task can run until the main
        // thread releases them all at once.
        CountDownLatch startGate = new CountDownLatch(1);

        List<PostResult> results = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // Fixed pool of THREADS so all 50 tasks have a thread of their own and
        // none of them has to wait for an earlier task to finish.
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // Counted down by each task once it has finished, so the main thread
        // can wait for the whole batch with a timeout.
        CountDownLatch doneGate = new CountDownLatch(THREADS);

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit((Callable<Void>) () -> {
                    try {
                        startGate.await();
                        results.add(ledgerService.postEntryIdempotent(key, description, legs));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneGate.countDown();
                    }
                    return null;
                });
            }

            // All 50 threads are parked on startGate.await() by now; this single
            // countDown releases them simultaneously.
            startGate.countDown();

            boolean finished = doneGate.await(60, TimeUnit.SECONDS);
            if (!finished) {
                throw new IllegalStateException("threads did not finish within 60s");
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertEquals(1, entryRepository.findAll().size());
        assertEquals(2, postingRepository.findAll().size());
        assertEquals(1, idempotencyKeyRepository.findAll().size());

        int createdCount = 0;
        for (PostResult result : results) {
            if (result.created()) {
                createdCount++;
            }
        }
        assertEquals(1, createdCount);

        System.out.println("successful results: " + results.size());
        System.out.println("created: " + createdCount + ", replayed: " + (results.size() - createdCount));
        System.out.println("failures: " + failures.size());
    }
}
