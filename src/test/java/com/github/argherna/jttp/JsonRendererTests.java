package com.github.argherna.jttp;

import org.junit.Test;

public class JsonRendererTests {
    
    @Test
    public void testEmptyStringValue() {
        var json = "{\"data\":\"\"}".toCharArray();
        var renderer = new JsonRenderer(json, System.out, true, true);
        renderer.run();
    }
}