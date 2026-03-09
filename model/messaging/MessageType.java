package fr.uga.im2ag.m1info.overlay.messaging;

public enum MessageType {
    /** A data message being routed along the virtual ring (left or right). */
    DATA,

    /**
     * Hop-by-hop routing message: carries a physical path and forwards
     * itself hop by hop until it reaches the destination.
     */
    ROUTED,

    /** Sent when a node joins the ring to announce itself to its ring neighbors. */
    HELLO,

    /** Acknowledgement of a HELLO. */
    HELLO_ACK
}
