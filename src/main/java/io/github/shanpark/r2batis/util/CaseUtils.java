package io.github.shanpark.r2batis.util;

public class CaseUtils {
    public static String underscoreToCamalCase(String input) {
        StringBuilder builder = new StringBuilder();
        for (String word : input.split("_")) {
            if (builder.isEmpty())
                builder.append(word.toLowerCase());
            else
                builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return builder.toString();
    }
}
