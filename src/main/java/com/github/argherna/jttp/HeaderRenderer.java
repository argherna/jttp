package com.github.argherna.jttp;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Renders headers.
 */
class HeaderRenderer extends Renderer {

    private static final Set<String> ALWAYS_RENDERED_HEADERS = new LinkedHashSet<>();

    static {
        ALWAYS_RENDERED_HEADERS.add("Host");
        ALWAYS_RENDERED_HEADERS.add("Accept");
        ALWAYS_RENDERED_HEADERS.add("User-Agent");
    }

    private final HttpURLConnection conn;

    HeaderRenderer(HttpURLConnection conn, boolean colorOutput) {
        super(colorOutput);
        this.conn = conn;
    }

    /**
     * Prints the request line to the given PrintStream.
     * 
     * @param ps the PrintStream.
     * @throws URISyntaxException if a URISyntaxException occurs in processing.
     */
    void requestLine(PrintStream ps) throws URISyntaxException {
        var reqline = findRequestLine();

        if (isColorOutput()) {
            try (var scanner0 = new Scanner(reqline)) {
                var method = scanner0.useDelimiter(" ").next();
                var path = scanner0.next();
                var protocol = scanner0.next();
                try (var scanner1 = new Scanner(protocol)) {
                    var proto = scanner1.useDelimiter("/").next();
                    var vers = scanner1.next();
                    ps.printf("%s%s %s%s %s%s%s/%s%s%n",
                            getColorTheme().getFunctionColor().fgCode(), method,
                            getColorTheme().getKeyColor().fgCode(), path,
                            getColorTheme().getKeywordValueColor().fgCode(), proto,
                            getColorTheme().getDefaultColor().fgCode(),
                            getColorTheme().getKeywordValueColor().fgCode(), vers);
                }
            }
        } else {
            ps.println(reqline);
        }
    }

    /**
     * Prints the request headers to the given PrintStream.
     * 
     * @param ps the PrintStream.
     * @throws URISyntaxException if a URISyntaxException occurs in processing.
     */
    void requestHeaders(PrintStream ps) throws URISyntaxException {
        if (!conn.getRequestProperties().containsKey("Host")) {
            var uri = conn.getURL().toURI();
            var host = uri.getPort() == -1 ? uri.getHost()
                    : format("%s:%d", uri.getHost(), uri.getPort());
            render(ps, "Host", Arrays.asList(host));
        }

        ALWAYS_RENDERED_HEADERS.stream().filter(h -> conn.getRequestProperties().containsKey(h))
                .forEach(e -> render(ps, e, conn.getRequestProperties().get(e)));
        conn.getRequestProperties().keySet().stream()
                .filter(k -> !ALWAYS_RENDERED_HEADERS.contains(k))
                .filter(k -> nonNull(conn.getRequestProperties().get(k).get(0)))
                .forEach(k -> render(ps, k, conn.getRequestProperties().get(k)));
        ps.println();
    }

    /**
     * Prints the response line to the given PrintStream.
     * 
     * @param ps the PrintStream.
     */
    void responseStatusLine(PrintStream ps) throws IOException {
        var statusCode = conn.getResponseCode();
        var message = conn.getResponseMessage();
        if (isColorOutput()) {
            var statusColor = AnsiColor.BLUE;
            if (statusCode >= 200 && statusCode < 300) {
                statusColor = AnsiColor.GREEN;
            } else if (statusCode >= 300 && statusCode < 400) {
                statusColor = AnsiColor.YELLOW;
            } else if (statusCode >= 400 && statusCode < 500) {
                statusColor = AnsiColor.MAGENTA;
            } else if (statusCode >= 500) {
                statusColor = AnsiColor.RED;
            }
            ps.printf("%sHTTP%s/%s1.1 %s%d %s%s%n",
                    getColorTheme().getKeywordValueColor().fgCode(),
                    getColorTheme().getDefaultColor().fgCode(),
                    getColorTheme().getKeywordValueColor().fgCode(), statusColor.fgCode(),
                    statusCode, getColorTheme().getKeyColor().fgCode(),
                    isNull(message) ? "" : message);
        } else {
            ps.println(conn.getHeaderField(0));
        }
    }

    /**
     * Prints the response headers to the given PrintStream.
     * 
     * @param ps the PrintStream.
     */
    void responseHeaders(PrintStream ps) {
        if (isColorOutput()) {
            conn.getHeaderFields().entrySet().stream().filter(e -> nonNull(e.getKey()))
                    .forEach(e -> ps.printf("%s%s%s: %s%n",
                            getColorTheme().getKeyColor().fgCode(),
                            capitalizeHeaderName(e.getKey()),
                            getColorTheme().getDefaultColor().fgCode(),
                            e.getValue().stream().collect(joining(","))));
        } else {
            conn.getHeaderFields().entrySet().stream().filter(e -> nonNull(e.getKey()))
                    .forEach(e -> ps.printf("%s: %s%n", capitalizeHeaderName(e.getKey()),
                            e.getValue().stream().collect(joining(","))));
        }
    }

    /**
     * Converts header names of the form {@code header-name} to {@code Header-Name}.
     * 
     * @param headername the headername.
     * @return capitalized headername.
     */
    private String capitalizeHeaderName(String headername) {
        return Arrays.stream(headername.split("-"))
                .map(h -> h.substring(0, 1).toUpperCase() + h.substring(1))
                .collect(joining("-"));
    }

    private String findRequestLine() throws URISyntaxException {
        // → Cheat: the request header with the null value is the request line.
        var reqline = conn.getRequestProperties().entrySet().stream()
                .filter(e -> isNull(e.getValue().get(0))).map(e -> e.getKey()).findFirst()
                .orElse("");
        // → Failing getting the request line, make our own from the data provided.
        if (reqline.isEmpty()) {
            var uri = conn.getURL().toURI();
            var path = (nonNull(uri.getQuery()) && !uri.getQuery().isEmpty())
                    ? format("%s?%s", uri.getPath(), uri.getQuery())
                    : uri.getPath();
            reqline = format("%s %s HTTP/1.1", conn.getRequestMethod(), path);
        }
        return reqline;
    }

    /**
     * Convenience for printing the header to the PrintStream.
     * 
     * <p>
     * Implementation note: The value for each header is a List of Strings. This method
     * collapses the logic.
     * 
     * @param ps    the PrintStream.
     * @param name  the header name.
     * @param value the header value(s).
     */
    private void render(PrintStream ps, String name, List<String> value) {
        if (isColorOutput()) {
            value.stream()
                    .forEach(v -> ps.printf("%s%s: %s%s%n",
                            getColorTheme().getKeyColor().fgCode(), name,
                            getColorTheme().getDefaultColor().fgCode(), v));
        } else {
            value.stream().forEach(v -> ps.printf("%s: %s%n", name, v));
        }
    }
}