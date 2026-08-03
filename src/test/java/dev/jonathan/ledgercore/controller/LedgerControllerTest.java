package dev.jonathan.ledgercore.controller;

import dev.jonathan.ledgercore.domain.Account;
import dev.jonathan.ledgercore.repository.AccountRepository;
import dev.jonathan.ledgercore.repository.JournalEntryRepository;
import dev.jonathan.ledgercore.repository.PostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LedgerControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    JournalEntryRepository entryRepository;

    @Autowired
    PostingRepository postingRepository;

    Account alice;
    Account bob;

    @BeforeEach
    void setUp() {
        // Children before parents: postings reference both entries and accounts.
        postingRepository.deleteAll();
        entryRepository.deleteAll();
        accountRepository.deleteAll();

        alice = accountRepository.save(new Account("alice"));
        bob = accountRepository.save(new Account("bob"));
    }

    @Test
    void postingBalancedEntryReturns201() throws Exception {
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"test\",\"legs\":[{\"accountId\":" + alice.getId() +
                                ",\"amount\":-1000},{\"accountId\":" + bob.getId() + ",\"amount\":1000}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void postingUnbalancedEntryReturns400() throws Exception {
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"test\",\"legs\":[{\"accountId\":" + alice.getId() +
                                ",\"amount\":-1000},{\"accountId\":" + bob.getId() + ",\"amount\":999}]}"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(status().isBadRequest());
    }
}
