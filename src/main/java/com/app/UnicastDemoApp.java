package com.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.routingInformationProtocol.RoutingInformationProtocol;
import com.routingManagement.RoutingProtocolManagementServiceUserInterface;
import com.unicast.UnicastProtocol;

/**
 * Routing / Unicast demo application with a simple command-line interface.
 *
 * For selfId == 0 this acts as the RoutingManagementApplication (manager).
 * For selfId != 0 this acts as a plain node; it only answers requests.
 */
public class UnicastDemoApp {

    /**
     * Simple implementation of the RoutingManagementApplication that just prints
     * indications received from the RoutingInformationProtocol.
     */
    private static class RoutingManagementShell implements RoutingProtocolManagementServiceUserInterface {

        @Override
        public synchronized void linkCostIndication(short nodeA, short nodeB, int cost) {
            System.out.println();
            System.out.println("[RIP] linkCostIndication(" + nodeA + ", " + nodeB + ") = " + cost);
            System.out.print("> ");
        }

        @Override
        public synchronized void distanceTableIndication(short nodeId, int[][] distanceTable) {
            System.out.println();
            System.out.println("[RIP] distanceTableIndication from node " + nodeId + ":");

            int nodeCount = distanceTable.length;

            // header
            System.out.print("     ");
            for (int columnIndex = 0; columnIndex < nodeCount; columnIndex++) {
                System.out.printf("%4d", columnIndex);
            }
            System.out.println();

            // rows
            for (int rowIndex = 0; rowIndex < nodeCount; rowIndex++) {
                System.out.printf("%4d:", rowIndex);
                for (int columnIndex = 0; columnIndex < nodeCount; columnIndex++) {
                    System.out.printf("%4d", distanceTable[rowIndex][columnIndex]);
                }
                System.out.println();
            }

            System.out.print("> ");
        }
    }

    public UnicastDemoApp() {
        // Default constructor
    }

