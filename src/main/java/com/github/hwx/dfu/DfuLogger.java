package com.github.hwx.dfu;

/** Stands in for {@code org.slf4j.Logger} in DFU; only the methods DFU calls. */
public interface DfuLogger {

    void info(String message);

    void info(String format, Object arg1, Object arg2);

    void warn(String format, Object arg1, Object arg2);

    void error(String message);
}
