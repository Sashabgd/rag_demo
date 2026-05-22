CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(512) NOT NULL,
    file_type       VARCHAR(32) NOT NULL,
    file_size       BIGINT NOT NULL,
    full_text       TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    uploaded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    parsed_at       TIMESTAMP,
    chunked_at      TIMESTAMP,
    embedded_at     TIMESTAMP
);

CREATE TABLE document_chunks (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    start_index     INTEGER NOT NULL,
    end_index       INTEGER NOT NULL,
    vector_store_id VARCHAR(128),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
