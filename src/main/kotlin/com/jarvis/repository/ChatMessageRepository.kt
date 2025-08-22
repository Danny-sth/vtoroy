package com.jarvis.repository

import com.jarvis.entity.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdAt DESC")
    fun findBySessionIdOrderByCreatedAtDesc(
        @Param("sessionId") sessionId: String,
        pageable: Pageable
    ): List<ChatMessage>
    
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdAt DESC")
    fun findBySessionIdOrderByCreatedAtDesc(
        @Param("sessionId") sessionId: String
    ): List<ChatMessage>
    
    fun countBySessionId(sessionId: String): Long
}