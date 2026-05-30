-- 策略配置 JSON 可能较长；运行记录保存策略引用
ALTER TABLE system_parameter ALTER COLUMN param_value SET DATA TYPE CLOB;

ALTER TABLE planning_pipeline_run ADD COLUMN strategy_id VARCHAR(64);
ALTER TABLE planning_pipeline_run ADD COLUMN strategy_name VARCHAR(128);

ALTER TABLE plan_version ADD COLUMN strategy_id VARCHAR(64);
ALTER TABLE plan_version ADD COLUMN strategy_name VARCHAR(128);
