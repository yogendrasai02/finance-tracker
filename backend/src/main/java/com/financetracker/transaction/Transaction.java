package com.financetracker.transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.financetracker.common.tenant.CurrentTenantContext;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Maps to {@code app.transactions}, the ledger.
 *
 * Every reference to another feature ({@code account_id}, {@code statement_import_id}, {@code source_row_id}, the category columns) is a plain {@code Long}.
 * So this package never depends on the features that write to it, such as statement import.
 *
 * {@code source}, {@code transaction_type} and the other value sets are plain strings with CHECK constraints in the database, like {@code Account}'s {@code type}.
 * {@code toString()} names only the id, because the other columns are amounts and narrations (SR-25).
 */
@Entity
@Table(name = "transactions", schema = "app")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "txn_time")
    private LocalTime txnTime;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column
    private String narration;

    @Column(name = "narration_normalized")
    private String narrationNormalized;

    @Column(name = "balance_after_paise")
    private Long balanceAfterPaise;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String source;

    @Column(name = "statement_import_id")
    private Long statementImportId;

    @Column(name = "source_row_id")
    private Long sourceRowId;

    @Column(name = "source_row_fingerprint")
    private String sourceRowFingerprint;

    @Column(name = "fingerprint_version")
    private Short fingerprintVersion;

    /** Set here as well as by the column default, because Hibernate inserts every mapped column and would otherwise send NULL. */
    @Column(name = "transaction_type", nullable = false)
    private String transactionType = "UNCLASSIFIED";

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_source")
    private String categorySource;

    @Column(name = "category_rule_id")
    private Long categoryRuleId;

    @Column(name = "needs_wants")
    private String needsWants;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Transaction(long accountId, LocalDate txnDate, long amountPaise, String source) {
        this.userId = CurrentTenantContext.requireUserId();
        this.accountId = accountId;
        this.txnDate = txnDate;
        this.amountPaise = amountPaise;
        this.source = source;
    }
}
