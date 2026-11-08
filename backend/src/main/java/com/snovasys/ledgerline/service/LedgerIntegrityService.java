package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.web.dto.AdminDtos.AccountMismatch;
import com.snovasys.ledgerline.web.dto.AdminDtos.IntegrityReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "A ledger you can prove": recomputes everything from the raw ledger entries and compares it with what is stored.
 *  1. total debits == total credits
 *  2. every completed transaction has exactly 2 entries with equal debit/credit amounts
 *  3. every account's stored balance == credits - debits recomputed from its ledger entries
 * REPEATABLE READ gives all three queries the same consistent snapshot, even while payments are happening.
 */
@Service
public class LedgerIntegrityService {

    private final JdbcTemplate jdbc;

    public LedgerIntegrityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IntegrityReport check() {
        BigDecimal debits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE entry_type = 'DEBIT'", BigDecimal.class);
        BigDecimal credits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE entry_type = 'CREDIT'", BigDecimal.class);

        List<UUID> unbalanced = jdbc.query(
                "SELECT t.id FROM financial_transactions t "
                        + "LEFT JOIN ledger_entries e ON e.transaction_id = t.id "
                        + "WHERE t.status = 'COMPLETED' "
                        + "GROUP BY t.id, t.amount "
                        + "HAVING COUNT(e.id) <> 2 "
                        + "    OR COALESCE(SUM(CASE WHEN e.entry_type = 'DEBIT'  THEN e.amount END), 0) <> t.amount "
                        + "    OR COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount END), 0) <> t.amount "
                        + "LIMIT 100",
                (rs, i) -> rs.getObject("id", UUID.class));

        List<AccountMismatch> mismatches = jdbc.query(
                "SELECT a.id, a.account_number, a.balance, "
                        + "       COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) AS ledger_balance "
                        + "FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id "
                        + "GROUP BY a.id, a.account_number, a.balance "
                        + "HAVING a.balance <> COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) "
                        + "LIMIT 100",
                (rs, i) -> new AccountMismatch(rs.getObject("id", UUID.class), rs.getString("account_number"),
                        rs.getBigDecimal("balance"), rs.getBigDecimal("ledger_balance")));

        boolean balanced = debits.compareTo(credits) == 0 && unbalanced.isEmpty() && mismatches.isEmpty();
        return new IntegrityReport(balanced, debits, credits, unbalanced, mismatches, Instant.now());
    }
}
