package com.wavesplatform;

import com.google.gson.*;
import com.wavesplatform.wavesj.Base58;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jcajce.provider.digest.Blake2b;
import org.whispersystems.curve25519.VrfSignatureVerificationFailedException;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Block {
    public static final long MinTime = Integer.parseInt(System.getProperty("block-time", "5000"));
    private static final BigDecimal MaxHit = new BigDecimal(2).pow(64).subtract(new BigDecimal(1));

    public Miner miner;
    public Block prev;
    public long baseTarget;
    public long time;
    public int height;
    public long delay;
    public byte[] genSig;
    public byte[] vrf;

    public Block(ArrayList<Miner> miners) {
        this.miner = miners.get(ThreadLocalRandom.current().nextInt(0, miners.size()));
        this.prev = null;
        this.baseTarget = 60;
        this.delay = 60000;
        this.time = 0;
        this.height = 0;
        this.genSig = new byte[96];
        ThreadLocalRandom.current().nextBytes(this.genSig);
        this.vrf = new byte[32];
        ThreadLocalRandom.current().nextBytes(this.vrf);
    }

    public Block(ArrayList<Miner> miners, Block prev) {
        this.miner = miners.get(ThreadLocalRandom.current().nextInt(0, miners.size()));
        this.prev = prev;
        this.baseTarget = 60;
        this.delay = 60000;
        this.time = prev.time + this.delay;
        this.height = prev.height + 1;
        this.genSig = new byte[96];
        ThreadLocalRandom.current().nextBytes(this.genSig);
        this.vrf = new byte[32];
        ThreadLocalRandom.current().nextBytes(this.vrf);
    }

    private byte[] createGenSig(Block prev) {
        byte[] output = new byte[64];
        System.arraycopy(prev.vrf, 0, output, 0, 32);
        System.arraycopy(prev.miner.pk, 0, output, 32, 32);
        Blake2b.Blake2b256 blake2b256 = new Blake2b.Blake2b256();
        return blake2b256.digest(output);
    }

    public Block(Miner miner, Block prev, Block prev100, boolean vrf) {
        this.miner = miner;
        this.prev = prev;
        this.height = prev.height + 1;

        if (vrf) {
            this.genSig = Crypto.provider.calculateVrfSignature(Crypto.provider.getRandom(32), miner.sk, prev100.vrf);
            try {
                this.vrf = Crypto.provider.verifyVrfSignature(miner.pk, prev100.vrf, this.genSig);
            } catch (VrfSignatureVerificationFailedException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.vrf = createGenSig(prev);
            this.genSig = this.vrf;
        }

        byte[] hitSource = Arrays.copyOfRange(this.vrf, 0, 8);
        ArrayUtils.reverse(hitSource);
        BigInteger hit = new BigInteger(1, hitSource);
        double h = new BigDecimal(hit).divide(MaxHit, MathContext.DECIMAL128).doubleValue();

        final int C1 = 70000;
        final double C2 = 5e17;
        this.delay = (long) (MinTime + C1 * Math.log(1 - C2 * Math.log(h) / prev.baseTarget / miner.balance));
        // this.delay = (long) (MinTime + Math.log(1 - Math.log(h) / (miner.balance * prev.baseTarget) * 100000000L * 100000000L * 50L) * 70000L);
        this.time = prev.time + this.delay;

        double maxDelay = 90.;
        double minDelay = 30.;

        long avg = (this.time - prev.prev.prev.time) / 3 / 1000;
        if (avg > maxDelay) this.baseTarget = prev.baseTarget + Math.max(1, prev.baseTarget / 100);
        else if (avg < minDelay) this.baseTarget = prev.baseTarget - Math.max(1, prev.baseTarget / 100);
        else this.baseTarget = prev.baseTarget;
    }

    public static class BlockSerializer implements JsonSerializer<Block> {
        @Override
        public JsonElement serialize(Block src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            json.add("privateKey", new JsonPrimitive(Base58.encode(src.miner.sk)));
            json.add("publicKey", new JsonPrimitive(Base58.encode(src.miner.pk)));
            json.add("balance", new JsonPrimitive(src.miner.balance));
            json.add("baseTarget", new JsonPrimitive(src.baseTarget));
            json.add("delay", new JsonPrimitive(src.delay));
            json.add("height", new JsonPrimitive(src.height));
            json.add("time", new JsonPrimitive(src.time));
            json.add("vrf", new JsonPrimitive(Base58.encode(src.vrf)));
            json.add("genSig", new JsonPrimitive(Base58.encode(src.genSig)));
            return json;
        }
    }
}
