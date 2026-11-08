package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.RevokedToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
