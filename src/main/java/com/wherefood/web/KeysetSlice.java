package com.wherefood.web;

import java.util.List;

record KeysetSlice<T>(List<T> content, String nextCursor) {}
