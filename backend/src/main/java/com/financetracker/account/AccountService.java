package com.financetracker.account;

import java.util.List;

import com.financetracker.common.tenant.CurrentTenantContext;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-1's read side.
 *
 * No account-management methods: FR-1 explicitly has no management UI in MVP, so this service only ever lists.
 * It does not set the tenant itself; that already happened before this method runs, via {@link com.financetracker.common.security.TenantContextFilter} and the transaction manager (SR-01).
 * Row-Level Security is what actually restricts {@link AccountRepository#findAllByOrderByNameAsc()} to the caller's own rows.
 */
@Service
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        List<Account> accounts = accountRepository.findAllByOrderByNameAsc();

        log.info("Found {} accounts for user {}", accounts.size(), CurrentTenantContext.currentUserId());

        return accounts.stream().map(AccountResponse::from).toList();
    }
}
