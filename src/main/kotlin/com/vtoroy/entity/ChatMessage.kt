package com.vtoroy.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "chat_messages")
data class ChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    val session: ChatSession,
    
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val role: MessageRole,
    
    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: JsonNode? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    FUNCTION
}