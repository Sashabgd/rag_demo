CREATE TABLE embedding_models (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    base_url    VARCHAR(1024) NOT NULL,
    model_name  VARCHAR(255) NOT NULL,
    api_key     VARCHAR(1024),
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Which embedding model / Redis index each document was embedded with
ALTER TABLE documents ADD COLUMN embedding_model VARCHAR(255);
ALTER TABLE documents ADD COLUMN embedding_index VARCHAR(255);

-- Everything embedded so far used the local e5 model and the rag_demo index
UPDATE documents SET embedding_model = 'e5-small (lokalni)', embedding_index = 'rag_demo';
