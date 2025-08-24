-- V1: Complete database setup
-- Author: Vtoroy System
-- Date: 2025-08-20

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create knowledge_files table
CREATE TABLE knowledge_files (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(500) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    embedding vector(384),
    metadata JSONB,
    file_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create chat_sessions table
CREATE TABLE chat_sessions (
    id VARCHAR(100) PRIMARY KEY,
    user_context JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create chat_messages table
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'FUNCTION')),
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_knowledge_files_embedding 
ON knowledge_files 
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

CREATE INDEX idx_knowledge_files_path ON knowledge_files(file_path);
CREATE INDEX idx_knowledge_files_metadata ON knowledge_files USING gin(metadata);
CREATE INDEX idx_chat_messages_session ON chat_messages(session_id, created_at DESC);
CREATE INDEX idx_chat_sessions_active ON chat_sessions(last_active_at DESC);

-- Function to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for auto-updating timestamps
CREATE TRIGGER update_knowledge_files_updated_at BEFORE UPDATE ON knowledge_files
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    
CREATE TRIGGER update_chat_sessions_updated_at BEFORE UPDATE ON chat_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();