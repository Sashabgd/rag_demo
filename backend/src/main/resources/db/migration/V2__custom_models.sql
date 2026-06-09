CREATE TABLE custom_models (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    base_url    VARCHAR(1024) NOT NULL,
    model_name  VARCHAR(255) NOT NULL,
    api_key     VARCHAR(1024),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
