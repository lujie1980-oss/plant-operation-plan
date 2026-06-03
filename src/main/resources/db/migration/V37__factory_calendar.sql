CREATE TABLE factory_calendar_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    saturday_work BOOLEAN NOT NULL DEFAULT FALSE,
    sunday_work BOOLEAN NOT NULL DEFAULT FALSE,
    shift_mode VARCHAR(16) NOT NULL DEFAULT 'TWO',
    shift1_start TIME NOT NULL DEFAULT '08:00:00',
    shift1_end TIME NOT NULL DEFAULT '20:00:00',
    shift2_start TIME NOT NULL DEFAULT '20:00:00',
    shift2_end TIME NOT NULL DEFAULT '08:00:00',
    shift3_start TIME NULL,
    shift3_end TIME NULL,
    CONSTRAINT uk_factory_calendar_policy_ws UNIQUE (workspace_id)
);

CREATE TABLE factory_calendar_day_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    calendar_date DATE NOT NULL,
    shift1_open BOOLEAN NOT NULL,
    shift2_open BOOLEAN NOT NULL,
    shift3_open BOOLEAN NULL,
    CONSTRAINT uk_factory_calendar_day_ws_date UNIQUE (workspace_id, calendar_date)
);
