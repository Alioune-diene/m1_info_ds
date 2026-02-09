package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

public class KeyExchangeMessageData {
    private final int fromId;
    private final int toId;
    private final byte[] publicKey;
    private final boolean isResponse;

    public KeyExchangeMessageData(int fromId, int toId, byte[] publicKey, boolean isResponse) {
        this.fromId = fromId;
        this.toId = toId;
        this.publicKey = publicKey.clone();
        this.isResponse = isResponse;
    }

    public int getFromId() { return fromId; }
    public int getToId() { return toId; }
    public byte[] getPublicKey() { return publicKey.clone(); }
    public boolean isResponse() { return isResponse; }

    @Override
    public String toString() {
        return String.format("KeyExchangeMessageData{from=%d, to=%d, isResponse=%s, keyLen=%d}", fromId, toId, isResponse, publicKey.length);
    }
}