// src/main/java/com/osman/traviaskbot/util/AddressValidator.java
package com.osman.traviaskbot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressValidator {

    // Bu ifade şehir ve ülke içeren adresleri doğrulamak için kullanılacak
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(".*[a-zA-Z]+.*,.*[a-zA-Z]+.*");

    /**
     * Adresin geçerli olup olmadığını kontrol eder.
     * Adresin şehir ve ülke içermesi beklenir.
     */
    public static boolean isValid(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        // Adresin şehir ve ülke içerip içermediğini kontrol et
        Matcher matcher = ADDRESS_PATTERN.matcher(address);
        return matcher.matches();
    }
}
