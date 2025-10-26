package com.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.RoutingInformationProtocol.RoutingInformationProtocol;
import com.app.i18n.I18n;
import com.unicast.UnicastProtocol;

/**
 * Unicast demo application with simple command-line interface.
 */
public class UnicastDemoApp {

    /**
     * Default constructor for UnicastDemoApp.
     * Initializes the application without any specific setup.
     */
    public UnicastDemoApp() {
        // Default constructor
    }


    /**
     * Prints the help text for available commands.
     */
    private static void printHelp(I18n i18n) {
        System.out.println(i18n.get("helpText"));
    }

    /**
     * Main entry point.
     * 
     * @param args Command-line arguments
     * 
     * --self &lt;id&gt;       : Self UCSAP id (mandatory)
     * --config &lt;path&gt;   : Path to the unicast protocol config file (default: /up.conf)
     * --lang &lt;code&gt;     : Language code, "en" or "pt" (default: "en")
     *
     * @throws Exception on fatal errors
     */
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        Map<String, String> argumentsMap = parseCommandLineArguments(args);

        // Get values or defaults
        String selfIdString = argumentsMap.getOrDefault("--self", null);
        String configPath = argumentsMap.getOrDefault("--config", "/up.conf");
        String languageCode = argumentsMap.getOrDefault("--lang", "en");

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
            System.exit(2); return;
        }

        // Create RoutingInformationProtocol and UnicastProtocol, binding them together
        final RoutingInformationProtocol rip;
        final UnicastProtocol unicastProtocol;
        try {
            rip = new RoutingInformationProtocol(selfId);
            unicastProtocol  = new UnicastProtocol(configPath, selfId, rip);
            rip.bind(unicastProtocol);
        } catch (RuntimeException error) {
            System.err.println(i18n.get("failedToStartPrefix") + error.getMessage());
            System.exit(1); return;
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
        System.out.println(i18n.get("startedPrefix") + selfId + ", config=\"" + configPath + "\"");
        printHelp(i18n);

        // Start command loop
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.out.print("> ");
        while ((line = bufferedReader.readLine()) != null) {
            // Trim and check for empty line
            line = line.trim();
            if (line.isEmpty()) { System.out.print("> "); continue; }

            // Check commands
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            } else if (line.equalsIgnoreCase("help")) {
                printHelp(i18n);
            } else if (line.equalsIgnoreCase("whoami")) {
                System.out.println(i18n.get("whoAmIPrefix") + selfId);
            } else if (line.equalsIgnoreCase("peers")) {
                System.out.println(i18n.get("peersHint"));
            } else if (line.toLowerCase().startsWith("send ")) {
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
        Map<String, String> parsedArguments = Arrays.stream(arguments).collect(Collectors.toMap(
            argument -> argument, argument -> "", (existingValue, newValue) -> existingValue
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
