package com.gtnewhorizons.dfu;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Stands in for {@code org.slf4j.LoggerFactory} in DFU. Both use {@code {}} placeholders. */
public final class DfuLoggerFactory {

    private DfuLoggerFactory() {}

    public static DfuLogger getLogger(Class<?> clazz) {
        Logger logger = LogManager.getLogger(clazz);
        return new DfuLogger() {

            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void info(String format, Object arg1, Object arg2) {
                logger.info(format, arg1, arg2);
            }

            @Override
            public void warn(String format, Object arg1, Object arg2) {
                logger.warn(format, arg1, arg2);
            }

            @Override
            public void error(String message) {
                logger.error(message);
            }
        };
    }
}
