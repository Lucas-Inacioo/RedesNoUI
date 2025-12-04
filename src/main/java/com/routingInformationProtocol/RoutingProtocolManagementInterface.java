package com.routingInformationProtocol;

/** Interface to communicate with RoutingManagementApplication. */
public interface RoutingProtocolManagementInterface {
    /**
     * Asks the RoutingInformationProtocol to return its distance table.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     *
     * @return true if the distance table was successfully retrieved, false otherwise
     */
    boolean getDistanceTable(short UCSAPId);

    /**
     * Asks the RoutingInformationProtocol to get the cost of the link to a neighbor.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     * @param neighbor The UCSAP id of the neighbor
     * 
     * @return true if the link cost was successfully retrieved, false otherwise
     */
    boolean getLinkCost(short UCSAPId, short neighborUCSAPId);

    /**
     * Asks the RoutingInformationProtocol to set the cost of the link to a neighbor.
     *
     * @param UCSAPId The UCSAP id of the RoutingInformationProtocol
     * @param neighbor The UCSAP id of the neighbor
     * @param cost The new cost of the link
     * 
     * @return true if the link cost was successfully set, false otherwise
     */
    boolean setLinkCost(short UCSAPId, short neighborUCSAPId, int cost);
}