-- PanacheEntity 默认使用 <table>_SEQ 序列生成 id；V14 使用 AUTO_INCREMENT 会导致找不到序列
CREATE SEQUENCE IF NOT EXISTS material_SEQ START WITH 1 INCREMENT BY 50;

