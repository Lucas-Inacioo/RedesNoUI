package com.routingInformationProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;

import com.helpers.Helpers;
import com.routingManagement.RoutingProtocolManagementServiceUserInterface;
import com.unicast.UnicastServiceInterface;
import com.unicast.UnicastServiceUserInterface;

/* Possible states of the RoutingInformationProtocol */
enum PossibleState {
    IDLE,
    LINK_COST_REQUEST,
    LINK_COST_SET_REQUEST_1,
    LINK_COST_SET_REQUEST_2,
    DISTANCE_TABLE_REQUEST,
    VECTOR_PROPAGATION,
    DISTANCE_VECTOR_UPDATE
}

/**
 * Implementation of a RoutingInformationProtocol.
 */
public class RoutingInformationProtocol implements UnicastServiceUserInterface, RoutingProtocolManagementInterface {

    /**
     * The UCSAP id of this RoutingInformationProtocol
     */
    private final short selfId;

    /**
     * The distance table of this RoutingInformationProtocol
     */
    private int[][] distanceTable;

    /**
     * Physical link costs c(x, v).
     */
    private int[][] linkCostTable;

    /**
     * The current state of the manager
     */
    private PossibleState currentState = PossibleState.IDLE;

    /**
     * The UnicastProtocol used by this RoutingInformationProtocol
     */
    private UnicastServiceInterface unicastProtocol;

    /**
     * The RoutingManagementApplication using this RoutingInformationProtocol
     */
    private RoutingProtocolManagementServiceUserInterface routingProtocolManagementServiceUserInterface;

    /**
     * Represents an infinite cost in the distance table
     */
    private static final int INF = -1;

    /**
     * Maximum number of nodes supported (0..15)
     */
    private static final int MAX_NODES = 16; // 0..15

    /**
     * Timeout (ms) for periodic distance vector propagation
     */
    private final long propagationTimeoutMillis;

    /**
     * Timer for periodic propagation
     */
    private Timer propagationTimer;

    /**
     * Constructs a RoutingInformationProtocol with default timeout (10s).
     */
    public RoutingInformationProtocol(String networkConfig, short selfId) throws IOException {
        this(networkConfig, selfId, 10_000L); // 10 seconds default
    }

    /**
     * Constructs a RoutingInformationProtocol with a specific timeout.
     *
     * @param timeoutMillis Timeout in milliseconds for periodic distance vector
     * propagation
     */
    public RoutingInformationProtocol(String networkConfig, short selfId, long timeoutMillis) throws IOException {
        this.selfId = selfId;
        this.propagationTimeoutMillis = timeoutMillis;
        initializeDistanceTable(networkConfig);
        startPropagationTimerIfNode();
    }

