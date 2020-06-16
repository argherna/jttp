package com.github.argherna.jttp;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Authenticator implementation used for gathering username and password.
 */
class JttpAuthenticator extends Authenticator {

    private String username;

    private char[] password;

    /**
     * Construct a new instance of JttpAuthenticator.
     * 
     * <p>
     * When invoked with the username, password is prompted. Note that {@link System#console()
     * System.console} must not return {@code null} for this to work.
     * 
     * @param username the username.
     */
    JttpAuthenticator(String username) {
        this(username, System.console().readPassword(Jttp.RB.getString("jttp.password.prompt")));
    }

    /**
     * Construct a new instance of JttpAuthenticator with username and password pre-populated.
     * 
     * @param username the username.
     * @param password the password.
     */
    JttpAuthenticator(String username, char[] password) {
        this.username = username;
        this.password = password.clone();
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password);
    }

    @Override
    public String toString() {
        return "JttpAuthenticator [username=" + username + "]";
    }
}