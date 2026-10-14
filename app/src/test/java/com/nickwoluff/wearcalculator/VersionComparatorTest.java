package com.nickwoluff.wearcalculator;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionComparatorTest {
    @Test public void comparesNumericComponentsAndOptionalPrefix() {
        assertTrue(VersionComparator.isNewer("v1.10.0", "1.9.9"));
        assertFalse(VersionComparator.isNewer("v1.1.1", "1.1.1"));
        assertFalse(VersionComparator.isNewer("1.1", "1.1.0"));
        assertFalse(VersionComparator.isNewer("1.0.9", "1.1"));
        assertTrue(VersionComparator.isNewer("2", "1.99.99"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnrecognizedReleaseInsteadOfReportingFalseUpdate() {
        VersionComparator.isNewer("nightly", "1.1.1");
    }
}
