package com.xenowarriors.journey;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;

import java.util.*;

/**
 * A journey's route: rows of steps from the start to the boss. Here and there the path forks into two routes for a few
 * rows (one riskier with an event panel, one safer with a healing pod) - step onto one and the other closes - and they
 * meet again before the boss.
 */
public final class JourneyMap {
    public static final int AHEAD = 0, HERE = 1, DONE = 2, CLOSED = 3;

    public static final class Node {
        public final int id, row, lane;
        public int kind, state;
        public final List<Integer> next = new ArrayList<>();
        java.util.UUID label;
        Node(int id, int row, int lane, int kind) { this.id = id; this.row = row; this.lane = lane; this.kind = kind; }
    }

    public final List<Node> nodes = new ArrayList<>();
    public final int rows;

    private JourneyMap(int rows) { this.rows = rows; }

    public Node get(int id) { return id >= 0 && id < nodes.size() ? nodes.get(id) : null; }

    /** Where the route forks: {first row, last row} of each two-lane stretch. */
    static int[][] forks(int rows) {
        return switch (rows) {
            case 10 -> new int[][]{{3, 5}};
            case 14 -> new int[][]{{2, 4}, {8, 11}};
            default -> new int[][]{{2, 4}, {7, 10}, {13, 15}};
        };
    }

    public static JourneyMap generate(int rows, RandomSource rnd) {
        JourneyMap m = new JourneyMap(rows);
        int[][] forks = forks(rows);
        List<List<Node>> byRow = new ArrayList<>();
        for (int row = 0; row <= rows; row++) {
            int[] fork = null;
            for (int[] f : forks) if (row >= f[0] && row <= f[1]) fork = f;
            List<Node> here = new ArrayList<>();
            if (fork == null) {
                int kind = row == 0 ? JourneyRules.START : row == rows ? JourneyRules.BOSS : row == 1 ? JourneyRules.BATTLE
                        : row == rows - 1 && rnd.nextInt(5) < 3 ? JourneyRules.HEAL : pick(rnd);
                here.add(m.add(row, 0, kind));
            } else {
                // two routes: the risky one gets an event panel in the middle, the safe one a healing pod
                int mid = (fork[0] + fork[1]) / 2;
                boolean riskyLeft = rnd.nextBoolean();
                for (int lane : new int[]{-1, 1}) {
                    boolean risky = (lane == -1) == riskyLeft;
                    int kind = row == mid ? (risky ? JourneyRules.EVENT : JourneyRules.HEAL)
                            : risky ? (rnd.nextInt(3) == 0 ? JourneyRules.SCENARIO : JourneyRules.BATTLE)
                            : (rnd.nextInt(4) == 0 ? JourneyRules.EVENT : rnd.nextBoolean() ? JourneyRules.SCENARIO : JourneyRules.BATTLE);
                    here.add(m.add(row, lane, kind));
                }
            }
            byRow.add(here);
        }
        for (int row = 0; row < rows; row++) {
            List<Node> a = byRow.get(row), b = byRow.get(row + 1);
            for (Node n : a) for (Node t : b) if (a.size() == 1 || b.size() == 1 || n.lane == t.lane) n.next.add(t.id);
        }
        m.nodes.get(0).state = HERE;
        return m;
    }

    private static int pick(RandomSource rnd) {
        int x = rnd.nextInt(20);
        return x < 10 ? JourneyRules.BATTLE : x < 16 ? JourneyRules.SCENARIO : JourneyRules.EVENT;
    }

    private Node add(int row, int lane, int kind) { Node n = new Node(nodes.size(), row, lane, kind); nodes.add(n); return n; }

    /** Step onto {@code chosen}: every step you can no longer reach closes. Returns the ones that just closed. */
    public List<Node> choose(int chosen) {
        Set<Integer> reach = new HashSet<>();
        Deque<Integer> q = new ArrayDeque<>(List.of(chosen));
        while (!q.isEmpty()) { int i = q.poll(); if (reach.add(i)) q.addAll(nodes.get(i).next); }
        int row = nodes.get(chosen).row;
        List<Node> closed = new ArrayList<>();
        for (Node n : nodes) if (n.row >= row && !reach.contains(n.id) && n.state == AHEAD) { n.state = CLOSED; closed.add(n); }
        return closed;
    }

    /** The platform's centre. */
    public BlockPos pos(int slot, Node n) {
        BlockPos s = JourneyWorld.start(slot);
        int wobble = n.lane == 0 ? (int) Math.round(Math.sin(n.row * 1.3) * 5) : 0;
        return new BlockPos(s.getX() + n.lane * 10 + wobble, s.getY() + n.row, s.getZ() + n.row * JourneyWorld.GAP);
    }

    /** For the client: each step's kind, row, lane, state and links. */
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("rows", rows);
        ListTag l = new ListTag();
        for (Node n : nodes) {
            CompoundTag nt = new CompoundTag();
            nt.putInt("k", n.kind); nt.putInt("r", n.row); nt.putInt("l", n.lane); nt.putInt("s", n.state);
            nt.put("n", new IntArrayTag(n.next.stream().mapToInt(Integer::intValue).toArray()));
            l.add(nt);
        }
        t.put("nodes", l);
        return t;
    }
}
