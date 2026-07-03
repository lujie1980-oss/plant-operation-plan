ALTER TABLE plan_version ADD COLUMN IF NOT EXISTS total_kpi INTEGER;
ALTER TABLE plan_version ADD COLUMN IF NOT EXISTS kpi_breakdown_json TEXT;
