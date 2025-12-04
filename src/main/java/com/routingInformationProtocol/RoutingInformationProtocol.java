package com.routingInformationProtocol;


import com.routingManagement.RoutingProtocolManagementServiceUserInterface;
import com.unicast.UnicastServiceInterface;
import com.unicast.UnicastServiceUserInterface;

enum PossibleState {
    IDLE,
    LINK_COST_REQUEST,
    LINK_COST_SET_REQUEST_1,
    LINK_COST_SET_REQUEST_2,
    DISTANCE_TABLE_REQUEST,
    VECTOR_PROPAGATION,
    DISTANCE_VECTOR_UPDATE
}

/** Implementation of a RoutingInformationProtocol. */
public class RoutingInformationProtocol implements UnicastServiceUserInterface, RoutingProtocolManagementInterface {
    /** The UCSAP id of this RoutingInformationProtocol */
    private final short selfId;

    /** The distance table of this RoutingInformationProtocol */
    private int[][] distanceTable;

    /** The current state of the manager */
    private PossibleState currentState = PossibleState.IDLE;

    /** The UnicastProtocol used by this RoutingInformationProtocol */
    private UnicastServiceInterface unicastProtocol;

    /** The RoutingManagementApplication using this RoutingInformationProtocol */
    private RoutingProtocolManagementServiceUserInterface routingProtocolManagementServiceUserInterface;

    /** Represents an infinite cost in the distance table */
    private static final int INF = -1;

    private static final int MAX_NODES = 16; // 0..15

    /**
     * Constructs a RoutingInformationProtocol with the given selfId.
     *
     * @param selfId The UCSAP id of this RoutingInformationProtocol
     */
    public RoutingInformationProtocol(short selfId) {
        this.selfId = selfId;
        initializeDistanceTable();
    }

