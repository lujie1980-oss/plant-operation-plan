-- PanacheEntity 使用 production_batch_SEQ；V42 表为 BIGSERIAL 时需补序列
CREATE SEQUENCE IF NOT EXISTS production_batch_SEQ START WITH 1 INCREMENT BY 50;
