CREATE TABLE short_links (
                             id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             code       VARCHAR(16)   NOT NULL,
                             target_url VARCHAR(2048) NOT NULL,
                             created_at TIMESTAMPTZ   NOT NULL,
                             CONSTRAINT uk_short_links_code UNIQUE (code)
);
