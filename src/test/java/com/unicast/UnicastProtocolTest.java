package com.unicast;

import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.rip.UnicastServiceUserInterface;

public class UnicastProtocolTest {

  private DatagramSocket receiver; // Closed in @AfterEach
  private UnicastProtocol unicastProtocol; // Closed in @AfterEach
  @SuppressWarnings("Convert2Lambda")
  private final UnicastServiceUserInterface upperLayerMock = new UnicastServiceUserInterface() {
    @Override
    public void UPDataInd(short originUCSAPId, String data) {
      // No-op for testing
    }
  };

  /**
   * Cleanup after each test
   */
  @AfterEach
  @SuppressWarnings("unused")
  void tearDown() {
    if (receiver != null && !receiver.isClosed()) receiver.close();
    if (unicastProtocol != null) unicastProtocol.close();
  }

  /**
   * Helper to read private field via reflection
   */
  @SuppressWarnings("unchecked")
  private static Map<Short, InetSocketAddress> readTable(UnicastProtocol unicastProtocolInstance) throws Exception {
    Field field = UnicastProtocol.class.getDeclaredField("table");
    field.setAccessible(true);
    return (Map<Short, InetSocketAddress>) field.get(unicastProtocolInstance);
  }

  /**
   * Test if constructor loads config file and fills table
   */
  @Test
  @DisplayName("Constructor loads config and fills table")
  void constructorLoadsConfig() throws Exception {
    // Create UnicastProtocol with test config
    unicastProtocol = new UnicastProtocol("/com/unicast/testConfig.txt", (short) 0, upperLayerMock);

    Map<Short, InetSocketAddress> table = readTable(unicastProtocol);
    assertTrue(table.containsKey((short) 1), "config should load UCSAP 1");

    InetSocketAddress addr = table.get((short) 1);
    assertEquals("localhost", addr.getHostString());
    assertEquals(1150, addr.getPort());
  }

  /**
   * Test if UPDataReq sends UDP packet to correct destination with correct format
   */
  @Test
  @DisplayName("UPDataReq sends UDP packet to configured destination and format is correct")
  void upDataReqSendsPacket() throws Exception {
    // Prepare a UDP receiver bound to 1150 (as per test config)
    receiver = new DatagramSocket(1150);
    receiver.setSoTimeout(1500);

    unicastProtocol = new UnicastProtocol("/com/unicast/testConfig.txt", (short) 0, upperLayerMock);

    // Send
    String payload = "hello";
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      unicastProtocol.UPDataReq((short) 1, payload);
    });

    // Receive
    byte[] buffer = new byte[1024];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    receiver.receive(packet);

    String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);

    // Should be "UPDREQPDU <len> <payload>"
    assertTrue(message.startsWith("UPDREQPDU "), "PDU must start with header");
    assertTrue(message.endsWith(payload), "PDU must end with the payload");
    assertEquals("UPDREQPDU " + payload.getBytes(StandardCharsets.UTF_8).length + " " + payload, message, "Full PDU must match 'UPDREQPDU <len> <payload>'");
  }

  /**
   * Test if UPDataReq throws when destination UCSAP id is missing
   */
  @Test
  @DisplayName("UPDataReq throws when destination UCSAP id is missing")
  void upDataReqThrowsForUnknownDest() {
    unicastProtocol = new UnicastProtocol("/com/unicast/testConfig.txt", (short) 0, upperLayerMock);
    Exception exception = assertThrows(IllegalArgumentException.class, () -> unicastProtocol.UPDataReq((short) 99, "test"));
    assertNotNull(exception);
  }

  /**
   * Test if UPDataReq throws when PDU exceeds 1024 bytes
   */
  @Test
  @DisplayName("UPDataReq rejects PDU larger than 1024 bytes (buildPdu limit)")
  void upDataReqRejectsOversizedPdu() {
    unicastProtocol = new UnicastProtocol("/com/unicast/testConfig.txt", (short) 0, upperLayerMock);

    // Make a payload big enough that "UPDREQPDU <len> " + payload exceeds 1024
    // Header length is roughly 10 + digits(len) + 1, 1015 'a's is safely over.
    String big = "a".repeat(1015);
    Exception exception = assertThrows(IllegalArgumentException.class, () -> unicastProtocol.UPDataReq((short) 1, big));
    assertNotNull(exception);
  }

  /**
   * Test if UPDataReq throws when payload is null
   */
  @Test
  @DisplayName("UPDataReq throws when payload is null")
  void upDataReqThrowsForNullPayload() {
    unicastProtocol = new UnicastProtocol("/com/unicast/testConfig.txt", (short) 0, upperLayerMock);
    Exception exception = assertThrows(NullPointerException.class, () -> unicastProtocol.UPDataReq((short) 1, null));
    assertNotNull(exception);
  }

  /**
   * Test if UPDataReq throws when no config file is found
   */
  @Test
  @DisplayName("Constructor throws when config file is missing")
  void constructorThrowsForMissingConfig() {
    Exception exception = assertThrows(RuntimeException.class, () -> new UnicastProtocol("/com/unicast/missingConfig.txt", (short) 0, upperLayerMock));
    assertNotNull(exception);
  }

  /**
   * Test if UPDataReq throws when config file has invalid lines
   */
  @Test
  @DisplayName("Constructor throws when config file has invalid lines")
  void constructorThrowsForInvalidConfig() {
    Exception exception;
      exception = assertThrows(RuntimeException.class, () -> new UnicastProtocol("/com/unicast/invalidConfig.txt", (short) 0, upperLayerMock));
    assertNotNull(exception);
  }
}