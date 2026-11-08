package com.snovasys.ledgerline.web;

/** Keeps page numbers and page sizes inside safe limits so nobody can request a million rows at once. */
final class Paging {

    static final int MAX_SIZE = 100;

    private Paging() {
    }

    static int page(int page) {
        return Math.max(page, 0);
    }

    static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
