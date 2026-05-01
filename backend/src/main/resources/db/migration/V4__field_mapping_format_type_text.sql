-- ENUM_JSON:{...} and other format hints can exceed VARCHAR(32); wizard-synced enum maps need room.
ALTER TABLE t_field_mapping MODIFY COLUMN format_type TEXT NULL;
