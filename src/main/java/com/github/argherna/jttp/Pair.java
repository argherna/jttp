package com.github.argherna.jttp;

/**
 * Holder type used for handling request items specified on the command line.
 */
class Pair<N, V> {
    private final N n;
    private final V v;

    Pair(N n, V v) {
        this.n = n;
        this.v = v;
    }

    N getN() {
        return n;
    }

    V getV() {
        return v;
    }
}