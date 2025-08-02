package com.MyProject.identity.identity_service.repository;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.MyProject.identity.identity_service.entity.InvalidatedToken;

public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InvalidatedToken t WHERE t.expiryTime < :date")
    int deleteAllByExpiryTimeBefore(@Param("date") Date date);
}
