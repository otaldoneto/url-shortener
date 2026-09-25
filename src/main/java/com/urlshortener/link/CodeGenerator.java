package com.urlshortener.link;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

// Random Base62 codes (0-9, A-Z, a-z). Random instead of sequential, so codes cannot be guessed or enumerated
// and do not reveal how many links exist. 7 characters give 62^7, about 3.5 trillion possible codes.
@Component
public class CodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int LENGTH = 7;

    private final RandomGenerator random;

    public CodeGenerator() {
        this(new SecureRandom());
    }

    // Lets tests pass a predictable generator
    CodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
