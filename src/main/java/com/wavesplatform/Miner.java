package com.wavesplatform;

public class Miner {
    public byte[] pk;
    public byte[] sk;
    public long balance;

    public Miner(byte[] pk, byte[] sk, long balance) {
        this.pk = pk;
        this.sk = sk;
        this.balance = balance;
    }
}