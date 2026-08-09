-- ═══════════════════════════════════════════════════════════════════════════
-- Lensora Studio - schema.sql
-- Executed once at startup via DatabaseManager.initializeDatabase().
-- All statements use IF NOT EXISTS / IF NOT EXISTS so re-runs are safe.
-- ═══════════════════════════════════════════════════════════════════════════

PRAGMA foreign_keys = ON;

-- Project
CREATE TABLE IF NOT EXISTS project (
                                                project_id     INTEGER         PRIMARY KEY AUTOINCREMENT,
                                                project_number VARCHAR (30)    NOT NULL    UNIQUE,
                                                client_name    VARCHAR (200)   NOT NULL,
                                                client_phone   VARCHAR (20),
                                                client_email   VARCHAR (255),
                                                event_type     VARCHAR (50)    NOT NULL,
                                                event_date     DATE            NOT NULL,
                                                due_date       DATE,
                                                project_status VARCHAR (50)    NOT NULL,
                                                project_path   VARCHAR (1000)  NOT NULL,
                                                package_name   VARCHAR (100),
                                                total_amount   DECIMAL (12, 2),
                                                advance_amount DECIMAL (12, 2),
                                                balance_amount DECIMAL (12, 2),
                                                remarks        TEXT,
                                                created_at     TIMESTAMP       NOT NULL,
                                                updated_at     TIMESTAMP       NOT NULL
);

-- project indexes
CREATE INDEX IF NOT EXISTS idx_project_number ON project(project_number);
CREATE INDEX IF NOT EXISTS idx_project_client ON project(client_name);
CREATE INDEX IF NOT EXISTS idx_project_client_phone ON project(client_phone);
CREATE INDEX IF NOT EXISTS idx_project_client_email ON project(client_email);
CREATE INDEX IF NOT EXISTS idx_project_status ON project(project_status);
CREATE INDEX IF NOT EXISTS idx_project_event_date ON project(event_date);


-- Project Note
CREATE TABLE IF NOT EXISTS project_note (
                                                note_id      INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                project_id   INTEGER       NOT NULL,
                                                note_title   VARCHAR (200),
                                                note_content TEXT          NOT NULL,
                                                created_at   TIMESTAMP     NOT NULL,
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id)
);

-- project note indexes
CREATE INDEX IF NOT EXISTS idx_note_project ON project_note(project_id);

-- project tag
CREATE TABLE IF NOT EXISTS project_tag (
                                                project_id INTEGER NOT NULL,
                                                tag_id     INTEGER NOT NULL,
                                                PRIMARY KEY (
                                                        project_id,
                                                        tag_id
                                                ),
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id),
                                                FOREIGN KEY (
                                                        tag_id
                                                )
                                                REFERENCES tag (tag_id)
);


-- reminder
CREATE TABLE IF NOT EXISTS reminder (
                                                reminder_id          INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                project_id           INTEGER       NOT NULL,
                                                reminder_title       VARCHAR (200) NOT NULL,
                                                reminder_description TEXT,
                                                reminder_date        DATE          NOT NULL,
                                                is_completed         BOOLEAN       NOT NULL
                                                DEFAULT 0,
                                                created_at           TIMESTAMP     NOT NULL,
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id)
);

-- reminder indexes
CREATE INDEX IF NOT EXISTS idx_reminder_project   ON reminder(project_id);
CREATE INDEX IF NOT EXISTS idx_reminder_date      ON reminder(reminder_date);
CREATE INDEX IF NOT EXISTS idx_reminder_completed ON reminder(is_completed);

-- tag
CREATE TABLE IF NOT EXISTS tag (
                                                tag_id   INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                tag_name VARCHAR (100) UNIQUE
                                                NOT NULL
);

-- deliverable
CREATE TABLE IF NOT EXISTS deliverable (
                                                deliverable_id   INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                project_id       INTEGER       NOT NULL,
                                                deliverable_type VARCHAR (100),
                                                quantity         INTEGER,
                                                status           VARCHAR (50),
                                                delivery_date    DATE,
                                                remarks          TEXT,
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id)
);

-- deliverable indexes
CREATE INDEX IF NOT EXISTS idx_deliverable_project ON deliverable(project_id);

-- folder template
CREATE TABLE IF NOT EXISTS folder_template (
                                                template_id   INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                template_name VARCHAR (100) UNIQUE NOT NULL,
                                                description   TEXT
);


