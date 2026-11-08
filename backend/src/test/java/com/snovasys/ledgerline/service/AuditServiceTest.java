package com.snovasys.ledgerline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditEvent;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.repository.AuditEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditServiceTest {

    @Test
    void sensitiveKeysNeverReachTheAuditTrail() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("reference", "TXN-1");
        meta.put("password", "hunter2");
        meta.put("newPassword", "hunter3");
        meta.put("accessToken", "eyJ...");
        meta.put("jwtSecret", "s3cret");
        meta.put("Authorization", "Bearer abc");
        meta.put("passwordHash", "$2a$...");

        String json = AuditService.toJson(meta);

        assertEquals("{\"reference\":\"TXN-1\"}", json);
        assertFalse(json.contains("hunter"));
        assertFalse(json.contains("eyJ"));
    }

    @Test
    void valuesAreEscapedSoMetadataStaysValidJson() {
        String json = AuditService.toJson(Map.of("path", "/api/\"x\"\n\\"));
        assertEquals("{\"path\":\"/api/\\\"x\\\"\\u000a\\\\\"}", json);
    }

    @Test
    void emptyOrNullMetadataGivesNull() {
        assertNull(AuditService.toJson(null));
        assertNull(AuditService.toJson(Map.of()));
    }

    @Test
    void longValuesAreTruncated() {
        String json = AuditService.toJson(Map.of("note", "x".repeat(1000)));
        assertTrue(json.length() < 260);
    }

    @Test
    void recordStoresActorActionTargetAndOutcome() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditService service = new AuditService(repository);
        UUID actorId = UUID.randomUUID();

        service.record(AuditAction.TRANSFER, AuditOutcome.SUCCESS,
                new AuditActor(actorId, "a@example.com", "CUSTOMER"), "TRANSACTION", "tx-1", Map.of("amount", "5.00"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertEquals(actorId, saved.getActorUserId());
        assertEquals("a@example.com", saved.getActorEmail());
        assertEquals("CUSTOMER", saved.getActorRole());
        assertEquals("TRANSFER", saved.getAction());
        assertEquals(AuditOutcome.SUCCESS, saved.getOutcome());
        assertEquals("TRANSACTION", saved.getTargetType());
        assertEquals("tx-1", saved.getTargetId());
        assertEquals("{\"amount\":\"5.00\"}", saved.getMetadata());
    }

    @Test
    void auditRepositoryOffersNoDeleteOrUpdateMethods() {
        for (var method : AuditEventRepository.class.getMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("delete") || name.contains("remove") || name.contains("update"),
                    "AuditEventRepository must stay append-only, found: " + method.getName());
        }
        verifyNoUnexpected(mock(AuditEventRepository.class));
    }

    private static void verifyNoUnexpected(AuditEventRepository repository) {
        // a mock that was never called must not have saved anything
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
