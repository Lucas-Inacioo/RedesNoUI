package com.unicast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.helpers.Helpers;
import com.rip.UnicastServiceUserInterface;

/**
 * Unicast Protocol (UP) implementation
 */
public class UnicastProtocol implements UnicastServiceInterface {

    /** Mapping of UCSAP IDs to their socket addresses */
    private final Map<Short, InetSocketAddress> table = new HashMap<>();

    /** The UDP socket used for communication */
    private DatagramSocket socket;

    /** The UCSAP id of this UnicastProtocol */
    private final short selfId;

    /** The upper layer service user interface */
    private final UnicastServiceUserInterface upper;

    /** Flag indicating if the protocol is running */
    private volatile boolean running = true;

    /** Thread for receiving incoming packets */
    private Thread receivingThread;

    /**
     * Constructor that loads configuration from classpath resource
     * 
     * @param configResourcePath Path to configuration file in classpath
     * @param selfId UCSAP id of this instance
     * @param upper Reference to upper layer service user interface
     * 
     * @throws RuntimeException if initialization fails
     */
    public UnicastProtocol(String configResourcePath, short selfId, UnicastServiceUserInterface upper) throws RuntimeException {
        try {
            // Save selfId and upper layer reference
            this.selfId = selfId;
            this.upper = upper;

            // Load configuration from classpath resource provided by caller
            loadConfigFromClasspath(configResourcePath);

            // Verify selfId exists in loaded config
            InetSocketAddress self = table.get(selfId);

            // Abort if selfId not found
            if (self == null) throw new IOException("Self UCSAP not found in config: " + selfId);

            // Initialize UDP socket with assigned port
            this.socket = new DatagramSocket(self.getPort());

            // Start receiver thread
            startReceiver();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize UnicastProtocol with resource: " + configResourcePath, exception);
        }
    }

    /**
     * Send data to destination UCSAP
     * 
     * @param destUCSAPId Destination UCSAP id
     * @param data Data to send
     * 
     * @throws IllegalArgumentException if destination not found
     * @throws RuntimeException if sending fails
     */
    @Override
    public void UPDataReq(short destUCSAPId, String data) throws IllegalArgumentException, RuntimeException {
        InetSocketAddress dest = table.get(destUCSAPId);
        if (dest == null) {
            throw new IllegalArgumentException("Destination address not found: " + destUCSAPId);
        }

        byte[] pdu = buildPdu(data);
        DatagramPacket packet = new DatagramPacket(pdu, pdu.length, dest.getAddress(), dest.getPort());
        try {
            this.socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send UDP packet to " + dest, e);
        }
    }

