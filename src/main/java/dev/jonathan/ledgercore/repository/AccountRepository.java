package dev.jonathan.ledgercore.repository;

import dev.jonathan.ledgercore.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
