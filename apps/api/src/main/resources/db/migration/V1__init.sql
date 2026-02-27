CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
  id BIGSERIAL PRIMARY KEY,
  title TEXT,
  source TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chunks (
  id BIGSERIAL PRIMARY KEY,
  doc_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index INT NOT NULL,
  text TEXT,
  metadata JSONB,
  embedding VECTOR(384)
);

CREATE INDEX IF NOT EXISTS idx_chunks_doc_id ON chunks(doc_id);

-- Optional ANN index for vector similarity search.
-- Note: run ANALYZE on chunks after loading data so the planner can use ivfflat effectively.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_ivfflat
  ON chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
