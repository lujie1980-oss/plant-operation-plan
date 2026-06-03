-- 工艺 BOM：将空白/占位工序名补全为换型矩阵标准工序名
UPDATE product_resource
SET operation_name = CASE resource_id
    WHEN '通用裁线机' THEN '裁线'
    WHEN 'NET裁线设备' THEN '裁线'
    WHEN 'Coaxial' THEN '半成品'
    WHEN 'MATE-net' THEN '半成品'
    WHEN '总成' THEN '成品'
    WHEN '小标签设备' THEN '标签'
    WHEN '气密设备' THEN '气密'
    ELSE operation_name
END
WHERE operation_name IS NULL
   OR TRIM(operation_name) = ''
   OR operation_name LIKE '工序 %'
   OR operation_name = resource_id;

UPDATE product_resource
SET operation_name = CASE sequence_no
    WHEN 1 THEN '裁线'
    WHEN 2 THEN '半成品'
    WHEN 3 THEN '标签'
    WHEN 4 THEN '气密'
    WHEN 5 THEN '成品'
    ELSE operation_name
END
WHERE (operation_name IS NULL OR TRIM(operation_name) = '' OR operation_name LIKE '工序 %')
  AND sequence_no IS NOT NULL
  AND sequence_no BETWEEN 1 AND 5;