    /**
     * Close the UnicastProtocol and release resources
     */
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receivingThread != null) receivingThread.interrupt();
    }

    /**
     * Start the receiver thread for incoming UDP packets
     */
    private void startReceiver() {
        // Start a thread to receive incoming UDP packets
        receivingThread = new Thread(() -> {
        
        // Buffer for incoming packets
        byte[] buffer = new byte[1024];

        // DatagramPacket to receive data
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                // Receive incoming packet
                socket.receive(packet);

                // Parse PDU
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String payload = parsePdu(message);
                short source = resolveSource(packet.getAddress(), packet.getPort());

                // Deliver to upper layer
                if (upper != null) upper.UPDataInd(source, payload);
            } catch (IOException ignored) {
                if (!running) break;
                } catch (IllegalArgumentException bad) {
                System.err.println("[UnicastProtocol] Dropping invalid PDU: " + bad.getMessage());
            }
        }
        }, "UP-Receiver-" + selfId);

        // Set as daemon (interruptible) and start
        receivingThread.setDaemon(true);
        receivingThread.start();
    }

    /**
     * Build PDU byte array from data string
     * 
     * @param data Data string
     * 
     * @return PDU byte array
     */
    private static byte[] buildPdu(String data) {
        // Encode payload
        byte[] payload = data.getBytes(StandardCharsets.UTF_8);

        // Build header
        String header = "UPDREQPDU " + payload.length + " ";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);

        // Get total length and check if it exceeds limit
        int total = headerBytes.length + payload.length;
        if (total > 1024) {
            throw new IllegalArgumentException(
                    "Header + data exceeds 1024 bytes. "
                    + "header=" + headerBytes.length + ", payload=" + payload.length
                    + ", total=" + total
            );
        }

        // Compose PDU
        byte[] pdu = new byte[total];
        System.arraycopy(headerBytes, 0, pdu, 0, headerBytes.length);
        System.arraycopy(payload, 0, pdu, headerBytes.length, payload.length);
        return pdu;
    }

    /**
     * Parse PDU string and extract payload
     * 
     * @param pduString PDU string
     * 
     * @return Extracted payload
     * 
     * @throws IllegalArgumentException if PDU is invalid
     */
    private static String parsePdu(String pduString) throws IllegalArgumentException {
        // Check header
        if (!pduString.startsWith("UPDREQPDU ")) throw new IllegalArgumentException("Missing header");

        // Check length
        int indexOfSpace = pduString.indexOf(' ', "UPDREQPDU ".length());
        if (indexOfSpace < 0) throw new IllegalArgumentException("Missing payload size");

        // Extract length
        String lengthString = pduString.substring("UPDREQPDU ".length(), indexOfSpace);
        int lengthInteger = Integer.parseInt(lengthString);

        // Extract payload
        String payload = pduString.substring(indexOfSpace + 1);

        // Validate length
        if (payload.getBytes(StandardCharsets.UTF_8).length != lengthInteger)
            throw new IllegalArgumentException("Payload length mismatch");

        return payload;
    }

    /**
     * Resolve UCSAP id from address and port
     * 
     * @param senderAddress InetAddress of sender
     * @param port Port number of sender
     * 
     * @return Resolved UCSAP id
     */
    private short resolveSource(InetAddress senderAddress, int port) {
        // Find UCSAP id by matching address and port
        for (Map.Entry<Short, InetSocketAddress> entry : table.entrySet()) {
            InetSocketAddress currentAddress = entry.getValue();
            if (currentAddress.getPort() == port && currentAddress.getAddress().equals(senderAddress)) return entry.getKey();
        }

        throw new IllegalArgumentException("Unknown source: " + senderAddress + ":" + port);
    }

    /**
     * Load configuration from classpath resource
     * 
     * @param resourcePath Path to configuration file in classpath
     *
     * @throws IOException if loading fails
     */
    private void loadConfigFromClasspath(String resourcePath) throws IOException {
        // Load configuration from classpath resource
        try (InputStream inputStream = UnicastProtocol.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Config not found in classpath: " + resourcePath);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                int actualLine = 0;
                while ((line = br.readLine()) != null) {
                    // Process each line
                    actualLine++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse line: "<UCSAP_id> <host_name> <port_number>"
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        throw new IOException("Invalid config line (" + actualLine + "): " + line);
                    }

                    // Extract values
                    short UCSAPId;
                    String hostName;
                    int port;
                    try {
                        UCSAPId = Short.parseShort(parts[0]);
                        hostName = parts[1];
                        port = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException exception) {
                        throw new IOException("Invalid number format in config line (" + actualLine + "): " + line, exception);
                    }

                    // Parse and validate data
                    if (!Helpers.isValidId(UCSAPId)) throw new IllegalArgumentException("Invalid Node ID: " + UCSAPId);
                    if (!Helpers.isValidIP(hostName)) throw new IllegalArgumentException("Invalid host: " + hostName);
                    if (!Helpers.isValidPort(port)) throw new IllegalArgumentException("Invalid port: " + port);

                    // Store in table
                    table.put(UCSAPId, new InetSocketAddress(hostName, port));
                }
            }
        }
    }
}
