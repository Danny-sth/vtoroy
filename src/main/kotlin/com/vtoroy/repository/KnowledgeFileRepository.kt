package com.vtoroy.repository

import com.vtoroy.entity.KnowledgeFile
import com.pgvector.PGvector
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface KnowledgeFileRepository : JpaRepository<KnowledgeFile, Long> {
    
    fun findByFilePath(filePath: String): Optional<KnowledgeFile>
    
    fun existsByFilePath(filePath: String): Boolean
    
    fun findBySourceAndSourceId(source: String, sourceId: String): KnowledgeFile?
    
    fun findBySource(source: String): List<KnowledgeFile>
    
    fun countBySource(source: String): Long
    
    @Query(
        value = """
            SELECT k.*, k.embedding <=> CAST(?1 AS vector) as distance
            FROM knowledge_files k
            WHERE k.embedding IS NOT NULL
            ORDER BY k.embedding <=> CAST(?1 AS vector)
            LIMIT ?2
        """,
        nativeQuery = true
    )
    fun findSimilarDocuments(
        @Param("queryVector") queryVector: String,
        @Param("limit") limit: Int
    ): List<KnowledgeFile>
    
    @Query(
        value = """
            SELECT k.*, k.embedding <=> CAST(?1 AS vector) as distance
            FROM knowledge_files k
            WHERE k.embedding IS NOT NULL
            AND k.source = ?2
            ORDER BY k.embedding <=> CAST(?1 AS vector)
            LIMIT ?3
        """,
        nativeQuery = true
    )
    fun findSimilarDocumentsBySource(
        @Param("queryVector") queryVector: String,
        @Param("source") source: String,
        @Param("limit") limit: Int
    ): List<KnowledgeFile>
    
    @Query(
        value = """
            SELECT k.*, k.embedding <=> CAST(?1 AS vector) as distance
            FROM knowledge_files k
            WHERE k.embedding IS NOT NULL
            AND k.embedding <=> CAST(?1 AS vector) < ?2
            ORDER BY k.embedding <=> CAST(?1 AS vector)
            LIMIT ?3
        """,
        nativeQuery = true
    )
    fun findSimilarDocumentsWithThreshold(
        @Param("queryVector") queryVector: String,
        @Param("threshold") threshold: Float,
        @Param("limit") limit: Int
    ): List<KnowledgeFile>
}