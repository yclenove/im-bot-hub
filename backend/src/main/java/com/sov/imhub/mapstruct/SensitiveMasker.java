package com.sov.imhub.mapstruct;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class SensitiveMasker {

    @Named("maskToken")
    public String maskToken(String t) {
        if (t == null || t.length() <= 8) {
            return "****";
        }
        return t.substring(0, 4) + "****" + t.substring(t.length() - 4);
    }

    /** Empty string when unset; masked when set (webhook secret optional). */
    @Named("maskOptionalToken")
    public String maskOptionalToken(String t) {
        if (t == null || t.isBlank()) {
            return "";
        }
        return maskToken(t);
    }
}
