package com.github.argherna.jttp;

/**
 * Built-in Mime types for request payloads.
 */
enum RequestMimeType {
    /** {@code application/x-www-form-urlencoded} */
    FORM("application/x-www-form-urlencoded"),
    /** {@code application/json} */
    JSON("application/json"),
    /** {@code multipart/form-data} */
    MULTIPART("multipart/form-data");

    private final String contentType;

    private RequestMimeType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * @return the content type string.
     */
    public String getContentType() {
        return contentType;
    }
}