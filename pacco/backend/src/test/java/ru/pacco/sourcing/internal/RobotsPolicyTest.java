package ru.pacco.sourcing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.pacco.sourcing.ParsersTest;

class RobotsPolicyTest {

    @Test
    void starGroupLongestMatchWins() throws IOException {
        List<RobotsPolicy.Rule> rules = RobotsPolicy.parse(ParsersTest.fixture("robots.txt"));
        assertThat(RobotsPolicy.allowed(rules, "/geox-u-spherica-sneakers-basse-GE112O0H5-Q11.html")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/scarpe-uomo/?p=2")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/cart/items")).isFalse();
        assertThat(RobotsPolicy.allowed(rules, "/api/navigation")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/api/catalog")).isFalse();
        assertThat(RobotsPolicy.allowed(rules, "/scarpe/?a=1&b=2&c=3&d=4")).isFalse();
    }
}
