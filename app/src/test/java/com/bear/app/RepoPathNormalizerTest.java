package com.bear.app;

import com.bear.kernel.target.RepoPathNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoPathNormalizerTest {
    @Test
    void normalizePathForIdentityIsDeterministic() {
        assertEquals("src/main/java/blocks/account", RepoPathNormalizer.normalizePathForIdentity("./src\\main\\java\\blocks\\account/"));
        assertEquals("C:/repo/app", RepoPathNormalizer.normalizePathForIdentity("C:\\repo\\app\\"));
    }

    @Test
    void normalizePathForPrefixUsesSingleTrailingSlash() {
        assertEquals("src/main/java/", RepoPathNormalizer.normalizePathForPrefix("src/main/java"));
        assertEquals("/", RepoPathNormalizer.normalizePathForPrefix("/"));
    }

    @Test
    void hasSegmentPrefixIsSegmentSafe() {
        assertTrue(RepoPathNormalizer.hasSegmentPrefix("src/main/java/blocks/account/impl/X.java", "src/main/java/blocks/account"));
        assertFalse(RepoPathNormalizer.hasSegmentPrefix("src/main/java/blocks/accounting/impl/X.java", "src/main/java/blocks/account"));
    }

    @Test
    void hasSegmentPrefixIsCaseSensitive() {
        assertFalse(RepoPathNormalizer.hasSegmentPrefix("C:/Repo/app/src/Main.java", "C:/repo/app"));
    }
}
