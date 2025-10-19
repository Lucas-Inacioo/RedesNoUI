package com.rip;

import com.helpers.Helpers;

public class Node extends UCSAP implements NodeInterface {

    public Node(int UCSAPId, String hostName, int port) {
        super(UCSAPId, hostName, port);
    }

    @Override
    public void UPDataInd(short originUCSAPId, String data) {
        System.out.println("Node " + this.UCSAPId + " received data from UCSAP " + originUCSAPId + ": " + data);
    }

    @Override
  public void run() {
    System.out.println("Running Node " + this.UCSAPId + " on " + this.hostName + ":" + this.port);
    try {
      // Abort thread if parameters are invalid
      if (!Helpers.isValidId(this.UCSAPId))   throw new IllegalArgumentException("Invalid Node ID: " + this.UCSAPId);
      if (!Helpers.isValidIP(this.hostName))  throw new IllegalArgumentException("Invalid host: " + this.hostName);
      if (!Helpers.isValidPort(this.port))    throw new IllegalArgumentException("Invalid port: " + this.port);

      // Main loop:
      while (!Thread.currentThread().isInterrupted()) {
        // Work / receive / send
      }
    } catch (IllegalArgumentException exception) {
      // Validation failed
      System.err.println("[Node " + this.UCSAPId + "] start failed: " + exception.getMessage());
    } catch (Exception exception) {
      System.err.println("[Node " + this.UCSAPId + "] crashed: " + exception.getMessage());
    } finally {
      // Cleanup resources (close socket, etc.)
      System.out.println("UCSAP Node " + this.UCSAPId + " has stopped.");
    }
  }
}
