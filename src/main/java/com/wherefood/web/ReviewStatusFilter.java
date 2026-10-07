package com.wherefood.web;

enum ReviewStatusFilter {
    ALL,
    REVIEWED,
    UNREVIEWED;

    String queryValue() {
        return this == ALL ? null : name();
    }
}
