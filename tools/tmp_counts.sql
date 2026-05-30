SELECT 'materials', COUNT(*) FROM material WHERE workspace_id='te';
SELECT 'bom', COUNT(*) FROM bom_component WHERE workspace_id='te';
SELECT 'routing', COUNT(*) FROM product_resource WHERE workspace_id='te';