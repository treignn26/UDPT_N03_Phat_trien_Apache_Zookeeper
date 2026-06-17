package org.apache.zookeeper.recipes.barrier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class DistributedBarrierDemo {

    private static final String ZK_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String READY_NODE_NAME = "ready";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        ZooKeeper zk = new ZooKeeper(ZK_ADDRESS, SESSION_TIMEOUT, event -> {
        });

        String mode = args[0];

        if ("setup".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                System.out.println("Missing barrier path or threshold.");
                printUsage();
                return;
            }

            setupBarrier(zk, args[1], Integer.parseInt(args[2]));
            zk.close();
            return;
        }

        if ("worker".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                System.out.println("Missing barrier path or worker name.");
                printUsage();
                return;
            }

            runWorker(zk, args[1], args[2]);
            zk.close();
            return;
        }

        if ("status".equalsIgnoreCase(mode)) {
            if (args.length < 2) {
                System.out.println("Missing barrier path.");
                printUsage();
                return;
            }

            printStatus(zk, args[1]);
            zk.close();
            return;
        }

        printUsage();
        zk.close();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  setup <barrierPath> <threshold>");
        System.out.println("  worker <barrierPath> <workerName>");
        System.out.println("  status <barrierPath>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  setup /barrier_node 3");
        System.out.println("  worker /barrier_node worker-1");
        System.out.println("  status /barrier_node");
    }

    private static void setupBarrier(ZooKeeper zk, String barrierPath, int threshold) throws Exception {
        ensurePath(zk, barrierPath, String.valueOf(threshold).getBytes(StandardCharsets.UTF_8));
        System.out.println("Barrier created at " + barrierPath + " with threshold=" + threshold);
    }

    private static void runWorker(ZooKeeper zk, String barrierPath, String workerName) throws Exception {
        int threshold = readThreshold(zk, barrierPath);
        String workerPath = barrierPath + "/" + workerName;

        if (zk.exists(workerPath, false) != null) {
            throw new IllegalStateException("Worker already registered: " + workerName);
        }

        zk.create(
            workerPath,
            workerName.getBytes(StandardCharsets.UTF_8),
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL
        );

        System.out.println("[" + workerName + "] Reached barrier, registered at " + workerPath);

        waitForThreshold(zk, barrierPath, threshold, workerName);
        openBarrierIfNeeded(zk, barrierPath, workerName);
        waitUntilOpen(zk, barrierPath);

        System.out.println("[" + workerName + "] >>> BARRIER PASSED, proceeding <<<");
    }

    private static int readThreshold(ZooKeeper zk, String barrierPath) throws Exception {
        if (zk.exists(barrierPath, false) == null) {
            throw new IllegalStateException(
                "Barrier not set up yet: " + barrierPath + ". Run 'setup' first."
            );
        }

        byte[] data = zk.getData(barrierPath, false, null);
        return Integer.parseInt(new String(data, StandardCharsets.UTF_8));
    }

    private static void waitForThreshold(ZooKeeper zk, String barrierPath, int threshold, String workerName)
        throws Exception {
        while (true) {
            CountDownLatch latch = new CountDownLatch(1);
            List<String> children = zk.getChildren(barrierPath, event -> latch.countDown());
            int waitingCount = countWaitingWorkers(children);

            System.out.println("[" + workerName + "] Waiting workers: " + waitingCount + "/" + threshold);

            if (waitingCount >= threshold || children.contains(READY_NODE_NAME)) {
                return;
            }

            latch.await();
        }
    }

    private static int countWaitingWorkers(List<String> children) {
        int count = 0;

        for (String child : children) {
            if (!READY_NODE_NAME.equals(child)) {
                count++;
            }
        }

        return count;
    }

    private static void openBarrierIfNeeded(ZooKeeper zk, String barrierPath, String workerName) throws Exception {
        try {
            zk.create(
                barrierPath + "/" + READY_NODE_NAME,
                ("opened-by-" + workerName).getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT
            );

            System.out.println("[" + workerName + "] Threshold reached, OPENING barrier");
        } catch (KeeperException.NodeExistsException ignored) {
        }
    }

    private static void waitUntilOpen(ZooKeeper zk, String barrierPath) throws Exception {
        String readyPath = barrierPath + "/" + READY_NODE_NAME;

        while (true) {
            CountDownLatch latch = new CountDownLatch(1);
            boolean isOpen = zk.exists(readyPath, event -> latch.countDown()) != null;

            if (isOpen) {
                return;
            }

            latch.await();
        }
    }

    private static void printStatus(ZooKeeper zk, String barrierPath) throws Exception {
        if (zk.exists(barrierPath, false) == null) {
            System.out.println("Barrier not set up: " + barrierPath);
            return;
        }

        int threshold = readThreshold(zk, barrierPath);
        List<String> children = zk.getChildren(barrierPath, false);

        System.out.println("Barrier: " + barrierPath);
        System.out.println("Threshold: " + threshold);
        System.out.println("Workers waiting: " + countWaitingWorkers(children));
        System.out.println("Children: " + children);
        System.out.println("Opened: " + children.contains(READY_NODE_NAME));
    }

    private static void ensurePath(ZooKeeper zk, String path, byte[] data) throws Exception {
        if (zk.exists(path, false) != null) {
            return;
        }

        try {
            zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
        }
    }
}
