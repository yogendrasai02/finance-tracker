package com.financetracker.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, Long> {

    /** Ordered by name rather than the default id order, so the list reads the same way each time. */
    List<Account> findAllByOrderByNameAsc();
}
