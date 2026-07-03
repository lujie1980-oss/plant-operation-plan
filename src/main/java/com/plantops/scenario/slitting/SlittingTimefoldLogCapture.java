package com.plantops.scenario.slitting;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 将 Timefold 求解器 INFO 级关键日志写入分切运行记录（跳过高频 step 日志）。
 */
public final class SlittingTimefoldLogCapture implements AutoCloseable {

    private static final String LOGGER_NAME = "ai.timefold.solver";

    private final Logger logger;
    private final Handler handler;

    private SlittingTimefoldLogCapture(Logger logger, Handler handler) {
        this.logger = logger;
        this.handler = handler;
    }

    public static SlittingTimefoldLogCapture attach(String runId, SlittingSolverRunService runService) {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || !isLoggable(record)) {
                    return;
                }
                String message = record.getMessage();
                if (message == null || message.isBlank()) {
                    return;
                }
                if (!shouldCapture(message)) {
                    return;
                }
                String level = record.getLevel() != null ? record.getLevel().getName() : "INFO";
                runService.appendLog(runId, level, message);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
        return new SlittingTimefoldLogCapture(logger, handler);
    }

    private static boolean shouldCapture(String message) {
        return message.contains("Solving started")
                || message.contains("Solving ended")
                || message.contains("phase (")
                || message.contains("Construction Heuristic")
                || message.contains("Local Search")
                || message.contains("Problem scale")
                || message.contains("Skipped all phases");
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }
}
