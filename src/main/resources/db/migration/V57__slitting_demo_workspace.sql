-- 分切优化演示 workspace（主数据由启动时 SampleDataLoader 从 factory-slitting-demo.json 加载）
INSERT INTO workspace (workspace_id, name, description, is_default)
SELECT 'slitting-demo', '分切演示', '多场景分切优化演示：宽母卷拼排、N83 多级 BOM、锁定/重算验证', FALSE
WHERE NOT EXISTS (SELECT 1 FROM workspace WHERE workspace_id = 'slitting-demo');
