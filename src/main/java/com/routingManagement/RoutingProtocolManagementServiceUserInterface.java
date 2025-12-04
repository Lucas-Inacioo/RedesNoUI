package com.routingManagement;

/** Interface to communicate with RoutingInformationProtocol. */
public interface RoutingProtocolManagementServiceUserInterface {
    void distanceTableIndication(short UCSAPId, int[][] distanceTable);

    void linkCostIndication(short UCSAPId, short neighborUCSAPId, int cost);
}