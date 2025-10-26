package com.rip;

/** Interface for a UnicastServiceUser. */
public interface UnicastServiceUserInterface {
    /** Handles incoming data indications from the UnicastProtocol.
     *
     * @param originUCSAPId Origin UCSAP id
     * @param data The received data
    */
    void UPDataInd(short originUCSAPId, String data);
}