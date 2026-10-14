package com.nickwoluff.wearcalculator;

import java.math.BigInteger;

/** 比较正式发布版的数字版本号，拒绝不明确的版本格式。 */
final class VersionComparator {
    private VersionComparator() { }

    static boolean isNewer(String remote, String local) {
        String[] r = parts(remote);
        String[] l = parts(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            BigInteger rv = i < r.length ? new BigInteger(r[i]) : BigInteger.ZERO;
            BigInteger lv = i < l.length ? new BigInteger(l[i]) : BigInteger.ZERO;
            int comparison = rv.compareTo(lv);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static String[] parts(String version) {
        if (version == null || !version.matches("[vV]?[0-9]+(?:\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Invalid release version");
        }
        return version.replaceFirst("^[vV]", "").split("\\.");
    }
}
