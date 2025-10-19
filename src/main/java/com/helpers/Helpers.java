package com.helpers;

/**
 * Helper methods for validation.
 */
public class Helpers {
  /**
   * Checks if the given ID is valid.
   * A valid ID is a non-negative integer.
   * 
   * @param id the ID to check
   * 
   * @return true if the ID is valid, false otherwise
   */
  public static boolean isValidId(int id) {
    return id >= 0;
  }

  /**
   * Checks if the given IP address is valid.
   * A valid IP is either "localhost" or matches the IPv4 format.
   * 
   * @param ip the IP address to check
   * 
   * @return true if the IP is valid, false otherwise
   * 
   * @throws NullPointerException if ip is null
   */
  public static boolean isValidIP(String ip) {
    if (ip.equals("localhost")) return true;

    String[] parts = ip.split("\\.");
    if (parts.length != 4) return false;
    for (String part : parts) {
      try {
        int num = Integer.parseInt(part);
        if (num < 0 || num > 255) return false;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the given port number is valid.
   * A valid port is an integer greater than 1024 and up to 65535.
   * 
   * @param port the port number to check
   * 
   * @return true if the port is valid, false otherwise
   */
  public static boolean isValidPort(int port) {
    return port > 1024 && port <= 65535;
  }
}