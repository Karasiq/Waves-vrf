package com.wavesplatform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class VRFMiningModel {

    private static final Gson gson;

    static {
        GsonBuilder gb = new GsonBuilder();
        gb.registerTypeAdapter(Block.class, new Block.BlockSerializer());
        gson = gb.create();
    }

    public static void main(String[] args) {
        final long startTime = System.nanoTime();
        final int collisionTime = Integer.parseInt(System.getProperty("collision-time", "2000"));
        final int blocksCount = Integer.parseInt(System.getProperty("blocks", "20000"));
        final boolean removeMiner = System.getProperty("remove-miner") != null;
        final boolean vrf = System.getProperty("vrf") != null;

        long[] balances = new long[]{696130740849591L, 584663948431762L, 565684463008520L, 451319649380364L, 277314279564189L, 200545108614574L, 194897655003082L, 189208953087251L, 180421440289660L, 166076857835215L, 156588226789282L, 135394643161196L, 134338250205059L, 91219789332050L, 90804512599189L, 88395780879571L, 59618877960742L, 58388516240136L, 56647488658860L, 54500749599839L, 54083058293478L, 48970419104883L, 45186788259840L, 41668930838939L, 36424643943712L, 31588369868044L, 28108818433508L, 26379654222207L, 23882294276251L, 23457213584555L, 21302767022694L, 19913573038156L, 19511841596298L, 19038014348129L, 15191511062837L, 14244695735379L, 13771197176014L, 13683872280628L, 13153096596354L, 12836807396062L, 12029579686973L, 11601153985908L, 10192135643398L, 10026305858753L, 9550205967420L, 9107789074060L, 8891518590896L, 8695325553819L, 8218238546553L, 7605120493940L, 7414237207670L, 7024278800831L, 6864902902812L, 6777056220908L, 6327199803885L, 5669337253050L, 5633057260504L, 5629400675627L, 5539391176359L, 5483990792059L, 5238341614268L, 5112736303898L, 4839269931909L, 4776495942937L, 4676105348862L, 4022924395978L, 3956810684951L, 3420143143833L, 3383009211777L, 2935803053724L, 2664014407404L, 2529739013144L, 2444614976442L, 2246612105466L, 2243270140505L, 2213677265404L, 2169068659756L, 2152179682290L, 2147328964247L, 2137822549125L, 2133246204942L, 2120083786395L, 2119233320433L, 2108052025834L, 2100052488256L, 2085347663777L, 2078042393935L, 2063885507024L, 2030396321825L, 2007952744617L, 2006145764394L, 2005363551502L, 1981726192038L, 1977769244175L, 1976712695435L, 1844353727879L, 1831479339006L, 1751113897691L, 1681868974068L, 1648572632705L, 1577265601962L, 1560789288031L, 1499234218453L, 1496734185419L, 1468791705360L, 1455646875507L, 1421158776687L, 1420578277349L, 1404188034448L, 1382280898634L, 1315214433975L, 1185772804585L, 1139029215377L, 1088747053814L, 1020200051716L, 1012249232651L, 1007197602741L, 898256158669L, 828863734141L, 814629607270L, 783921920694L, 764779195424L, 751060548767L, 701076942370L, 667363280875L, 666095080263L, 647381328271L, 636504421464L, 600106715024L, 556176710882L, 449478083512L, 432194475522L, 429458149802L, 417564351012L, 415464948036L, 383211470240L, 351596696683L, 350102868123L, 334771138626L, 300134019188L, 268062979567L, 235453454740L, 234707102817L, 227824191698L, 224914471640L, 208095189807L, 176533551340L, 172304136802L, 133715881383L, 133066518202L, 104047396302L, 101966253133L, 101703291904L, 100646275195L, 100289460514L, 100172339466L, 56470546206L, 28204112255L, 26961032614L, 22911245543L, 22488262716L, 18000125638L, 9418684985L, 6019463233L};

        final long balancesSum = Arrays.stream(balances).sum();
        System.out.printf("Total stack is %.1fkk WAVES\n", balancesSum * 10e-15);

        /* ArrayList<Long> balances = new ArrayList<>();
        final long totalBalanceRequired = 100000000L * 100000000;
        long totalBalance = 0L;
        while (totalBalance < (totalBalanceRequired * 0.9)) {
            final double share = ThreadLocalRandom.current().nextDouble(0.2);
            final long value = (long)(totalBalanceRequired * share);
            final long newTotal = totalBalance + value;
            if (newTotal <= totalBalanceRequired) {
                balances.add(value);
                totalBalance = newTotal;
            }
        } */

        ArrayList<Miner> miners = new ArrayList<>();
        int[] minersBlocks = new int[balances.length];
        for (long balance : balances) {
            byte[] sk = Crypto.provider.generatePrivateKey();
            byte[] pk = Crypto.provider.generatePublicKey(sk);
            miners.add(new Miner(pk, sk, balance));
        }


        ArrayList<Block> blocks = new ArrayList<>();
        blocks.add(new Block(miners));
        for (int i = 0; i < 100; i++) {
            blocks.add(new Block(miners, blocks.get(blocks.size() - 1)));
        }

        Miner removedMiner = null;
        if (removeMiner) removedMiner = miners.remove(0);
        int collisions = 0, blocksInLine = 0, maxBlocksInLine = 0, maxBlocksHeight = 0, firstLine = 0;
        long totalTime = 0;
        int totalBlocks = 0;
        try (FileWriter writer = new FileWriter("blocks-" + Block.MinTime + ".json", false)) {
            writer.append("[").append('\n');
            for (int i = 0; i < blocksCount; i++) {
                final Block prev = blocks.get(blocks.size() - 1);
                List<Block> mined = miners.parallelStream()
                        .map(m -> new Block(m, prev, blocks.get(blocks.size() - 101), vrf))
                        .collect(Collectors.toList());

                Block block = mined.stream().min(Comparator.comparingLong(b -> b.delay)).get();

                for (Block other : mined)
                    if (other.miner != block.miner) {
                        final long delta = Math.abs(other.time - block.time);
                        if (delta < collisionTime) {
                            collisions++;
                            // System.out.println("Block overlaps within " + delta + " ms");
                        }
                    }

                final double baseTargetChange = (((double) block.baseTarget / prev.baseTarget) - 1.0) * 100.0;
                final double averageTime = (double) totalTime / totalBlocks / 1000;
                System.out.printf("Block generated: %d, bt = %d, change=%.2f%%, avg time=%.2f\n", block.height, block.baseTarget, baseTargetChange, averageTime);
                // System.out.printf("Block generated: %d\n", block.height);

                totalTime += block.delay;
                totalBlocks++;
                block.miner.balance += 600000000; // 6 waves reward
                blocks.add(block);

                writer.write(gson.toJson(block));
                if (i < blocksCount - 1) writer.append(",");
                writer.append('\n');
                writer.flush();

                final int minerCameo = blocksCount / 2;

                if (removeMiner && i == minerCameo) {
                    System.out.printf("Adding miner, average delay without miner = %.2f sec\n", (double)totalTime / totalBlocks / 1000);
                    totalTime = 0;
                    totalBlocks = 0;
                    miners.add(0, removedMiner);
                }

                if (!removeMiner || i > minerCameo) {
                    int indexOfMiner = miners.indexOf(block.miner);
                    minersBlocks[indexOfMiner]++;
                    if (indexOfMiner == 0) {
                        blocksInLine++;
                    } else {
                        if (maxBlocksInLine == 0) firstLine = blocksInLine;
                        if (blocksInLine != 0) {
                            if (blocksInLine > maxBlocksInLine) {
                                maxBlocksInLine = blocksInLine;
                                maxBlocksHeight = block.height - maxBlocksInLine;
                            }
                            blocksInLine = 0;
                        }
                    }
                }
            }
            writer.append("]");
            final double collisionsPct = ((double) collisions) / blocksCount * 100;
            final double averageTime = (double) totalTime / totalBlocks / 1000;

            System.out.printf("Block time = %d, %d total, %d overlapped (%.2f pct), avg delay is %.2f sec\n", Block.MinTime, blocksCount, collisions, collisionsPct, averageTime);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

        // System.out.printf("Max blocks in line = %d (at height %d), first = %d\n", maxBlocksInLine, maxBlocksHeight, firstLine);

        /* final long sumOfBalances = miners.stream().mapToLong(m -> m.balance).sum();
        final long sumOfBlocks = Arrays.stream(minersBlocks).sum();
        double[] minersRatio = new double[balances.length], minersPredicted = new double[balances.length];
        for (int i = 0; i < miners.size(); i++) {
            long minerBalance = miners.get(i).balance;
            minersPredicted[i] = sumOfBlocks * minerBalance / (double) sumOfBalances;
            if (minersPredicted[i] > 0) minersRatio[i] = minersBlocks[i] / minersPredicted[i];

            BigDecimal minerShare = new BigDecimal(minerBalance).setScale(6, RoundingMode.HALF_UP).divide(new BigDecimal(sumOfBalances).setScale(6, RoundingMode.HALF_UP), RoundingMode.HALF_UP);
            System.out.printf("Balance = %d, share = %.2f, blocks = %d, performance = %.2f\n", minerBalance, minerShare, minersBlocks[i], minersRatio[i]);
            // System.out.println(minerBalance + " " + minerShare + " " + miners_blocks[i] + " " + miners_ratio[i]);
        } */

        System.out.printf("Finished in %.2f seconds", ((double) (System.nanoTime() - startTime)) / 1000000000L);
    }
}
