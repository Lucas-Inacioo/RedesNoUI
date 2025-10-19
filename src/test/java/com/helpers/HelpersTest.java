package com.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the Helpers class.
 */
public class HelpersTest {

  /**
   * Tests for the isValidId method.
   * Valid IDs are non-negative integers.
   */
  @Nested
  @DisplayName("isValidId")
  @SuppressWarnings("unused")
  class IsValidIdTests {

    /**
     * Tests that isValidId returns true for zero and positive integers.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 42, Integer.MAX_VALUE})
    void returnsTrueForZeroOrPositive(int id) {
      assertTrue(Helpers.isValidId(id));
    }

    /**
     * Tests that isValidId returns false for negative integers.
     */
    @ParameterizedTest
    @ValueSource(ints = {-1, -7, -100, Integer.MIN_VALUE})
    void returnsFalseForNegative(int id) {
      assertFalse(Helpers.isValidId(id));
    }
  }

  /**
   * Tests for the isValidIP method.
   * Valid IPs are non-null strings that match the IPv4 format.
   * Alternatively, "localhost" is also considered valid.
   */
  @Nested
  @DisplayName("isValidIP")
  @SuppressWarnings("unused")
  class IsValidIpTests {

    /**
     * Tests that isValidIP returns true for valid IP addresses.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "localhost",
        "0.0.0.0",
        "127.0.0.1",
        "192.168.1.10",
        "255.255.255.255"
    })
    void acceptsValidIPs(String ip) {
      assertTrue(Helpers.isValidIP(ip));
    }

    /**
     * Tests that isValidIP returns false for invalid IP addresses.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "",                 // Empty
        "256.0.0.1",        // Octet > 255
        "1.1.1",            // Less than 4 parts
        "1.1.1.1.1",        // More than 4 parts
        "1..1.1",           // Empty part
        "a.b.c.d"           // Not numbers
    })
    void rejectsInvalidIPs(String ip) {
      assertFalse(Helpers.isValidIP(ip));
    }

    /**
     * Tests that isValidIP throws NullPointerException for null input.
     */
    @Test
    void nullThrowsNullPointerException() {
      NullPointerException ex = assertThrows(NullPointerException.class, () -> Helpers.isValidIP(null));
      assertTrue(ex != null);
    }
  }

  /**
   * Tests for the isValidPort method.
   * Valid ports are integers greater than 1024 and up to 65535.
   */
  @Nested
  @DisplayName("isValidPort")
  @SuppressWarnings("unused")
  class IsValidPortTests {

    /**
     * Tests that isValidPort returns true for ports greater than 1024 and up to 65535.
     */
    @ParameterizedTest
    @ValueSource(ints = {1025, 5000, 65535})
    void acceptsPortsGreaterThan1024(int port) {
      assertTrue(Helpers.isValidPort(port));
    }

    /**
     * Tests that isValidPort returns false for ports at or below 1024.
     */
    @ParameterizedTest
    @ValueSource(ints = {1024, 1023, 1, 0, -1})
    void rejectsPortsAtOrBelow1024(int port) {
      assertFalse(Helpers.isValidPort(port));
    }

    /**
     * Tests that isValidPort returns false for ports above 65535.
     */
    @ParameterizedTest
    @ValueSource(ints = {65536, 70000, Integer.MAX_VALUE})
    void rejectsPortsAbove65535(int port) {
      assertFalse(Helpers.isValidPort(port));
    }
  }
}
