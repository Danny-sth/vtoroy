package com.jarvis.repository

import com.jarvis.entity.ChatSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ChatSessionRepository : JpaRepository<ChatSession, String> {
    
    @Modifying
    @Query("UPDATE ChatSession c SET c.lastActiveAt = :now WHERE c.id = :sessionId")
    fun updateLastActiveAt(@Param("sessionId") sessionId: String, @Param("now") now: LocalDateTime)
    
    @Query("SELECT c FROM ChatSession c WHERE c.lastActiveAt < :threshold")
    fun findInactiveSessions(@Param("threshold") threshold: LocalDateTime): List<ChatSession>
}