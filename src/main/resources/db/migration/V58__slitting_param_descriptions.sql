-- 分切优化参数说明（供「分切排样 · 优化参数」页展示）
UPDATE system_parameter
SET description = '整方案分切（方案级 solve）Timefold 求解最长运行秒数'
WHERE param_id = 'slitting_solver_seconds';

UPDATE system_parameter
SET description = '母卷分切工作台会话层优化（自动分切、优化未锁定）最长运行秒数'
WHERE param_id = 'slitting_session_solver_seconds';

UPDATE system_parameter
SET description = '从销售需求导入子分切订单时的默认宽度（mm）'
WHERE param_id = 'slitting_default_child_width_mm';

UPDATE system_parameter
SET description = '从销售需求导入子分切订单时的默认长度（mm）'
WHERE param_id = 'slitting_default_child_length_mm';
