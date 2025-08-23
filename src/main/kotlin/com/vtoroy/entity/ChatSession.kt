package com.vtoroy.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "chat_sessions")
data class ChatSession(
    @Id
    @Column(length = 100)
    val id: String,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_context", columnDefinition = "jsonb")
    val userContext: JsonNode? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "last_active_at")
    var lastActiveAt: LocalDateTime = LocalDateTime.now(),
    
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val messages: MutableList<ChatMessage> = mutableListOf()
)