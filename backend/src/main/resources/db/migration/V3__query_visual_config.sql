-- Visual query wizard: persisted JSON for round-trip editing; compiled sql_template remains runtime source of truth.
ALTER TABLE t_query_definition
    ADD COLUMN query_mode VARCHAR(16) NOT NULL DEFAULT 'SQL' COMMENT 'SQL=hand-written; VISUAL=wizard',
    ADD COLUMN visual_config_json TEXT NULL COMMENT 'Wizard state JSON when query_mode=VISUAL';
