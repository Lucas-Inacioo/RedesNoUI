package com.unicast;

/**
 * Defines the Unicast service API: exposes the primitive to send a unicast data request.
 */
public interface UnicastServiceInterface {

    /**
     * Sends a Unicast Data Request to the specified UCSAP.
     *
     * @param destUCSAPId the destination UCSAP identifier to which the message should be sent
     * @param data the application payload (string) to deliver to the destination
     */
    void UPDataReq(short destUCSAPId, String data);
}
