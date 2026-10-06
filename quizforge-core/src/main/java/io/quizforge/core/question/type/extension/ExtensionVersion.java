package io.quizforge.core.question.type.extension;

import java.math.BigInteger;

/** Numeric release ordering followed by SemVer prerelease identifier ordering. */
public final class ExtensionVersion {
    private ExtensionVersion() { }
    public static int compare(String left, String right) {
        String[] a = left.split("-", 2), b = right.split("-", 2);
        String[] av = a[0].split("\\."), bv = b[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = new BigInteger(av[i]).compareTo(new BigInteger(bv[i]));
            if (comparison != 0) return comparison;
        }
        if (a.length != b.length) return a.length == 1 ? 1 : -1;
        if (a.length == 1) return 0;
        String[] ap = a[1].split("\\."), bp = b[1].split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            boolean an = ap[i].matches("[0-9]+"), bn = bp[i].matches("[0-9]+");
            int comparison = an && bn ? new BigInteger(ap[i]).compareTo(new BigInteger(bp[i]))
                    : an != bn ? (an ? -1 : 1) : ap[i].compareTo(bp[i]);
            if (comparison != 0) return comparison;
        }
        return Integer.compare(ap.length, bp.length);
    }
}