    /**
     * Constructs a RoutingInformationProtocol with the given selfId and binds it to the specified UnicastProtocol.
     * 
     * @param selfId The UCSAP id of this RoutingInformationProtocol
     * @param unicastProtocol The UnicastProtocol to bind to 
     */
    public RoutingInformationProtocol(short selfId, UnicastServiceInterface unicastProtocol) {
        this.selfId = selfId;
        this.unicastProtocol = unicastProtocol;
        initializeDistanceTable();
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

    private void initializeDistanceTable() {
        distanceTable = new int[MAX_NODES][MAX_NODES];

        // start everything as INF
        for (int i = 0; i < MAX_NODES; i++) {
            for (int j = 0; j < MAX_NODES; j++) {
                distanceTable[i][j] = INF;
            }
        }

        // cost to self is always 0
        for (int i = 0; i < MAX_NODES; i++) {
            distanceTable[i][i] = 0;
        }
    }

    /**
     * Sends a message to the specified destination UCSAP id using the unicast protocol.
     *
     * @param dest The destination UCSAP id
     * @param msg  The message to send
     *
     * @throws IllegalStateException if the RoutingInformationProtocol is not yet bound to a UnicastProtocol
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
     * @param data          The received data
     */
    @Override
    public void UPDataInd(short originUCSAPId, String data) {
        // Check if node is manager
        boolean isManager = (selfId == 0);
    
        // Check if sender is manager
        boolean senderIsManager = (originUCSAPId == 0);

        // Process data request PDU <UPDREQPDU><\s><data.length><\s><operation><\s><parameters split by \s>
        String pduData = processDataRequestPDU(data);

        if (pduData == null) {
            return;
        }

        // Get PDU parts
        String[] pduDataParts = pduData.split(" ");
        if (pduDataParts.length < 1) {
            return;
        }
        
        // Deal with operation considering if is manager or not
        if (isManager) {
            processManagerOperation(pduDataParts);
        }
        else {
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
     * Asks the RoutingInformationProtocol to get the cost of the link to a neighbor.
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
     * Asks the RoutingInformationProtocol to set the cost of the link to a neighbor.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     * @param neighbor The UCSAP id of the neighbor
     * @param cost The new cost of the link
     * 
     * @return true if both nodes are valid and connected and cost is non-negative, false otherwise 
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

    private String processDataRequestPDU(String data) {
        // Process the received data
        String[] pduParts = data.split(" ");
        
        // Check if it is a UPDREQPDU
        if (pduParts.length < 2 || !pduParts[0].equals("UPDREQPDU")) {
            return null;
        }

        // Verify if PDU length is less than the maximum length (don't trust the sender)
        if (data.length() > 1024) {
            return null;
        }

        // Get data length
        int dataLength;
        try {
            dataLength = Integer.parseInt(pduParts[1]);
        } catch (NumberFormatException error) {
            return null;
        }

        // Guarantee it is non-negative
        if (dataLength < 0) {
            return null;
        }

        // Starting from the first character after the second space, get dataLength characters
        int firstDataCharIndex = pduParts[0].length() + 1 + pduParts[1].length() + 1;
        if (firstDataCharIndex + dataLength > data.length()) {
            return null;
        }

        // Check if doesn't exceed data length
        try {
            if (firstDataCharIndex + dataLength > data.length()) {
                return null;
            }
        } catch (IndexOutOfBoundsException error) {
            return null;
        }

        // Get PDU data
        String pduData = data.substring(firstDataCharIndex, firstDataCharIndex + dataLength);

        return pduData;
    }

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
                if (pduDataParts.length < 2) {
                    return;
                }

                // Try to parse UCSAP id
                short UCSAPId;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Build distance table from remaining entries
                int[][] receivedTable = new int[16][16];
                int index = 2;
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        if (index >= pduDataParts.length) {
                            return;
                        }
                        try {
                            receivedTable[i][j] = Integer.parseInt(pduDataParts[index]);
                        } catch (NumberFormatException error) {
                            return;
                        }
                        index++;
                    }
                }

                // Notify the RoutingManagementApplication
                routingProtocolManagementServiceUserInterface.distanceTableIndication(UCSAPId, receivedTable);

                // Set state back to idle
                currentState = PossibleState.IDLE;
            }
            default -> {
                // Unknown operation
            }
        }
    }

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

                // Build distance table response PDU
                StringBuilder ripRspPDUBuilder = new StringBuilder("RIPRSP " + selfId);
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        ripRspPDUBuilder.append(" ").append(distanceTable[i][j]);
                    }
                }
                String ripRspPDU = ripRspPDUBuilder.toString();

                // Send response to manager and keep Idle state
                unicastProtocol.UPDataReq((short)0, ripRspPDU);
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
                unicastProtocol.UPDataReq((short)0, ripNtfPDU);
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

                // Update distance table
                distanceTable[UCSAPId][neighborUCSAPId] = cost;

                // Build notification PDU
                String ripNtfPDU = "RIPNTF " + UCSAPId + " " + neighborUCSAPId + " " + cost;

                // Send notification to manager
                unicastProtocol.UPDataReq((short)0, ripNtfPDU);

                // Update state and check if table changed
                distanceTable[UCSAPId][neighborUCSAPId] = cost;
                currentState = PossibleState.DISTANCE_VECTOR_UPDATE;
                checkIfDistanceTableChanged(neighborUCSAPId, distanceTable[neighborUCSAPId]);
            }
            // <RIPIND><\s><UCSAPId><\s><distanceTable entries...>
            // Entries <custo_no1><:><custo_no2><:><custo_no3>...<custo_noN>
            case "RIPIND" -> {
                // Check if we got UCSAPId
                if (pduDataParts.length < 2) {
                    return;
                }

                // Try to parse UCSAP id
                short UCSAPId;
                try {
                    UCSAPId = Short.parseShort(pduDataParts[1]);
                } catch (NumberFormatException error) {
                    return;
                }

                // Get all values from entries as an array
                int[] entries = new int[16];
                for (int i = 0; i < 16; i++) {
                    if (2 + i >= pduDataParts.length) {
                        return;
                    }
                    try {
                        entries[i] = Integer.parseInt(pduDataParts[2 + i]);
                    } catch (NumberFormatException error) {
                        return;
                    }
                }

                // Update state and check if table changed
                currentState = PossibleState.DISTANCE_VECTOR_UPDATE;
                checkIfDistanceTableChanged(UCSAPId, entries);
            }
            default -> {
                // Unknown operation
            }
        }
    }

    private boolean isValidNode(short UCSAPId) {
        return UCSAPId >= 0 && UCSAPId <= 15;
    }

    private boolean hasConnection(short UCSAPId, short neighborUCSAPId) {
        int cost = distanceTable[UCSAPId][neighborUCSAPId];
        return cost != INF && UCSAPId != neighborUCSAPId;
    }

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

                int costToNeighbor = distanceTable[selfId][neighborNodeId];      // c(x, v)
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

    private void propagateDistanceVector() {
        // RIPIND <selfId> <d0> <d1> ... <d15>
        StringBuilder ripIndPDUBuilder = new StringBuilder("RIPIND ").append(selfId);

        int[] selfDistanceVector = distanceTable[selfId];
        for (int destinationNodeId = 0; destinationNodeId < selfDistanceVector.length; destinationNodeId++) {
            ripIndPDUBuilder.append(" ").append(selfDistanceVector[destinationNodeId]);
        }

        String ripIndPDU = ripIndPDUBuilder.toString();

        // Send only to neighbors with finite cost, as the spec says not to notify
        // neighbors whose link has been set to infinity (-1). :contentReference[oaicite:3]{index=3}
        for (short neighborNodeId = 1; neighborNodeId < 16; neighborNodeId++) {
            if (neighborNodeId == selfId) {
                continue;
            }
            if (!hasConnection(selfId, neighborNodeId)) {
                continue;
            }
            if (distanceTable[selfId][neighborNodeId] == INF) {
                continue;
            }

            unicastProtocol.UPDataReq(neighborNodeId, ripIndPDU);
        }
    }
}