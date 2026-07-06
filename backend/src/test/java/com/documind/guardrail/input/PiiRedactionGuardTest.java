package com.documind.guardrail.input;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactionGuardTest {

    private final PiiRedactionGuard piiRedactionGuard = new PiiRedactionGuard();

    @Test
    void redactsAnEmailAddress() {
        String redacted = piiRedactionGuard.redact("Contact me at jane.doe@example.com for details.");

        assertThat(redacted).doesNotContain("jane.doe@example.com");
        assertThat(redacted).contains("[REDACTED_EMAIL]");
    }

    @Test
    void redactsASocialSecurityNumber() {
        String redacted = piiRedactionGuard.redact("My SSN is 123-45-6789.");

        assertThat(redacted).doesNotContain("123-45-6789");
        assertThat(redacted).contains("[REDACTED_SSN]");
    }

    @Test
    void redactsACreditCardNumber() {
        String redacted = piiRedactionGuard.redact("Card number: 4111 1111 1111 1111");

        assertThat(redacted).doesNotContain("4111 1111 1111 1111");
        assertThat(redacted).contains("[REDACTED_CREDIT_CARD]");
    }

    @Test
    void redactsAPhoneNumber() {
        String redacted = piiRedactionGuard.redact("Call me at (555) 123-4567 tomorrow.");

        assertThat(redacted).doesNotContain("(555) 123-4567");
        assertThat(redacted).contains("[REDACTED_PHONE]");
    }

    @Test
    void leavesTextWithNoPiiUnchanged() {
        String original = "What is the vacation policy for full-time employees?";

        assertThat(piiRedactionGuard.redact(original)).isEqualTo(original);
    }

    @Test
    void detectsWhetherTextContainsPiiWithoutRedacting() {
        assertThat(piiRedactionGuard.containsPii("email me at test@example.com")).isTrue();
        assertThat(piiRedactionGuard.containsPii("what is the vacation policy?")).isFalse();
    }
}
