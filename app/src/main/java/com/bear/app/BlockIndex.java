package com.bear.app;

import java.util.List;

record BlockIndex(String version, List<BlockIndexEntry> blocks) {
}

record BlockIndexEntry(
    String name,
    String ir,
    String projectRoot,
    boolean enabled
) {
}
