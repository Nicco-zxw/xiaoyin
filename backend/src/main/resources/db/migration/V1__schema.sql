CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE template_categories (id VARCHAR(50) PRIMARY KEY, name VARCHAR(100) NOT NULL, sort_order INT NOT NULL);
CREATE TABLE templates (
 id VARCHAR(60) PRIMARY KEY, category_id VARCHAR(50) NOT NULL, title VARCHAR(100) NOT NULL,
 description VARCHAR(500) NOT NULL, cover_url VARCHAR(200) NOT NULL,
 required_image_count INT NOT NULL, max_file_size_bytes BIGINT NOT NULL DEFAULT 5242880,
 FOREIGN KEY (category_id) REFERENCES template_categories(id)
);
CREATE TABLE assets (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, path VARCHAR(300) NOT NULL,
 mime_type VARCHAR(50) NOT NULL, size_bytes BIGINT NOT NULL, width INT NOT NULL, height INT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE creation_tasks (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, template_id VARCHAR(60) NOT NULL,
 template_title VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL, current_stage VARCHAR(30),
 failure_code VARCHAR(60), failure_message VARCHAR(500), result_path VARCHAR(300), simulation_json JSON NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), started_at TIMESTAMP(3), finished_at TIMESTAMP(3),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(template_id) REFERENCES templates(id),
 INDEX idx_user_created(user_id, created_at, id), INDEX idx_status(status)
);
CREATE TABLE task_assets (
 task_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL, slot_index INT NOT NULL,
 PRIMARY KEY(task_id, slot_index), FOREIGN KEY(task_id) REFERENCES creation_tasks(id), FOREIGN KEY(asset_id) REFERENCES assets(id)
);
CREATE TABLE task_step_runs (
 task_id VARCHAR(36) NOT NULL, stage VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
 started_at TIMESTAMP(3), finished_at TIMESTAMP(3), PRIMARY KEY(task_id, stage), FOREIGN KEY(task_id) REFERENCES creation_tasks(id)
);
