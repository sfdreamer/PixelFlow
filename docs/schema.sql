-- PixelFlow MySQL 8.4 schema draft, v1.0, 2026-11-13.
-- Convert to Flyway V1__schema.sql during implementation. No DROP or cascading deletion.
-- All DATETIME values are UTC. IDs are application-generated UUIDs.
SET NAMES utf8mb4;

CREATE TABLE app_user (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_session (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_session_token (token_hash),
  KEY idx_session_user (user_id),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE category (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE template (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  category_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title VARCHAR(120) NOT NULL,
  description TEXT NOT NULL,
  cover_storage_key VARCHAR(255) NOT NULL,
  demo_result_key VARCHAR(255) NOT NULL,
  required_image_count INT NOT NULL,
  accepted_mime_types JSON NOT NULL,
  max_file_size_bytes BIGINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  KEY idx_template_category (category_id, active, sort_order),
  CONSTRAINT fk_template_category FOREIGN KEY (category_id) REFERENCES category(id),
  CONSTRAINT ck_template_count CHECK (required_image_count BETWEEN 1 AND 10),
  CONSTRAINT ck_template_size CHECK (max_file_size_bytes > 0 AND max_file_size_bytes <= 5242880)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  template_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  storage_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_asset_storage (storage_key),
  KEY idx_asset_user (user_id, created_at),
  CONSTRAINT fk_asset_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_asset_template FOREIGN KEY (template_id) REFERENCES template(id),
  CONSTRAINT ck_asset_size CHECK (size_bytes > 0),
  CONSTRAINT ck_asset_dimensions CHECK (width > 0 AND height > 0 AND CAST(width AS UNSIGNED) * height <= 20000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  template_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  template_snapshot JSON NOT NULL,
  simulation_snapshot JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  current_stage VARCHAR(20) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  KEY idx_task_history (user_id, created_at DESC, id DESC),
  KEY idx_task_template_history (user_id, template_id, created_at DESC, id DESC),
  KEY idx_task_queue (status, created_at, id),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES template(id),
  CONSTRAINT ck_task_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT ck_task_stage CHECK (current_stage IS NULL OR current_stage IN ('ASSET_CHECK','GENERATE','FINALIZE')),
  CONSTRAINT ck_task_revision CHECK (revision >= 0),
  CONSTRAINT ck_task_terminal_time CHECK (
    (status IN ('QUEUED','RUNNING') AND finished_at IS NULL) OR
    (status IN ('SUCCEEDED','FAILED') AND finished_at IS NOT NULL)
  ),
  CONSTRAINT ck_task_error CHECK (
    (status = 'FAILED' AND error_code IS NOT NULL AND error_message IS NOT NULL) OR
    (status <> 'FAILED' AND error_code IS NULL AND error_message IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_asset (
  task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  slot_index INT NOT NULL,
  asset_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (task_id, slot_index),
  UNIQUE KEY uk_task_asset (task_id, asset_id),
  CONSTRAINT fk_task_asset_task FOREIGN KEY (task_id) REFERENCES task(id),
  CONSTRAINT fk_task_asset_asset FOREIGN KEY (asset_id) REFERENCES asset(id),
  CONSTRAINT ck_task_asset_slot CHECK (slot_index >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_step (
  task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  stage VARCHAR(20) NOT NULL,
  step_order INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  PRIMARY KEY (task_id, stage),
  UNIQUE KEY uk_step_order (task_id, step_order),
  CONSTRAINT fk_step_task FOREIGN KEY (task_id) REFERENCES task(id),
  CONSTRAINT ck_step_stage CHECK (stage IN ('ASSET_CHECK','GENERATE','FINALIZE')),
  CONSTRAINT ck_step_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED')),
  CONSTRAINT ck_step_order CHECK (
    (stage='ASSET_CHECK' AND step_order=1) OR
    (stage='GENERATE' AND step_order=2) OR
    (stage='FINALIZE' AND step_order=3)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_result (
  task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  storage_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mime_type VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_result_storage (storage_key),
  CONSTRAINT fk_result_task FOREIGN KEY (task_id) REFERENCES task(id),
  CONSTRAINT ck_result_size CHECK (size_bytes > 0),
  CONSTRAINT ck_result_dimensions CHECK (width > 0 AND height > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cross-table ownership, asset count, transitions and terminal-result consistency
-- are enforced in application transactions and integration tests.
-- Step timestamps: PENDING => both NULL; RUNNING => start only;
-- SUCCEEDED/FAILED => both set; SKIPPED => start NULL, finish set when skipped.
