package com.vtoroy.entity

import com.fasterxml.jackson.databind.JsonNode
import com.vtoroy.config.PGVectorType
import com.pgvector.PGvector
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "knowledge_files", 
    indexes = [
        Index(name = "idx_knowledge_source", columnList = "source"),
        Index(name = "idx_knowledge_source_id", columnList = "source_id")
    ]
)
data class KnowledgeFile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "source", nullable = false, length = 50)
    val source: String = "obsidian", // Knowledge source identifier (obsidian, notion, etc.)
    
    @Column(name = "source_id", nullable = false, length = 255)
    val sourceId: String, // Unique ID within the source
    
    @Column(name = "file_path", nullable = false, length = 500)
    val filePath: String, // Now represents path/location within the source
    
    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,
    
    @Type(PGVectorType::class)
    @Column(columnDefinition = "vector(384)")
    var embedding: PGvector? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: JsonNode? = null,
    
    @Column(name = "file_hash", nullable = false, length = 64)
    val fileHash: String,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)