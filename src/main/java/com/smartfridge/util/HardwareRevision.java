package com.smartfridge.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Natural-order comparison of hardware revision strings such as "A1", "A10", "2.1". */
public final class HardwareRevision {

    private static final Pattern TOKEN = Pattern.compile("\\d+|\\D+");

    private HardwareRevision() {
    }

    public static int compare(String a, String b) {
        List<String> left = tokens(a);
        List<String> right = tokens(b);
        int common = Math.min(left.size(), right.size());
        for (int i = 0; i < common; i++) {
            String x = left.get(i);
            String y = right.get(i);
            boolean xDigits = Character.isDigit(x.charAt(0));
            boolean yDigits = Character.isDigit(y.charAt(0));
            int result;
            if (xDigits && yDigits) {
                result = new BigInteger(x).compareTo(new BigInteger(y));
            } else {
                result = x.compareToIgnoreCase(y);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static List<String> tokens(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value.trim());
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
