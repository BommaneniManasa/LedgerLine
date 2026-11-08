package com.snovasys.ledgerline.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end tests: real HTTP calls against the running application and a REAL PostgreSQL database.
 *
 * They only run when these environment variables are set (otherwise they are skipped, never faked):
 *   LEDGERLINE_DB_URL, LEDGERLINE_DB_USER, LEDGERLINE_DB_PASSWORD   -> an EMPTY, throw-away database
 *   LEDGERLINE_JWT_SECRET                                           -> any random string, 32+ bytes
 *   LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL, LEDGERLINE_BOOTSTRAP_ADMIN_PASSWORD
 * Never point these at a database you care about: the tests create users and move (test) money.
 *
 * Uses only the JDK HttpClient + JDBC, so it needs no extra test libraries.
 */
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerlineIntegrationTest {

    private static final String PASSWORD = "Str0ngPassw0rd!";

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ tiny HTTP + JSON helpers

    record Resp(int status, String body, java.net.http.HttpHeaders headers) {
        String field(String name) {
            Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?[0-9.]+|true|false|null))").matcher(body);
            if (!m.find()) {
                return null;
            }
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
    }

    private Resp call(String method, String path, String token, String key, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            b.header("Idempotency-Key", key);
        }
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers());
    }

    private Resp get(String path, String token) throws Exception {
        return call("GET", path, token, null, null);
    }

    private Resp post(String path, String token, String json) throws Exception {
        return call("POST", path, token, null, json);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.io";
    }

    /** Registers a customer and logs in. Returns {email, token, userId}. */
    private String[] newCustomer() throws Exception {
        String email = uniqueEmail("cust");
        Resp reg = post("/api/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Test Customer\"}");
        assertEquals(201, reg.status(), reg.body());
        Resp login = post("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(200, login.status(), login.body());
        return new String[] {email, login.field("accessToken"), reg.field("id")};
    }

    private String adminToken() throws Exception {
        Resp login = post("/api/auth/login", null, "{\"email\":\"" + System.getenv("LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL")
                + "\",\"password\":\"" + System.getenv("LEDGERLINE_BOOTSTRAP_ADMIN_PASSWORD") + "\"}");
        assertEquals(200, login.status(), "bootstrap admin login failed: " + login.body());
        return login.field("accessToken");
    }

    private String auditorToken() throws Exception {
        String email = uniqueEmail("aud");
        Resp create = post("/api/admin/users", adminToken(), "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Test Auditor\",\"role\":\"AUDITOR\"}");
        assertEquals(201, create.status(), create.body());
        return post("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .field("accessToken");
    }

    /** Creates an INR account and returns {id, accountNumber}. */
    private String[] newAccount(String token) throws Exception {
        Resp r = post("/api/accounts", token, "{\"currency\":\"INR\"}");
        assertEquals(201, r.status(), r.body());
        return new String[] {r.field("id"), r.field("accountNumber")};
    }

    private Resp deposit(String token, String accountId, String amount, String key) throws Exception {
        return call("POST", "/api/transactions/deposit", token, key,
                "{\"accountId\":\"" + accountId + "\",\"amount\":" + amount + ",\"currency\":\"INR\"}");
    }

    private Resp transfer(String token, String sourceId, String destNumber, String amount, String key) throws Exception {
        return call("POST", "/api/transactions/transfer", token, key, "{\"sourceAccountId\":\"" + sourceId
                + "\",\"destinationAccountNumber\":\"" + destNumber + "\",\"amount\":" + amount + ",\"currency\":\"INR\"}");
    }

    private BigDecimal balance(String token, String accountId) throws Exception {
        return new BigDecimal(get("/api/accounts/" + accountId, token).field("balance"));
    }

    private static String key() {
        return "k-" + UUID.randomUUID();
    }

    private static Connection jdbc() throws SQLException {
        return DriverManager.getConnection(System.getenv("LEDGERLINE_DB_URL"),
                System.getenv("LEDGERLINE_DB_USER"), System.getenv("LEDGERLINE_DB_PASSWORD"));
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = jdbc(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ------------------------------------------------------------------ authentication

    @Test
    @Order(1)
    void registrationAndLoginWork_andDuplicateEmailIsRejected() throws Exception {
        String[] c = newCustomer();
        assertNotNull(c[1]);
        Resp dup = post("/api/auth/register", null, "{\"email\":\"" + c[0].toUpperCase() + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Dup\"}");
        assertEquals(409, dup.status());
        assertEquals("EMAIL_TAKEN", dup.field("code"));
    }

    @Test
    @Order(2)
    void registrationCannotChooseARole_andResponseNeverContainsPasswordHash() throws Exception {
        String email = uniqueEmail("esc");
        Resp r = post("/api/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Sneaky\",\"role\":\"ADMIN\"}");
        assertEquals(201, r.status(), r.body());
        assertEquals("CUSTOMER", r.field("role"));
        assertFalse(r.body().toLowerCase().contains("password"), "response must not echo any password data");
    }

    @Test
    @Order(3)
    void invalidCredentialsGiveTheSameGenericError() throws Exception {
        String[] c = newCustomer();
        Resp wrongPassword = post("/api/auth/login", null, "{\"email\":\"" + c[0] + "\",\"password\":\"WrongPass123\"}");
        Resp unknownUser = post("/api/auth/login", null, "{\"email\":\"nobody-" + UUID.randomUUID() + "@test.io\",\"password\":\"WrongPass123\"}");
        assertEquals(401, wrongPassword.status());
        assertEquals(401, unknownUser.status());
        assertEquals(wrongPassword.field("message"), unknownUser.field("message"), "no user enumeration");
    }

    @Test
    @Order(4)
    void accountLocksAfterRepeatedFailures() throws Exception {
        String[] c = newCustomer();
        for (int i = 0; i < 5; i++) {
            post("/api/auth/login", null, "{\"email\":\"" + c[0] + "\",\"password\":\"WrongPass123\"}");
        }
        Resp correctButLocked = post("/api/auth/login", null, "{\"email\":\"" + c[0] + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(429, correctButLocked.status());
    }

    @Test
    @Order(5)
    void jwtValidation_missingTamperedAndAlgNoneTokensAreRejected() throws Exception {
        String[] c = newCustomer();
        assertEquals(401, get("/api/accounts", null).status());
        assertEquals(401, get("/api/accounts", "not.a.jwt").status());

        String token = c[1];
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertEquals(401, get("/api/accounts", tampered).status(), "tampered signature");

        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + c[2] + "\",\"iss\":\"ledgerline\",\"exp\":4102444800,\"jti\":\"x\"}").getBytes());
        assertEquals(401, get("/api/accounts", header + "." + payload + ".").status(), "alg=none must be refused");
    }

    @Test
    @Order(6)
    void logoutRevokesTheToken() throws Exception {
        String[] c = newCustomer();
        assertEquals(200, get("/api/accounts", c[1]).status());
        assertEquals(204, post("/api/auth/logout", c[1], null).status());
        assertEquals(401, get("/api/accounts", c[1]).status(), "token must be unusable after logout");
    }

    // ------------------------------------------------------------------ authorization

    @Test
    @Order(10)
    void roleBasedAccess_isEnforcedOnTheBackend() throws Exception {
        String[] c = newCustomer();
        String admin = adminToken();
        String auditor = auditorToken();

        // customer cannot reach admin / auditor / audit endpoints
        assertEquals(403, get("/api/admin/users", c[1]).status());
        assertEquals(403, get("/api/auditor/transactions", c[1]).status());
        assertEquals(403, get("/api/audit-events", c[1]).status());
        // customer cannot promote self
        assertEquals(403, call("PATCH", "/api/admin/users/" + c[2] + "/role", c[1], null, "{\"role\":\"ADMIN\"}").status());

        // admin and auditor are not customers: no access to customer money endpoints
        assertEquals(403, get("/api/accounts", admin).status());
        assertEquals(403, get("/api/accounts", auditor).status());

        // auditor can read but cannot write
        assertEquals(200, get("/api/auditor/transactions", auditor).status());
        assertEquals(200, get("/api/audit-events", auditor).status());
        assertEquals(403, get("/api/admin/users", auditor).status());
        assertEquals(403, post("/api/admin/users", auditor, "{}").status());
        int del = call("DELETE", "/api/audit-events", auditor, null, null).status();
        assertTrue(del == 403 || del == 405, "audit events cannot be deleted over HTTP, got " + del);

        // admin can list users
        assertEquals(200, get("/api/admin/users", admin).status());
    }

    @Test
    @Order(11)
    void customerCannotReadAnotherCustomersAccountOrTransactions_IDOR() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] aliceAcc = newAccount(alice[1]);
        Resp dep = deposit(alice[1], aliceAcc[0], "100.00", key());
        assertEquals(201, dep.status(), dep.body());

        assertEquals(404, get("/api/accounts/" + aliceAcc[0], bob[1]).status());
        assertEquals(404, get("/api/accounts/" + aliceAcc[0] + "/ledger", bob[1]).status());
        assertEquals(404, get("/api/transactions/" + dep.field("id"), bob[1]).status());
        // Bob cannot deposit into / withdraw from / transfer out of Alice's account by passing her id
        assertEquals(404, deposit(bob[1], aliceAcc[0], "1.00", key()).status());
        String[] bobAcc = newAccount(bob[1]);
        assertEquals(404, transfer(bob[1], aliceAcc[0], bobAcc[1], "1.00", key()).status());
        assertEquals(new BigDecimal("100.00"), balance(alice[1], aliceAcc[0]));
        // and Bob's history does not contain Alice's transaction
        assertFalse(get("/api/transactions", bob[1]).body().contains(dep.field("id")));
    }

    // ------------------------------------------------------------------ financial operations

    @Test
    @Order(20)
    void depositWithdrawAndTransfer_updateBalances_andWriteDoubleEntryLedger() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);

        assertEquals(201, deposit(alice[1], a[0], "500.00", key()).status());
        assertEquals(new BigDecimal("500.00"), balance(alice[1], a[0]));

        Resp t = transfer(alice[1], a[0], b[1], "125.50", key());
        assertEquals(201, t.status(), t.body());
        assertEquals("COMPLETED", t.field("status"));
        assertEquals(new BigDecimal("374.50"), balance(alice[1], a[0]));
        assertEquals(new BigDecimal("125.50"), balance(bob[1], b[0]));

        Resp w = call("POST", "/api/transactions/withdraw", bob[1], key(),
                "{\"accountId\":\"" + b[0] + "\",\"amount\":25.50,\"currency\":\"INR\"}");
        assertEquals(201, w.status(), w.body());
        assertEquals(new BigDecimal("100.00"), balance(bob[1], b[0]));

        // exactly one DEBIT and one CREDIT with equal amounts for the transfer
        UUID txId = UUID.fromString(t.field("id"));
        try (Connection c = jdbc(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), "
                     + "count(*) FILTER (WHERE entry_type='DEBIT'), count(*) FILTER (WHERE entry_type='CREDIT'), "
                     + "sum(amount) FILTER (WHERE entry_type='DEBIT') - sum(amount) FILTER (WHERE entry_type='CREDIT') "
                     + "FROM ledger_entries WHERE transaction_id = '" + txId + "'")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
            assertEquals(1, rs.getInt(2));
            assertEquals(1, rs.getInt(3));
            assertEquals(0, rs.getBigDecimal(4).compareTo(BigDecimal.ZERO));
        }
        // the platform-wide integrity report (recomputed from the raw ledger) says balanced
        Resp integrity = get("/api/auditor/ledger-integrity", auditorToken());
        assertEquals(200, integrity.status());
        assertEquals("true", integrity.field("balanced"), integrity.body());
    }

    @Test
    @Order(21)
    void invalidRequestsAreRejected_andNothingChanges() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);
        assertEquals(201, deposit(alice[1], a[0], "50.00", key()).status());
        long ledgerBefore = count("SELECT count(*) FROM ledger_entries");
        long txBefore = count("SELECT count(*) FROM financial_transactions");

        Resp insufficient = transfer(alice[1], a[0], b[1], "50.01", key());
        assertEquals(422, insufficient.status());
        assertEquals("INSUFFICIENT_FUNDS", insufficient.field("code"));
        assertEquals(400, transfer(alice[1], a[0], b[1], "-5", key()).status(), "negative amount");
        assertEquals(400, transfer(alice[1], a[0], b[1], "0", key()).status(), "zero amount");
        assertEquals(400, transfer(alice[1], a[0], b[1], "1.005", key()).status(), "more than 2 decimals");
        assertEquals(422, transfer(alice[1], a[0], a[1], "1.00", key()).status(), "same account");
        assertEquals(422, transfer(alice[1], a[0], "LL999999999999", "1.00", key()).status(), "unknown destination");
        assertEquals(400, call("POST", "/api/transactions/transfer", alice[1], "short",
                "{\"sourceAccountId\":\"" + a[0] + "\",\"destinationAccountNumber\":\"" + b[1]
                        + "\",\"amount\":1,\"currency\":\"INR\"}").status(), "bad idempotency key");
        assertEquals(400, call("POST", "/api/transactions/transfer", alice[1], null,
                "{\"sourceAccountId\":\"" + a[0] + "\",\"destinationAccountNumber\":\"" + b[1]
                        + "\",\"amount\":1,\"currency\":\"INR\"}").status(), "missing idempotency key");
        Resp wrongCurrency = call("POST", "/api/transactions/transfer", alice[1], key(),
                "{\"sourceAccountId\":\"" + a[0] + "\",\"destinationAccountNumber\":\"" + b[1]
                        + "\",\"amount\":1,\"currency\":\"USD\"}");
        assertEquals(422, wrongCurrency.status());

        // rollback: no balance moved, no transaction, no ledger row
        assertEquals(new BigDecimal("50.00"), balance(alice[1], a[0]));
        assertEquals(new BigDecimal("0.00"), balance(bob[1], b[0]));
        assertEquals(ledgerBefore, count("SELECT count(*) FROM ledger_entries"));
        assertEquals(txBefore, count("SELECT count(*) FROM financial_transactions"));
    }

    @Test
    @Order(22)
    void frozenAccountCannotMoveMoney() throws Exception {
        String[] alice = newCustomer();
        String[] a = newAccount(alice[1]);
        assertEquals(201, deposit(alice[1], a[0], "10.00", key()).status());
        Resp freeze = call("PATCH", "/api/admin/accounts/" + a[0] + "/status", adminToken(), null, "{\"status\":\"FROZEN\"}");
        assertEquals(200, freeze.status(), freeze.body());
        Resp blocked = deposit(alice[1], a[0], "10.00", key());
        assertEquals(422, blocked.status());
        assertEquals("ACCOUNT_NOT_ACTIVE", blocked.field("code"));
    }

    // ------------------------------------------------------------------ idempotency + concurrency

    @Test
    @Order(30)
    void duplicateIdempotencyKey_returnsOriginalResult_andMovesMoneyOnce() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);
        assertEquals(201, deposit(alice[1], a[0], "100.00", key()).status());

        String k = key();
        Resp first = transfer(alice[1], a[0], b[1], "30.00", k);
        Resp retry = transfer(alice[1], a[0], b[1], "30.00", k);
        assertEquals(201, first.status());
        assertEquals(200, retry.status());
        assertEquals("true", retry.headers().firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(first.field("id"), retry.field("id"));
        assertEquals(new BigDecimal("70.00"), balance(alice[1], a[0]), "debited exactly once");

        Resp reused = transfer(alice[1], a[0], b[1], "31.00", k);
        assertEquals(422, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.field("code"));
    }

    @Test
    @Order(31)
    void concurrentRequestsWithTheSameKey_createOnlyOneTransaction() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);
        assertEquals(201, deposit(alice[1], a[0], "100.00", key()).status());

        String k = key();
        List<Callable<Resp>> jobs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            jobs.add(() -> transfer(alice[1], a[0], b[1], "10.00", k));
        }
        List<Resp> results = runAll(jobs);
        long created = results.stream().filter(r -> r.status() == 201).count();
        long replayed = results.stream().filter(r -> r.status() == 200).count();
        assertEquals(1, created, "exactly one request creates the transaction");
        assertEquals(7, replayed, "the others get the original result");
        assertEquals(new BigDecimal("90.00"), balance(alice[1], a[0]));
        assertEquals(new BigDecimal("10.00"), balance(bob[1], b[0]));
    }

    @Test
    @Order(32)
    void concurrentTransfers_neverOverdraw_andBalancesStayConsistent() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);
        assertEquals(201, deposit(alice[1], a[0], "50.00", key()).status());

        List<Callable<Resp>> jobs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            jobs.add(() -> transfer(alice[1], a[0], b[1], "10.00", key()));   // 12 x 10 = 120 > 50
        }
        List<Resp> results = runAll(jobs);
        long ok = results.stream().filter(r -> r.status() == 201).count();
        long refused = results.stream().filter(r -> r.status() == 422).count();
        assertEquals(5, ok, "only 5 x 10.00 can be afforded from 50.00");
        assertEquals(7, refused);
        assertEquals(new BigDecimal("0.00"), balance(alice[1], a[0]));
        assertEquals(new BigDecimal("50.00"), balance(bob[1], b[0]));
        assertEquals("true", get("/api/auditor/ledger-integrity", auditorToken()).field("balanced"));
    }

    @Test
    @Order(33)
    void oppositeConcurrentTransfers_doNotDeadlock() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String[] a = newAccount(alice[1]);
        String[] b = newAccount(bob[1]);
        assertEquals(201, deposit(alice[1], a[0], "100.00", key()).status());
        assertEquals(201, deposit(bob[1], b[0], "100.00", key()).status());

        List<Callable<Resp>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            jobs.add(() -> transfer(alice[1], a[0], b[1], "5.00", key()));
            jobs.add(() -> transfer(bob[1], b[0], a[1], "5.00", key()));
        }
        List<Resp> results = runAll(jobs);
        assertTrue(results.stream().allMatch(r -> r.status() == 201), "all succeed, none deadlock or time out");
        assertEquals(new BigDecimal("100.00"), balance(alice[1], a[0]));
        assertEquals(new BigDecimal("100.00"), balance(bob[1], b[0]));
    }

    private List<Resp> runAll(List<Callable<Resp>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            List<Future<Resp>> futures = pool.invokeAll(jobs);
            List<Resp> out = new ArrayList<>();
            for (Future<Resp> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ audit trail

    @Test
    @Order(40)
    void auditEvents_areCreated_visibleToAuditors_andNeverContainSecrets() throws Exception {
        String[] alice = newCustomer();
        String[] a = newAccount(alice[1]);
        assertEquals(201, deposit(alice[1], a[0], "10.00", key()).status());
        post("/api/auth/login", null, "{\"email\":\"" + alice[0] + "\",\"password\":\"WrongPass123\"}");
        get("/api/accounts/" + UUID.randomUUID(), alice[1]);

        String auditor = auditorToken();
        String body = get("/api/audit-events?size=100", auditor).body();
        assertTrue(count("SELECT count(*) FROM audit_events WHERE action = 'LOGIN_SUCCESS'") > 0);
        assertTrue(count("SELECT count(*) FROM audit_events WHERE action = 'LOGIN_FAILURE'") > 0);
        assertTrue(count("SELECT count(*) FROM audit_events WHERE action = 'ACCOUNT_CREATED'") > 0);
        assertTrue(count("SELECT count(*) FROM audit_events WHERE action = 'DEPOSIT'") > 0);
        assertTrue(count("SELECT count(*) FROM audit_events WHERE action = 'USER_REGISTERED'") > 0);
        assertFalse(body.contains(PASSWORD), "audit trail must never contain passwords");
        assertFalse(count("SELECT count(*) FROM audit_events WHERE metadata ILIKE '%" + PASSWORD + "%'") > 0);
        assertEquals(403, get("/api/audit-events", alice[1]).status(), "customers cannot read the audit trail");
    }

    @Test
    @Order(41)
    void auditAndLedgerTables_areImmutableEvenWithDirectDatabaseAccess() throws Exception {
        String[] alice = newCustomer();
        String[] a = newAccount(alice[1]);
        assertEquals(201, deposit(alice[1], a[0], "10.00", key()).status());
        for (String sql : List.of(
                "UPDATE audit_events SET action = 'TAMPERED'",
                "DELETE FROM audit_events",
                "TRUNCATE audit_events",
                "UPDATE ledger_entries SET amount = 1",
                "DELETE FROM ledger_entries",
                "UPDATE financial_transactions SET amount = 1")) {
            try (Connection c = jdbc(); Statement s = c.createStatement()) {
                s.execute(sql);
                throw new AssertionError("statement should have been blocked: " + sql);
            } catch (SQLException expected) {
                assertEquals("42501", expected.getSQLState(), sql);
            }
        }
    }
}
