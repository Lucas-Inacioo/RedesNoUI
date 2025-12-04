package com.routingManagement;

import com.routingInformationProtocol.RoutingProtocolManagementInterface;

/** Implementation of a RoutingManagementApplication. */
public class RoutingManagementApplication implements RoutingProtocolManagementServiceUserInterface {
    /** The UCSAP id of this RoutingManagementApplication */
    private final short selfId = 0;

    /** The UnicastProtocol used by this RoutingInformationProtocol */
    private RoutingProtocolManagementInterface routingProtocolManagementInterface;

    /**
     * Constructs a RoutingManagementApplication.
     */
    public RoutingManagementApplication() { }

    /**
     * Constructs a RoutingManagementApplication and binds it to the specified RoutingProtocolManagementInterface.
     * 
     * @param routingProtocolManagementInterface The RoutingProtocolManagementInterface to bind to 
     */
    public RoutingManagementApplication(RoutingProtocolManagementInterface routingProtocolManagementInterface) {
        this.routingProtocolManagementInterface = routingProtocolManagementInterface;
    }

    /**
     * Binds this RoutingManagementApplication to the given RoutingProtocolManagementInterface.
     *
     * @param routingProtocolManagementInterface The RoutingProtocolManagementInterface to bind to
     */
    public void bind(RoutingProtocolManagementInterface routingProtocolManagementInterface) {
        this.routingProtocolManagementInterface = routingProtocolManagementInterface;
    }

    @Override
    public void distanceTableIndication(short UCSAPId, int[][] distanceTable) {
        System.out.println("Distance table indication received from UCSAP ID: " + UCSAPId);
    }

    @Override
    public void linkCostIndication(short UCSAPId, short neighborUCSAPId, int cost) {
        System.out.println("Link cost indication received from UCSAP ID: " + UCSAPId + " for neighbor UCSAP ID: " + neighborUCSAPId + " with cost: " + cost);
    }
}