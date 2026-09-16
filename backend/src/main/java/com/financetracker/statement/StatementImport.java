package com.financetracker.statement;

import java.time.Instant;
import java.time.LocalDate;

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
 * Maps to {@code app.statement_imports}: one row per uploaded file.
 *
 * {@code status} and {@code hold_reason} are plain strings with CHECK constraints in the database, like {@code Account}'s {@code type}.
 * {@code account_id} is a plain column, not a relation, so this feature never loads another feature's entity.
 *
 * {@code user_id} comes from the tenant in scope and has no setter, so an import cannot be written for another user by mistake.
 * Row-Level Security refuses a wrong one anyway.
 */
@Entity
@Table(name = "statement_imports", schema = "app")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class StatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    @ToString.Include
    private Long accountId;

    /** Sanitized before it gets here (SR-16), and never logged: a filename can carry a name or an account number. */
    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "file_sha256", nullable = false)
    private String fileSha256;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(nullable = false)
    @ToString.Include
    private String status;

    @Column(name = "hold_reason")
    @ToString.Include
    private String holdReason;

    @Column(name = "hold_row_number")
    private Integer holdRowNumber;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    public StatementImport(long accountId, String sourceFilename, String fileSha256, String status) {
        this.userId = CurrentTenantContext.requireUserId();
        this.accountId = accountId;
        this.sourceFilename = sourceFilename;
        this.fileSha256 = fileSha256;
        this.status = status;
    }
}
