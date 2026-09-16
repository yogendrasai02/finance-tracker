package com.financetracker.statement;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.financetracker.common.tenant.CurrentTenantContext;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Maps to {@code app.statement_import_rows}: one transaction-table row of an uploaded file, stored as the file had it (DM-44).
 *
 * {@code raw_cells} is JSON text in a {@code String}, not a parsed object, so the stored text is exactly what the parser wrote.
 * {@code jsonb} still normalizes it on the way in: key order and whitespace come back in Postgres's own form.
 *
 * The raw facts have no setters; a database trigger refuses to change them as well (DM-02).
 * {@code toString()} names only ids, because {@code raw_cells} holds narrations and amounts (SR-25).
 */
@Entity
@Table(name = "statement_import_rows", schema = "app")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class StatementImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Column(name = "statement_import_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    @ToString.Include
    private Long statementImportId;

    @Column(name = "row_number", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    @ToString.Include
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_cells", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String rawCells;

    @Column(name = "row_status")
    private String rowStatus;

    @Column(name = "related_transaction_id")
    private Long relatedTransactionId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public StatementImportRow(long statementImportId, int rowNumber, String rawCells) {
        this.userId = CurrentTenantContext.requireUserId();
        this.statementImportId = statementImportId;
        this.rowNumber = rowNumber;
        this.rawCells = rawCells;
    }
}
