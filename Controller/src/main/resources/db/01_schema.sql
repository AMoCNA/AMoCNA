use poseidon_db;
CREATE TABLE IF NOT EXISTS `action` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action_name VARCHAR(255) NOT NULL,
    perform_time DATETIME,
    state VARCHAR(50) NOT NULL,
    success_perform_time DATETIME,
    last_try_time DATETIME,
    idempotency_id VARCHAR(1000),
    priority INT,
    parent_id BIGINT,
    sharing_counter BIGINT NOT NULL DEFAULT 0,
    arguments VARCHAR(10000),
    PRIMARY KEY (id)
);