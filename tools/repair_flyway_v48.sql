-- 清除 V48 失败记录（H2 需双引号表名/列名）
DELETE FROM "flyway_schema_history" WHERE "version" = '48' AND "success" = FALSE;