    /**
     * Prints the help text for available commands.
     */
    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  help                              - Show this help");
        System.out.println("  whoami                            - Show this node id");
        System.out.println("  peers                             - Show a simple peers hint");
        System.out.println("  quit / exit                       - Exit the application");
        System.out.println();
        System.out.println("RIP commands (only when self id == 0, the manager):");
        System.out.println("  getcost <node> <neighbor>         - Request link cost");
        System.out.println("  setcost <node> <neighbor> <cost>  - Set link cost");
        System.out.println("  gettable <node>                   - Request full distance table");
        System.out.println();
        System.out.println("Raw send (for debugging, any id):");
        System.out.println("  send <destId> <message>");
    }

    /**
     * Main entry point.
     *
     * Usage:
     *   java com.app.UnicastDemoApp <selfId>
     */
    public static void main(String[] args) throws Exception {
        // Self id is mandatory and is the first argument
        if (args.length < 1) {
            System.err.println("Usage: java com.app.UnicastDemoApp <selfId>");
            System.exit(2);
        }

        // Parse self id
        final short selfId;
        try {
            selfId = Short.parseShort(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid self id: " + args[0]);
            System.exit(2);
            return;
        }

        // Simple default config paths
        final String nodesConfig   = "classpath:/nodes.conf";
        final String networkConfig = "classpath:/network.conf";

        // Create RoutingInformationProtocol and UnicastProtocol, binding them together
        final RoutingInformationProtocol routingProtocol;
        final UnicastProtocol unicastProtocol;
        final RoutingManagementShell routingManagementShell; // only used when selfId == 0

        try {
            routingProtocol = new RoutingInformationProtocol(networkConfig, selfId);
            unicastProtocol = new UnicastProtocol(nodesConfig, selfId, routingProtocol);
            routingProtocol.bind(unicastProtocol);

            // If this is the manager (selfId == 0), bind a management application
            if (selfId == 0) {
                routingManagementShell = new RoutingManagementShell();
                routingProtocol.bind(routingManagementShell);
            } else {
                routingManagementShell = null;
            }
        } catch (RuntimeException error) {
            System.err.println("Failed to start: " + error.getMessage());
            System.exit(1);
            return;
        }

        // Add shutdown hook to close the protocol on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down...");
                unicastProtocol.close();
            } catch (Exception ignored) {
            }
        }));

        // Print welcome message and commands help
        System.out.println(
            "UnicastDemoApp started. selfId=" + selfId +
            ", config=\"" + nodesConfig + "\", network=\"" + networkConfig + "\""
        );
        printHelp();

        // Start command loop
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.print("> ");
        while ((line = bufferedReader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            String lower = line.toLowerCase();

            if (lower.equals("quit") || lower.equals("exit")) {
                break;

            } else if (lower.equals("help")) {
                printHelp();

            } else if (lower.equals("whoami")) {
                System.out.println("I am node " + selfId + ".");

            } else if (lower.equals("peers")) {
                System.out.println("Peers depend on your network.conf topology.");

            } else if (lower.startsWith("send ")) {
                // raw send: send <dest> <message>
                String rest = line.substring(5).trim();
                int spaceIndex = rest.indexOf(' ');
                if (spaceIndex <= 0) {
                    System.out.println("Usage: send <destId> <message>");
                } else {
                    String destinationIdString = rest.substring(0, spaceIndex).trim();
                    String message             = rest.substring(spaceIndex + 1);
                    try {
                        short destinationId = Short.parseShort(destinationIdString);
                        routingProtocol.send(destinationId, message);
                        System.out.println("Sent to " + destinationId + ": " + message);
                    } catch (NumberFormatException numberFormatException) {
                        System.out.println("Invalid destination id: " + destinationIdString);
                    } catch (RuntimeException runtimeException) {
                        System.out.println("Send failed: " + runtimeException.getMessage());
                    }
                }

            } else if (lower.startsWith("getcost ")) {
                // getcost <node> <neighbor>  (manager only)
                if (selfId != 0) {
                    System.out.println("This command is only valid in the manager (self id 0).");
                } else {
                    String rest = line.substring("getcost".length()).trim();
                    String[] parts = rest.split("\\s+");
                    if (parts.length != 2) {
                        System.out.println("Usage: getcost <node> <neighbor>");
                    } else {
                        try {
                            short node     = Short.parseShort(parts[0]);
                            short neighbor = Short.parseShort(parts[1]);
                            boolean requestAccepted = routingProtocol.getLinkCost(node, neighbor);
                            if (!requestAccepted) {
                                System.out.println("Request rejected: invalid nodes or no link.");
                            } else {
                                System.out.println("Request sent; wait for linkCostIndication.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid id(s).");
                        }
                    }
                }

            } else if (lower.startsWith("setcost ")) {
                // setcost <node> <neighbor> <cost>  (manager only)
                if (selfId != 0) {
                    System.out.println("This command is only valid in the manager (self id 0).");
                } else {
                    String rest = line.substring("setcost".length()).trim();
                    String[] parts = rest.split("\\s+");
                    if (parts.length != 3) {
                        System.out.println("Usage: setcost <node> <neighbor> <cost>");
                    } else {
                        try {
                            short node     = Short.parseShort(parts[0]);
                            short neighbor = Short.parseShort(parts[1]);
                            int cost       = Integer.parseInt(parts[2]);
                            boolean requestAccepted = routingProtocol.setLinkCost(node, neighbor, cost);
                            if (!requestAccepted) {
                                System.out.println("Request rejected: invalid nodes/link or cost.");
                            } else {
                                System.out.println("Request sent; wait for linkCostIndication.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid id(s) or cost.");
                        }
                    }
                }

            } else if (lower.startsWith("gettable ")) {
                // gettable <node>  (manager only)
                if (selfId != 0) {
                    System.out.println("This command is only valid in the manager (self id 0).");
                } else {
                    String rest = line.substring("gettable".length()).trim();
                    if (rest.isEmpty()) {
                        System.out.println("Usage: gettable <node>");
                    } else {
                        try {
                            short node = Short.parseShort(rest);
                            boolean requestAccepted = routingProtocol.getDistanceTable(node);
                            if (!requestAccepted) {
                                System.out.println("Request rejected: invalid node id.");
                            } else {
                                System.out.println("Request sent; wait for distanceTableIndication.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid node id.");
                        }
                    }
                }

            } else {
                System.out.println("Unknown command: " + line);
                System.out.println();
                printHelp();
            }

            // Prepare for next command
            System.out.print("> ");
        }

        // Close protocol and exit
        unicastProtocol.close();
        System.out.println("Goodbye.");
    }
}
