package com.hooklens.endpoint;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator slugGenerator = new SlugGenerator();

    @Test
    void 영숫자_10자를_생성한다() {
        assertThat(slugGenerator.generate()).matches("^[A-Za-z0-9]{10}$");
    }

    @Test
    void 만_번_생성해도_중복이_없다() {
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            slugs.add(slugGenerator.generate());
        }
        assertThat(slugs).hasSize(10_000);
    }
}
