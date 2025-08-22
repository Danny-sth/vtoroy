-- Add source fields to knowledge_files table for multiple knowledge sources support

-- Enable pgcrypto extension for digest function (if not already enabled)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add new columns
ALTER TABLE knowledge_files 
ADD COLUMN IF NOT EXISTS source VARCHAR(50) NOT NULL DEFAULT 'obsidian',
ADD COLUMN IF NOT EXISTS source_id VARCHAR(255);

-- Update existing records to have source_id based on file_path hash
UPDATE knowledge_files 
SET source_id = encode(digest(file_path, 'md5'), 'hex')
WHERE source_id IS NULL;

-- Make source_id NOT NULL after setting values
ALTER TABLE knowledge_files 
ALTER COLUMN source_id SET NOT NULL;

-- Remove unique constraint on file_path if exists
ALTER TABLE knowledge_files 
DROP CONSTRAINT IF EXISTS knowledge_files_file_path_key;

-- Add unique constraint on source + source_id combination
ALTER TABLE knowledge_files 
ADD CONSTRAINT unique_source_item UNIQUE (source, source_id);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_knowledge_source ON knowledge_files(source);
CREATE INDEX IF NOT EXISTS idx_knowledge_source_id ON knowledge_files(source_id);

-- Add comment explaining the fields
COMMENT ON COLUMN knowledge_files.source IS 'Knowledge source identifier (obsidian, notion, etc.)';
COMMENT ON COLUMN knowledge_files.source_id IS 'Unique identifier within the source';
COMMENT ON COLUMN knowledge_files.file_path IS 'Path or location within the source (for display purposes)';