    /**
     * Starts the periodic propagation timer for node entities (selfId != 0)
     */
    private void startPropagationTimerIfNode() {
        if (selfId == 0) {
            return;
        }

        propagationTimer = new Timer(true);
        propagationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handlePeriodicPropagation();
            }
        }, propagationTimeoutMillis, propagationTimeoutMillis);
    }

    /**
     * Called periodically by the timer
     */
    private void handlePeriodicPropagation() {
        if (selfId == 0) {
            return;
        }
        propagateDistanceVector();
    }

    /**
     * Binds this RoutingInformationProtocol to the given UnicastProtocol.
     *
     * @param unicastProtocol The UnicastProtocol to bind to
     */
    public void bind(UnicastServiceInterface unicastProtocol) {
        this.unicastProtocol = unicastProtocol;
    }

    public void bind(RoutingProtocolManagementServiceUserInterface app) {
        this.routingProtocolManagementServiceUserInterface = app;
    }

    /**
     * Sends a message to the specified destination UCSAP id using the unicast
     * protocol.
     *
     * @param dest The destination UCSAP id
     * @param msg The message to send
     *
     * @throws IllegalStateException if the RoutingInformationProtocol is not
     * yet bound to a UnicastProtocol
     */
    public void send(short dest, String msg) throws IllegalStateException {
        if (unicastProtocol == null) {
            throw new IllegalStateException("RoutingInformationProtocol not bound to protocol yet.");
        }
        unicastProtocol.UPDataReq(dest, msg);
    }

    /**
     * Handles incoming data indications from the UnicastProtocol.
     *
     * @param originUCSAPId Origin UCSAP id
     * @param data The received data
     */
    @Override
    public void UPDataInd(short originUCSAPId, String data) {
        System.out.println("RIP received data from " + originUCSAPId + ": " + data);
        // Check if node is manager
        boolean isManager = (selfId == 0);

        // Check if sender is manager
        boolean senderIsManager = (originUCSAPId == 0);

        // Get PDU parts
        String[] pduDataParts = data.split(" ");
        if (pduDataParts.length < 1) {
            return;
        }

        // Deal with operation considering if is manager or not
        if (isManager) {
            processManagerOperation(pduDataParts);
        } else {
            processNodeOperation(pduDataParts, senderIsManager);
        }
    }

    /**
     * Asks the RoutingInformationProtocol to return its distance table.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     *
     * @return true if valid UCSAPId, false otherwise
     */
    @Override
    public boolean getDistanceTable(short UCSAPId) {
        // Check if UCSAPId is valid
        if (!isValidNode(UCSAPId)) {
            return false;
        }

        // Build PDU and send request
        String ripRqtPDU = "RIPRQT";
        unicastProtocol.UPDataReq(UCSAPId, ripRqtPDU);

        // Set current state accordingly
        currentState = PossibleState.DISTANCE_TABLE_REQUEST;

        return true;
    }

    /**
     * Asks the RoutingInformationProtocol to get the cost of the link to a
     * neighbor.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     * @param neighbor The UCSAP id of the neighbor
     *
     * @return true if the both nodes are valid and connected, false otherwise
     */
    @Override
    public boolean getLinkCost(short UCSAPId, short neighborUCSAPId) {
        // Check if UCSAPId and neighborUCSAPId are valid
        if (!isValidNode(UCSAPId) || !isValidNode(neighborUCSAPId)) {
            return false;
        }

        // Check if there is a link between UCSAPId and neighborUCSAPId
        if (!hasConnection(UCSAPId, neighborUCSAPId)) {
            return false;
        }

        // If state is not idle, ignore request
        if (currentState != PossibleState.IDLE) {
            return true; // True because both nodes are valid and connected
        }

        // Build PDU and send request
        String ripGetPDU = "RIPGET " + UCSAPId + " " + neighborUCSAPId;
        unicastProtocol.UPDataReq(UCSAPId, ripGetPDU);

        // Set current state accordingly
        currentState = PossibleState.LINK_COST_REQUEST;

        return true;
    }

    /**
     * Asks the RoutingInformationProtocol to set the cost of the link to a
     * neighbor.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     * @param neighbor The UCSAP id of the neighbor
     * @param cost The new cost of the link
     *
     * @return true if both nodes are valid and connected and cost is
     * non-negative, false otherwise
     */
    @Override
    public boolean setLinkCost(short UCSAPId, short neighborUCSAPId, int cost) {
        // Check if UCSAPId and neighborUCSAPId are valid
        if (!isValidNode(UCSAPId) || !isValidNode(neighborUCSAPId)) {
            return false;
        }

        // Check if there is a link between UCSAPId and neighborUCSAPId
        if (!hasConnection(UCSAPId, neighborUCSAPId)) {
            return false;
        }

        // If state is not idle or waiting for link cost set confirmation, ignore request
        if (currentState != PossibleState.IDLE && currentState != PossibleState.LINK_COST_SET_REQUEST_1) {
            return true; // True because both nodes are valid and connected
        }

        // Build PDU and send request
        String ripSetPDU = "RIPSET " + UCSAPId + " " + neighborUCSAPId + " " + cost;
        unicastProtocol.UPDataReq(UCSAPId, ripSetPDU);

        // Set current state accordingly
        if (currentState == PossibleState.IDLE) {
            currentState = PossibleState.LINK_COST_SET_REQUEST_1;
        } else {
            currentState = PossibleState.LINK_COST_SET_REQUEST_2;
        }

        return true;
    }

    /**
     * Initializes the distance table from a configuration file.
     *
     * @param networkConfig The path to the network configuration file
     *
     * @throws IOException if there is an error reading the configuration file
     */
    private void initializeDistanceTable(String networkConfig) throws IOException {
        // Open config input stream
        InputStream input = openConfigInputStream(networkConfig);

        // Check if found
        if (input == null) {
            throw new IOException("Config not found: " + networkConfig);
        }

        // Read and parse config lines
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        readConfigLines(bufferedReader);
    }

    /**
     * Opens an InputStream for the given configuration path.
     *
     * @param configPath The configuration path (can be classpath: or file path)
     *
     * @return An InputStream for the configuration, or null if not found
     *
     * @throws IOException if there is an error opening the stream
     */
    private InputStream openConfigInputStream(String configPath) throws IOException {
        // Check if classpath resource
        if (configPath.startsWith("classpath:")) {
            String classPath = configPath.substring("classpath:".length());
            InputStream input = RoutingInformationProtocol.class.getResourceAsStream(classPath);
            if (input == null) {
                throw new IOException("Classpath resource not found: " + classPath);
            }
            return input;
        }

        // Search locally as file path
        Path path = Paths.get(configPath);
        if (Files.exists(path) && Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }

        return null;
    }

    /**
     * Reads and parses configuration lines from the given BufferedReader.
     *
     * @param bufferedReader The BufferedReader to read from
     *
     * @throws IOException if there is an error reading or parsing the lines
     */
    private void readConfigLines(BufferedReader bufferedReader) throws IOException {
        String line;
        int actualLine = 0;
        while ((line = bufferedReader.readLine()) != null) {
            actualLine++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\s+");
            if (parts.length != 3) {
                throw new IOException("Invalid config line (" + actualLine + "): " + line);
            }

            short UCSAPId;
            short neighborUCSAPId;
            int cost;
            try {
                UCSAPId = Short.parseShort(parts[0]);
                neighborUCSAPId = Short.parseShort(parts[1]);
                cost = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid number format in config line (" + actualLine + "): " + line, e);
            }

            if (!Helpers.isValidId(UCSAPId)) {
                throw new IllegalArgumentException("Invalid Node ID: " + UCSAPId);
            }
            if (!Helpers.isValidId(neighborUCSAPId)) {
                throw new IllegalArgumentException("Invalid neighbor Node ID: " + neighborUCSAPId);
            }
            if (cost < 1 || cost > 15) {
                throw new IllegalArgumentException("Invalid cost: " + cost);
            }

            // Add to distance table
            if (linkCostTable == null) {
                linkCostTable = new int[MAX_NODES][MAX_NODES];
                distanceTable = new int[MAX_NODES][MAX_NODES];

                for (int i = 0; i < MAX_NODES; i++) {
                    for (int j = 0; j < MAX_NODES; j++) {
                        linkCostTable[i][j] = INF;
                        distanceTable[i][j] = INF;
                    }
                    // every node's cost to itself is 0
                    distanceTable[i][i] = 0;
                }
            }
            linkCostTable[UCSAPId][neighborUCSAPId] = cost;
            linkCostTable[neighborUCSAPId][UCSAPId] = cost;

            if (UCSAPId == selfId) {
                distanceTable[selfId][neighborUCSAPId] = cost;
            } else if (neighborUCSAPId == selfId) {
                distanceTable[selfId][UCSAPId] = cost;
            }
        }
    }

    /** 
     * Processes an operation received by the manager.
     */
    private void processManagerOperation(String[] pduDataParts) {
        // Get operation
        String operation = pduDataParts[0];

        switch (operation) {
            // <RIPNTF><\s><UCSAPId><\s><neighborUCSAPId><\s><cost>
            case "RIPNTF" -> {
                System.out.println("Received RIPNTF");
                // Check if we got both nodes and cost
                if (pduDataParts.length < 4) {
                    return;
                }

                // Try to parse UCSAP ids and cost
                short UCSAPId;
                short neighborUCSAPId;
                int cost;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                    neighborUCSAPId = Short.parseShort(pduDataParts[2]);
                    cost = Integer.parseInt(pduDataParts[3]);
                } catch (NumberFormatException error) {
                    return;
                }

                boolean shouldNotify = false;

                System.out.println("Current state: " + currentState);

                switch (currentState) {
                    case LINK_COST_REQUEST -> {
                        shouldNotify = true;
                        currentState = PossibleState.IDLE;
                    }
                    case LINK_COST_SET_REQUEST_1 -> {
                        // faz o segundo RIPSET invertendo os parâmetros
                        setLinkCost(neighborUCSAPId, UCSAPId, cost);
                        currentState = PossibleState.LINK_COST_SET_REQUEST_2;
                    }
                    case LINK_COST_SET_REQUEST_2 -> {
                        shouldNotify = true;
                        currentState = PossibleState.IDLE;
                    }
                    default -> {
                        return; // ignora
                    }
                }

                if (shouldNotify && routingProtocolManagementServiceUserInterface != null) {
                    routingProtocolManagementServiceUserInterface
                            .linkCostIndication(UCSAPId, neighborUCSAPId, cost);
                }
            }
            // <RIPRSP><\s><UCSAPId><\s><distanceTable entries...>
            // Entries <vetor_distancia_no_origem><\s><vetor_distancia_no1>...<vetor_distancia_non-1>
            case "RIPRSP" -> {
                // Check if we got UCSAPId
                if (pduDataParts.length < 3) { // need at least "RIPRSP <id> <vec_self>"
                    return;
                }

                // Try to parse UCSAP id
                short UCSAPId;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Initialize received table with INF and 0 on diagonal
                int[][] receivedTable = new int[MAX_NODES][MAX_NODES];
                for (int i = 0; i < MAX_NODES; i++) {
                    for (int j = 0; j < MAX_NODES; j++) {
                        receivedTable[i][j] = INF;
                    }
                    receivedTable[i][i] = 0;
                }

                int partIndex = 2;

                // 1) First vector is the origin node's DV
                int[] originVector = decodeVector(pduDataParts[partIndex++]);
                for (int dst = 0; dst < MAX_NODES; dst++) {
                    receivedTable[UCSAPId][dst] = originVector[dst];
                }

                // 2) Remaining vectors correspond to the other nodes in increasing nodeId order,
                // skipping UCSAPId. This must match the order used when building the PDU.
                int nextNodeId = 0;
                while (partIndex < pduDataParts.length && nextNodeId < MAX_NODES) {
                    if (nextNodeId == UCSAPId) {
                        nextNodeId++;
                        continue;
                    }

                    int[] vec = decodeVector(pduDataParts[partIndex++]);
                    for (int dst = 0; dst < MAX_NODES; dst++) {
                        receivedTable[nextNodeId][dst] = vec[dst];
                    }

                    nextNodeId++;
                }

                // Notify the RoutingManagementApplication
                if (routingProtocolManagementServiceUserInterface != null) {
                    routingProtocolManagementServiceUserInterface.distanceTableIndication(UCSAPId, receivedTable);
                }

                // Set state back to idle
                currentState = PossibleState.IDLE;
            }
            default -> {
                // Unknown operation
            }
        }
    }

    /**
     * Processes an operation received by a node.
     */
    private void processNodeOperation(String[] pduDataParts, boolean senderIsManager) {
        // Get operation
        String operation = pduDataParts[0];

        // Check if idle (all operations in node must start in idle state)
        if (currentState != PossibleState.IDLE) {
            return;
        }

        switch (operation) {
            // <RIPRQT>
            case "RIPRQT" -> {
                // Only process if sender is manager
                if (!senderIsManager) {
                    return;
                }

                // Build distance table response PDU:
                // RIPRSP <selfId> <DV_self> <DV_node0> <DV_node1> ... (excluding selfId)
                StringBuilder ripRspPDUBuilder = new StringBuilder("RIPRSP ").append(selfId);

                // 1) First vector: this node's own distance vector
                ripRspPDUBuilder.append(" ")
                        .append(encodeVector(distanceTable[selfId]));

                // 2) Remaining vectors: one per other node (0..15, except selfId),
                // in deterministic order so manager can rebuild the table.
                for (int nodeId = 0; nodeId < MAX_NODES; nodeId++) {
                    if (nodeId == selfId) {
                        continue;
                    }
                    ripRspPDUBuilder.append(" ")
                            .append(encodeVector(distanceTable[nodeId]));
                }

                String ripRspPDU = ripRspPDUBuilder.toString();

                // Send response to manager and keep Idle state
                unicastProtocol.UPDataReq((short) 0, ripRspPDU);
            }
            // <RIPGET><\s><UCSAPId><\s><neighborUCSAPId>
            case "RIPGET" -> {
                // Only process if sender is manager
                if (!senderIsManager) {
                    return;
                }

                // Check if we got both nodes
                if (pduDataParts.length < 3) {
                    return;
                }

                // Try to parse UCSAP ids
                short UCSAPId;
                short neighborUCSAPId;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                    neighborUCSAPId = Short.parseShort(pduDataParts[2]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Get cost from distance table
                int cost = distanceTable[UCSAPId][neighborUCSAPId];

                // Build notification PDU
                String ripNtfPDU = "RIPNTF " + UCSAPId + " " + neighborUCSAPId + " " + cost;

                // Send notification to manager
                unicastProtocol.UPDataReq((short) 0, ripNtfPDU);
            }
            // <RIPSET><\s><UCSAPId><\s><neighborUCSAPId><\s><cost>
            case "RIPSET" -> {
                // Only process if sender is manager
                if (!senderIsManager) {
                    return;
                }

                // Check if we got both nodes and cost
                if (pduDataParts.length < 4) {
                    return;
                }

                // Try to parse UCSAP ids and cost
                short UCSAPId;
                short neighborUCSAPId;
                int cost;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                    neighborUCSAPId = Short.parseShort(pduDataParts[2]);
                    cost = Integer.parseInt(pduDataParts[3]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Check cost validity
                if (cost < -1 || cost > 15 || cost == 0) {
                    return;
                }

                // Update physical link cost
                linkCostTable[UCSAPId][neighborUCSAPId] = cost;
                linkCostTable[neighborUCSAPId][UCSAPId] = cost;

                if (UCSAPId == selfId) {
                    distanceTable[selfId][neighborUCSAPId] = cost;
                } else if (neighborUCSAPId == selfId) {
                    distanceTable[selfId][UCSAPId] = cost;
                }

                // Build notification PDU
                String ripNtfPDU = "RIPNTF " + UCSAPId + " " + neighborUCSAPId + " " + cost;

                // Send notification to manager
                unicastProtocol.UPDataReq((short) 0, ripNtfPDU);

                // Update state and check if table changed
                currentState = PossibleState.DISTANCE_VECTOR_UPDATE;
                checkIfDistanceTableChanged(neighborUCSAPId, null);
            }
            // <RIPIND><\s><UCSAPId><\s><distanceTable entries...>
            // Entries <custo_no1><:><custo_no2><:><custo_no3>...<custo_noN>
            case "RIPIND" -> {
                // Check if we got UCSAPId
                if (pduDataParts.length < 3) {
                    return;
                }

                // Try to parse UCSAP id
                short UCSAPId;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Decode neighbor's distance vector (colon-separated)
                int[] entries = decodeVector(pduDataParts[2]);

                // Update state and check if table changed
                currentState = PossibleState.DISTANCE_VECTOR_UPDATE;
                checkIfDistanceTableChanged(UCSAPId, entries);
            }
            default -> {
                // Unknown operation
            }
        }
    }

    /**
     * Checks if the given UCSAP id is a valid node (0..15).
     *
     * @param UCSAPId The UCSAP id to check
     *
     * @return true if valid, false otherwise
     */
    private boolean isValidNode(short UCSAPId) {
        return UCSAPId >= 0 && UCSAPId <= 15;
    }

    /**
     * Checks if there is a connection between two nodes.
     *
     * @param UCSAPId The UCSAP id of the first node
     * @param neighborUCSAPId The UCSAP id of the second node
     *
     * @return true if there is a connection, false otherwise
     */
    private boolean hasConnection(short UCSAPId, short neighborUCSAPId) {
        int costAB = linkCostTable[UCSAPId][neighborUCSAPId];
        int costBA = linkCostTable[neighborUCSAPId][UCSAPId];

        boolean hasPath = (costAB != INF) || (costBA != INF);
        return hasPath && UCSAPId != neighborUCSAPId;
    }

    /**
     * Checks if the distance table has changed based on a neighbor's distance
     * vector update.
     *
     * @param neighborId The UCSAP id of the neighbor
     * @param neighborDistanceVector The distance vector received from the neighbor
     */
    private void checkIfDistanceTableChanged(short neighborId, int[] neighborDistanceVector) {
        boolean tableChanged = false;

        // 1) Store / refresh the neighbor's distance vector in the table
        //    (so future updates use the latest info)
        if (neighborDistanceVector != null) {
            for (int destinationNodeId = 0; destinationNodeId < neighborDistanceVector.length; destinationNodeId++) {
                distanceTable[neighborId][destinationNodeId] = neighborDistanceVector[destinationNodeId];
            }
        }

        int numberOfNodes = distanceTable[0].length; // 16 in your case

        // 2) This node's own distance vector (row = selfId)
        int[] selfDistanceVector = distanceTable[selfId];

        // Cost to self must always be 0
        if (selfDistanceVector[selfId] != 0) {
            selfDistanceVector[selfId] = 0;
            tableChanged = true;
        }

        // 3) Recalculate Dx(Y) = min_v { c(x,v) + Dv(Y) } for every destination Y
        for (int destinationNodeId = 0; destinationNodeId < numberOfNodes; destinationNodeId++) {
            if (destinationNodeId == selfId) {
                continue; // skip cost to myself
            }

            int oldCost = selfDistanceVector[destinationNodeId];
            int bestCost = INF;

            // iterate over all possible neighbors v
            for (short neighborNodeId = 1; neighborNodeId < numberOfNodes; neighborNodeId++) {
                if (neighborNodeId == selfId) {
                    continue;
                }

                int costToNeighbor = linkCostTable[selfId][neighborNodeId];      // c(x, v)
                int neighborCostToDestination = distanceTable[neighborNodeId][destinationNodeId]; // Dv(Y)

                if (costToNeighbor == INF || neighborCostToDestination == INF) {
                    continue; // this path is not usable
                }

                int candidateCost = costToNeighbor + neighborCostToDestination;
                if (bestCost == INF || candidateCost < bestCost) {
                    bestCost = candidateCost;
                }
            }

            // If nothing reachable, keep INF (-1)
            if (bestCost != oldCost) {
                selfDistanceVector[destinationNodeId] = bestCost;
                tableChanged = true;
            }
        }

        // 4) If there was any change, propagate Dx to neighbors
        if (tableChanged) {
            propagateDistanceVector();
        }

        // 5) Back to IDLE
        currentState = PossibleState.IDLE;
    }

    /**
     * Encodes a distance vector as a colon-separated string.
     *
     * @param vec The distance vector to encode
     *
     * @return The encoded string
     */
    private String encodeVector(int[] vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(vec[i]);
        }
        return sb.toString();
    }

    /**
     * Decodes a colon-separated string into a distance vector.
     *
     * @param encoded The encoded string
     *
     * @return The decoded distance vector
     */
    private int[] decodeVector(String encoded) {
        int[] vec = new int[MAX_NODES];
        String[] parts = encoded.split(":");
        for (int i = 0; i < MAX_NODES; i++) {
            if (i < parts.length) {
                try {
                    vec[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    vec[i] = INF;
                }
            } else {
                vec[i] = INF;
            }
        }
        return vec;
    }

    /**
     * Propagates this node's distance vector to all connected neighbors.
     */
    private void propagateDistanceVector() {
        String pdu = "RIPIND " + selfId + " " + encodeVector(distanceTable[selfId]);

        for (short neighborNodeId = 1; neighborNodeId < MAX_NODES; neighborNodeId++) {
            if (neighborNodeId == selfId) {
                continue;
            }
            if (!hasConnection(selfId, neighborNodeId)) {
                continue;
            }
            if (linkCostTable[selfId][neighborNodeId] == INF) {
                continue;
            }

            unicastProtocol.UPDataReq(neighborNodeId, pdu);
        }
    }
}
