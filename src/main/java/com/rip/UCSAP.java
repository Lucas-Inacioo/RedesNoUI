package com.rip;

import com.helpers.Helpers;

public abstract class UCSAP implements Runnable {
  protected final int UCSAPId;
  protected final String hostName;
  protected final int port;

  protected UCSAP(int UCSAPId, String hostName, int port) {
    this.UCSAPId = UCSAPId;
    this.hostName = hostName;
    this.port = port;
  }

  @Override
  public void run() {
    System.out.println("Running UCSAP Node " + UCSAPId + " on " + hostName + ":" + port);
    try {
      // validate
      if (!Helpers.isValidId(UCSAPId))   throw new IllegalArgumentException("Invalid UCSAP ID: " + UCSAPId);
      if (!Helpers.isValidIP(hostName))  throw new IllegalArgumentException("Invalid host: " + hostName);
      if (!Helpers.isValidPort(port))    throw new IllegalArgumentException("Invalid port: " + port);

      // main loop
      while (!Thread.currentThread().isInterrupted()) {
        // work / receive / send
      }
    } catch (IllegalArgumentException exception) {
      // Validation failed
      System.err.println("[NODE " + UCSAPId + "] start failed: " + exception.getMessage());
    } catch (Exception exception) {
      System.err.println("[NODE " + UCSAPId + "] crashed: " + exception.getMessage());
    } finally {
      // cleanup resources (close socket, etc.)
      System.out.println("UCSAP Node " + UCSAPId + " has stopped.");
    }
  }
}