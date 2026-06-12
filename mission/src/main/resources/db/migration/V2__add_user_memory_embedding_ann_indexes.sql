-- =============================================================
-- Mission RAG - User Memory Embedding Search Indexes
-- =============================================================

CREATE INDEX idx_user_memory_embeddings_rag_filter
    ON user_memory_embeddings(user_id, embedding_model, embedding_dimension)
    WHERE status = 'COMPLETED'
      AND embedding IS NOT NULL;

CREATE INDEX idx_user_memory_embeddings_embedding_hnsw
    ON user_memory_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE status = 'COMPLETED'
      AND embedding IS NOT NULL;

