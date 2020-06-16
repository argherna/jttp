package com.github.argherna.jttp;

import static java.lang.System.Logger.Level.INFO;

import java.io.File;
import java.net.HttpURLConnection;

/**
 * Allows access to certain internal Jttp objects from within pre- and post-process scripts.
 * 
 * <p>
 * There should be separate instances of JttpScriptObject associated with pre-processing and
 * post-processing. That is, each phase gets its own instance. However, the HttpURLConnection
 * will be the same object in both phases but in different states.
 * 
 * <p>
 * The pre-processing phase can modify the HttpURLConnection as normal by setting headers,
 * cookies, etc. The response file will be unavailable in pre-processing.
 * 
 * <p>
 * The post-processing phase can read the HttpURLConnection as normal such as headers and
 * cookies. The response file will be available and its data processed by the script.
 * Post-processing should not modify the contents of this file.
 */
class JttpScriptObject {

    private static System.Logger SCRIPT_LOGGER =
            System.getLogger(JttpScriptObject.class.getName());

    private final HttpURLConnection conn;

    private final File response;

    /**
     * Construct a new JttpScriptObject.
     * 
     * @param conn the HttpURLConnection associated with Jttp.
     */
    JttpScriptObject(HttpURLConnection conn) {
        this(conn, null);
    }

    /**
     * 
     * @param conn     the HttpURLConnection associated with Jttp.
     * @param response the response file (a temporary file).
     */
    public JttpScriptObject(HttpURLConnection conn, File response) {
        this.conn = conn;
        this.response = response;
    }

    /**
     * @return the HttpURLConnection associated with Jttp.
     */
    final HttpURLConnection getHttpURLConnection() {
        return conn;
    }

    /**
     * @return the response (temporary) file, possibly {@code null} if in pre-processing phase.
     */
    final File getResponseFile() {
        return response;
    }

    /**
     * Logs a message at INFO.
     * 
     * @param msg message to log.
     */
    final void log(String msg) {
        SCRIPT_LOGGER.log(INFO, msg);
    }
}