package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesSevenBase62Characters() {
        String code = generator.generate();

        assertThat(code).hasSize(CodeGenerator.LENGTH).matches("[0-9A-Za-z]{7}");
    }

    @Test
    void doesNotRepeatCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(10_000);
    }

    @Test
    void usesTheWholeAlphabet() {
        // A fixed seed makes this test repeatable
        CodeGenerator seeded = new CodeGenerator(new Random(42));
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            for (char c : seeded.generate().toCharArray()) {
                seen.add(c);
            }
        }

        assertThat(seen).hasSize(CodeGenerator.ALPHABET.length());
    }
}