-- folder template item
CREATE TABLE IF NOT EXISTS folder_template_item (
                                                item_id     INTEGER       PRIMARY KEY AUTOINCREMENT,
                                                template_id INTEGER       NOT NULL,
                                                folder_name VARCHAR (255) NOT NULL,
                                                sequence_no INTEGER       NOT NULL,
                                                FOREIGN KEY (
                                                        template_id
                                                )
                                                REFERENCES folder_template (template_id)
);

-- folder template item indexes
CREATE INDEX IF NOT EXISTS idx_template_item ON folder_template_item(template_id, sequence_no);


-- image cache
CREATE TABLE IF NOT EXISTS image_cache (
                                                image_id            INTEGER        PRIMARY KEY AUTOINCREMENT,
                                                project_id          INTEGER,
                                                file_path           VARCHAR (2000) NOT NULL,
                                                file_name           VARCHAR (500),
                                                extension           VARCHAR (20),
                                                file_size           BIGINT,
                                                width               INTEGER,
                                                height              INTEGER,
                                                capture_date        TIMESTAMP,
                                                camera_model        VARCHAR (255),
                                                thumbnail_generated BOOLEAN        DEFAULT 0,
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id)
);

-- image cache indexes
CREATE INDEX IF NOT EXISTS idx_image_project ON image_cache(project_id);
CREATE INDEX IF NOT EXISTS idx_image_path    ON image_cache(file_path);

-- payment
CREATE TABLE IF NOT EXISTS payment (
                                                payment_id     INTEGER         PRIMARY KEY AUTOINCREMENT,
                                                project_id     INTEGER         NOT NULL,
                                                payment_date   DATE            NOT NULL,
                                                amount         DECIMAL (12, 2) NOT NULL,
                                                payment_method VARCHAR (50),
                                                reference_no   VARCHAR (100),
                                                remarks        TEXT,
                                                FOREIGN KEY (
                                                        project_id
                                                )
                                                REFERENCES project (project_id)
);

-- payment indexes
CREATE INDEX IF NOT EXISTS idx_payment_project ON payment(project_id);

-- user settings
CREATE TABLE IF NOT EXISTS user_settings (
                                                setting_key   VARCHAR (100)    PRIMARY KEY,
                                                setting_value TEXT
);

-- Remembers, per project, the last folder the user was browsing (relative
-- to the project's root folder) so it can be restored on revisit.
CREATE TABLE IF NOT EXISTS project_last_folder (
                                                project_id       INTEGER        PRIMARY KEY,
                                                relative_path    VARCHAR (2000) NOT NULL,
                                                FOREIGN KEY (
                                                        project_id
                                                ) 
                                                REFERENCES project (project_id)
);


-- ═══════════════════════════════════════════════════════════════════════════
-- Seed default folder templates (skipped if already present)
-- ═══════════════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO folder_template (template_name, description) VALUES
        ('Wedding Standard',    'Standard wedding photography folder layout'),
        ('Event Standard',      'Standard event photography folder layout'),
        ('Graduation Standard', 'Standard graduation photography folder layout');


-- Wedding Standard folders
INSERT OR IGNORE INTO folder_template_item (template_id, folder_name, sequence_no)
SELECT t.template_id, f.folder_name, f.seq
FROM folder_template t, 
        (SELECT '01_RAW'       AS folder_name, 1 AS seq UNION ALL
        SELECT '02_SELECTED',                           2        UNION ALL
        SELECT '03_EDITED',                             3        UNION ALL
        SELECT '04_ALBUM',                              4        UNION ALL
        SELECT '05_DELIVERED',                          5) f
WHERE t.template_name = 'Wedding Standard';

-- Event Standard folders
INSERT OR IGNORE INTO folder_template_item (template_id, folder_name, sequence_no)
SELECT t.template_id, f.folder_name, f.seq
FROM folder_template t,
        (SELECT '01_RAW' AS folder_name, 1 AS seq UNION ALL
        SELECT '02_EDITED',   2 UNION ALL
        SELECT '03_DELIVERED',3) f
WHERE t.template_name = 'Event Standard';

-- Graduation Standard folders
INSERT OR IGNORE INTO folder_template_item (template_id, folder_name, sequence_no)
SELECT t.template_id, f.folder_name, f.seq
FROM folder_template t,
        (SELECT '01_RAW'AS folder_name, 1 AS seq UNION ALL
        SELECT '02_EDITED',   2 UNION ALL
        SELECT '03_PRINTS',   3 UNION ALL
        SELECT '04_DELIVERED',4) f
WHERE t.template_name = 'Graduation Standard';

-- Add unique constraint to prevent duplicate folders per template
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_template_folder 
        ON folder_template_item(template_id, folder_name);