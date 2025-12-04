package com.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.app.i18n.I18n;
import com.routingInformationProtocol.RoutingInformationProtocol;
import com.routingManagement.RoutingProtocolManagementServiceUserInterface;
import com.unicast.UnicastProtocol;

/**
 * Routing / Unicast demo application with simple command-line interface.
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

        private final I18n i18n;

        RoutingManagementShell(I18n i18n) {
            this.i18n = i18n;
        }

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

            int n = distanceTable.length;

            // header
            System.out.print("     ");
            for (int j = 0; j < n; j++) {
                System.out.printf("%4d", j);
            }
            System.out.println();

            // rows
            for (int i = 0; i < n; i++) {
                System.out.printf("%4d:", i);
                for (int j = 0; j < n; j++) {
                    System.out.printf("%4d", distanceTable[i][j]);
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
    private static void printHelp(I18n i18n) {
        System.out.println(i18n.get("helpText"));
        System.out.println();
        System.out.println("RIP commands (only when self id == 0, the manager):");
        System.out.println("  getcost <node> <neighbor>          - Request link cost");
        System.out.println("  setcost <node> <neighbor> <cost>   - Set link cost");
        System.out.println("  gettable <node>                    - Request full distance table");
        System.out.println("Raw send (for debugging, any id):");
        System.out.println("  send <destId> <message>");
    }

    /**
     * Main entry point.
     *
     * --self <id>       : Self UCSAP id (mandatory)
     * --config <path>   : Path to the unicast protocol config file (default: /nodes.conf)
     * --network <path>  : Path to the network config file (default: /network.conf)
     * --lang <code>     : Language code, "en" or "pt" (default: "en")
     */
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        Map<String, String> argumentsMap = parseCommandLineArguments(args);

        // Get values or defaults
        String selfIdString  = argumentsMap.getOrDefault("--self", null);
        String nodesConfig   = argumentsMap.getOrDefault("--config", "classpath:/nodes.conf");
        String networkConfig = argumentsMap.getOrDefault("--network", "classpath:/network.conf");
        String languageCode  = argumentsMap.getOrDefault("--lang", "en");

        // Language code must be "en" or "pt"
        final I18n i18n = I18n.forLanguageCode(languageCode);

        // Self id is mandatory
        if (selfIdString == null) {
            System.err.println(i18n.get("usage"));
            System.err.println(i18n.get("usageExample"));
            System.exit(2);
        }

        // Parse self id
        final short selfId;
        try {
            selfId = Short.parseShort(selfIdString);
        } catch (NumberFormatException e) {
            System.err.println(i18n.get("selfIdInvalid"));
            System.exit(2);
            return;
        }

        // Create RoutingInformationProtocol and UnicastProtocol, binding them together
        final RoutingInformationProtocol rip;
        final UnicastProtocol unicastProtocol;
        final RoutingManagementShell routingManagementShell; // only used when selfId == 0

        try {
            rip = new RoutingInformationProtocol(selfId);
            unicastProtocol = new UnicastProtocol(nodesConfig, selfId, rip);
            rip.bind(unicastProtocol);

            // If this is the manager (selfId == 0), bind a management application
            if (selfId == 0) {
                routingManagementShell = new RoutingManagementShell(i18n);
                // precisa existir em RoutingInformationProtocol:
                // public void bind(RoutingProtocolManagementServiceUserInterface app) { ... }
                rip.bind(routingManagementShell);
            } else {
                routingManagementShell = null;
            }
        } catch (RuntimeException error) {
            System.err.println(i18n.get("failedToStartPrefix") + error.getMessage());
            System.exit(1);
            return;
        }

        // Add shutdown hook to close the protocol on exit
        final I18n finalI18n = i18n;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println(finalI18n.get("shuttingDown"));
                unicastProtocol.close();
            } catch (Exception ignored) {}
        }));

        // Print welcome message and commands help
        System.out.println(
            i18n.get("startedPrefix") + selfId +
            ", config=\"" + nodesConfig + "\", network=\"" + networkConfig + "\""
        );
        printHelp(i18n);

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
                printHelp(i18n);

            } else if (lower.equals("whoami")) {
                System.out.println(i18n.get("whoAmIPrefix") + selfId);

            } else if (lower.equals("peers")) {
                System.out.println(i18n.get("peersHint"));

            } else if (lower.startsWith("send ")) {
                // raw send: send <dest> <message>
                String rest = line.substring(5).trim();
                int sp = rest.indexOf(' ');
                if (sp <= 0) {
                    System.out.println(i18n.get("sendUsage"));
                } else {
                    String idStr = rest.substring(0, sp).trim();
                    String msg   = rest.substring(sp + 1);
                    try {
                        short dest = Short.parseShort(idStr);
                        rip.send(dest, msg);
                        System.out.println(i18n.get("sendConfirmPrefix") + dest + ": " + msg);
                    } catch (NumberFormatException nfe) {
                        System.out.println(i18n.get("destInvalidPrefix") + idStr);
                    } catch (RuntimeException rte) {
                        System.out.println(i18n.get("sendFailedPrefix") + rte.getMessage());
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
                            boolean ok = rip.getLinkCost(node, neighbor);
                            if (!ok) {
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
                            boolean ok = rip.setLinkCost(node, neighbor, cost);
                            if (!ok) {
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
                            boolean ok = rip.getDistanceTable(node);
                            if (!ok) {
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
                System.out.println(i18n.get("unknownCommand"));
                System.out.println();
                printHelp(i18n);
            }

            // Prepare for next command
            System.out.print("> ");
        }

        // Close protocol and exit
        unicastProtocol.close();
        System.out.println(i18n.get("goodbye"));
    }

    /**
     * Parses command-line arguments into a Map.
     *
     * @param arguments Command-line arguments
     *
     * @return Map of argument keys to values
     */
    private static Map<String, String> parseCommandLineArguments(String[] arguments) {
        // Simple parser for arguments in the form --key value or --flag
        Map<String, String> parsedArguments = Arrays.stream(arguments)
            .collect(Collectors.toMap(
                argument -> argument,
                argument -> "",
                (existingValue, newValue) -> existingValue
            ));

        // Iterate through arguments to fill the map correctly
        for (int index = 0; index < arguments.length; index++) {
            String currentArgument = arguments[index];
            if (currentArgument.startsWith("--")) {
                if (index + 1 < arguments.length && !arguments[index + 1].startsWith("--")) {
                    // it's a key-value pair
                    parsedArguments.put(currentArgument, arguments[index + 1]);
                    index++;
                } else {
                    // it's a boolean flag
                    parsedArguments.put(currentArgument, "true");
                }
            }
        }
        return parsedArguments;
    }
}
