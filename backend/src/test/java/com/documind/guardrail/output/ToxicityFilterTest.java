package com.documind.guardrail.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToxicityFilterTest {

    private final ToxicityFilter toxicityFilter = new ToxicityFilter(java.util.Set.of());

    @Test
    void allowsOrdinaryPolicyAnswersThrough() {
        assertThat(toxicityFilter.isToxic("Vacation is 20 days per year for full-time employees.")).isFalse();
    }

    @Test
    void flagsTextContainingASlurFromTheBlocklist() {
        // Deliberately generic placeholder term, not a real slur, to keep the test source clean
        // while still exercising the blocklist-matching mechanism.
        ToxicityFilter filterWithTestBlocklist = new ToxicityFilter(java.util.Set.of("blockedtestterm"));

        assertThat(filterWithTestBlocklist.isToxic("this contains blockedtestterm in it")).isTrue();
    }

    @Test
    void matchingIsCaseInsensitive() {
        ToxicityFilter filterWithTestBlocklist = new ToxicityFilter(java.util.Set.of("blockedtestterm"));

        assertThat(filterWithTestBlocklist.isToxic("BLOCKEDTESTTERM appears here")).isTrue();
    }

    @Test
    void aSubstringMatchInsideALargerBenignWordDoesNotFalselyFlag() {
        ToxicityFilter filterWithTestBlocklist = new ToxicityFilter(java.util.Set.of("ass"));

        assertThat(filterWithTestBlocklist.isToxic("Please review the class assignment.")).isFalse();
    }
}
