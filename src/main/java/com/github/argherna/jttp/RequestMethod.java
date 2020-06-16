package com.github.argherna.jttp;

/**
 * HTTP request methods.
 */
enum RequestMethod {
    DELETE(false), GET(false), HEAD(false), OPTIONS(false), PATCH(true), POST(true), PUT(
            true), TRACE(false);

    private final boolean hasPayload;

    private RequestMethod(boolean hasPayload) {
        this.hasPayload = hasPayload;
    }

    /**
     * @return {@code true} if the method can have a payload to send to the server.
     */
    public boolean hasPayload() {
        return hasPayload;
    }
}