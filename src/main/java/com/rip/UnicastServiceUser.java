package com.rip;

import com.unicast.UnicastServiceInterface;

public class UnicastServiceUser implements UnicastServiceUserInterface {
    /** The UCSAP id of this UnicastServiceUser */
    private final short selfId;

    /** The UnicastProtocol used by this UnicastServiceUser */
    private UnicastServiceInterface unicastProtocol;

    /**
     * Constructs a UnicastServiceUser with the given selfId.
     *
     * @param selfId The UCSAP id of this UnicastServiceUser
     */
    public UnicastServiceUser(short selfId) {
        this.selfId = selfId;
    }

    /**
     * Constructs a UnicastServiceUser with the given selfId and binds it to the specified UnicastProtocol.
     * 
     * @param selfId The UCSAP id of this UnicastServiceUser
     * @param unicastProtocol The UnicastProtocol to bind to 
     */
    public UnicastServiceUser(short selfId, UnicastServiceInterface unicastProtocol) {
        this.selfId = selfId;
        this.unicastProtocol = unicastProtocol;
    }

    /**
     * Binds this UnicastServiceUser to the given UnicastProtocol.
     *
     * @param unicastProtocol The UnicastProtocol to bind to
     */
    public void bind(UnicastServiceInterface unicastProtocol) {
        this.unicastProtocol = unicastProtocol;
    }

    /**
     * Sends a message to the specified destination UCSAP id using the unicast protocol.
     *
     * @param dest The destination UCSAP id
     * @param msg  The message to send
     *
     * @throws IllegalStateException if the UnicastServiceUser is not yet bound to a UnicastProtocol
     */
    public void send(short dest, String msg) throws IllegalStateException {
        if (unicastProtocol == null) {
            throw new IllegalStateException("UnicastServiceUser not bound to protocol yet.");
        }
        unicastProtocol.UPDataReq(dest, msg);
    }

    /**
     * Handles incoming data indications from the UnicastProtocol.
     *
     * @param originUCSAPId Origin UCSAP id
     * @param data          The received data
     */
    @Override
    public void UPDataInd(short originUCSAPId, String data) {
        String from = (originUCSAPId >= 0) ? Short.toString(originUCSAPId) : "unknown";
        System.out.println("\n[RECV] from " + from + ": " + data);
        System.out.print("> ");
    }
}