package com.wavesplatform;

public class Miner {
    public final byte[] publicKey;
    public final byte[] privateKey;
    public long balance;

    public Miner(byte[] publicKey, byte[] privateKey, long balance) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.balance = balance;
    }
}
