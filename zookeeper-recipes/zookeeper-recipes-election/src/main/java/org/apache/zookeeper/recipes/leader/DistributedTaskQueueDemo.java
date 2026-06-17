package org.apache.zookeeper.recipes.leader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class DistributedTaskQueueDemo {

    private static final String ZK_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;

    private static final String ELECTION_PATH = "/election";
    private static final String WORKERS_PATH = "/workers";
    private static final String TASKS_PATH = "/tasks";
    private static final String ASSIGNMENTS_PATH = "/assignments";
    private static final String RESULTS_PATH = "/results";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        ZooKeeper zk = new ZooKeeper(ZK_ADDRESS, SESSION_TIMEOUT, event -> {
        });

        ensurePath(zk, ELECTION_PATH);
        ensurePath(zk, WORKERS_PATH);
        ensurePath(zk, TASKS_PATH);
        ensurePath(zk, ASSIGNMENTS_PATH);
        ensurePath(zk, RESULTS_PATH);

        String mode = args[0];

        if ("worker".equalsIgnoreCase(mode)) {
            if (args.length < 2) {
                System.out.println("Missing worker name.");
                printUsage();
                return;
            }

            runWorker(zk, args[1]);
            return;
        }

        if ("submit".equalsIgnoreCase(mode)) {
            if (args.length < 2) {
                System.out.println("Missing task data.");
                printUsage();
                return;
            }

            submitTask(zk, args[1]);
            zk.close();
            return;
        }

        if ("status".equalsIgnoreCase(mode)) {
            printStatus(zk);
            zk.close();
            return;
        }

        printUsage();
        zk.close();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  worker <workerName>");
        System.out.println("  submit <taskData>");
        System.out.println("  status");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  worker Node-A");
        System.out.println("  submit \"Process order 1\"");
        System.out.println("  status");
    }

    private static void runWorker(ZooKeeper zk, String workerName) throws Exception {
        registerWorker(zk, workerName);

        LeaderElectionSupport election = new LeaderElectionSupport();
        election.setZooKeeper(zk);
        election.setHostName(workerName);
        election.setRootNodeName(ELECTION_PATH);

        election.addListener(event -> {
            System.out.println("[" + workerName + "] Election event: " + event);

            if (event == LeaderElectionSupport.EventType.ELECTED_COMPLETE) {
                System.out.println("[" + workerName + "] >>> I AM LEADER <<<");

                Thread leaderThread = new Thread(() -> leaderLoop(zk, workerName));
                leaderThread.setDaemon(true);
                leaderThread.start();
            }

            if (event == LeaderElectionSupport.EventType.READY_COMPLETE) {
                System.out.println("[" + workerName + "] I AM FOLLOWER");
            }
        });

        election.start();

        Thread workerThread = new Thread(() -> workerLoop(zk, workerName));
        workerThread.setDaemon(true);
        workerThread.start();

        Thread.sleep(Long.MAX_VALUE);
    }

    private static void registerWorker(ZooKeeper zk, String workerName) throws Exception {
        String workerPath = WORKERS_PATH + "/" + workerName;
        String assignmentPath = ASSIGNMENTS_PATH + "/" + workerName;

        ensurePath(zk, assignmentPath);

        if (zk.exists(workerPath, false) != null) {
            throw new IllegalStateException(
                "Worker name already exists: " + workerName
                    + ". Use another name or wait for the old session to expire.");
        }

        zk.create(
            workerPath,
            workerName.getBytes(StandardCharsets.UTF_8),
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL
        );

        System.out.println("[" + workerName + "] Registered worker at " + workerPath);
    }

    private static void submitTask(ZooKeeper zk, String taskData) throws Exception {
        String taskPath = zk.create(
            TASKS_PATH + "/task-",
            taskData.getBytes(StandardCharsets.UTF_8),
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT_SEQUENTIAL
        );

        System.out.println("Submitted task: " + taskPath);
    }

    private static void leaderLoop(ZooKeeper zk, String leaderName) {
        while (true) {
            try {
                cleanupAssignmentsOfDeadWorkers(zk, leaderName);
                assignWaitingTasks(zk, leaderName);
                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.println("[" + leaderName + "] Leader loop error: " + e.getMessage());
                sleepQuietly(2000);
            }
        }
    }

    private static void assignWaitingTasks(ZooKeeper zk, String leaderName) throws Exception {
        List<String> tasks = zk.getChildren(TASKS_PATH, false);
        List<String> workers = zk.getChildren(WORKERS_PATH, false);

        if (workers.isEmpty()) {
            System.out.println("[" + leaderName + "] No workers available.");
            return;
        }

        int workerIndex = 0;

        for (String task : tasks) {
            if (zk.exists(RESULTS_PATH + "/" + task, false) != null) {
                continue;
            }

            if (isAssigned(zk, task)) {
                continue;
            }

            String worker = workers.get(workerIndex % workers.size());
            String workerAssignmentRoot = ASSIGNMENTS_PATH + "/" + worker;
            String assignmentPath = workerAssignmentRoot + "/" + task;

            ensurePath(zk, workerAssignmentRoot);

            try {
                zk.create(
                    assignmentPath,
                    task.getBytes(StandardCharsets.UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT
                );

                System.out.println(
                    "[" + leaderName + "] Assigned " + task + " to " + worker
                );
            } catch (KeeperException.NodeExistsException ignored) {
            }

            workerIndex++;
        }
    }

    private static void cleanupAssignmentsOfDeadWorkers(ZooKeeper zk, String leaderName) throws Exception {
        List<String> assignmentWorkers = zk.getChildren(ASSIGNMENTS_PATH, false);

        for (String worker : assignmentWorkers) {
            if (zk.exists(WORKERS_PATH + "/" + worker, false) != null) {
                continue;
            }

            String workerAssignmentRoot = ASSIGNMENTS_PATH + "/" + worker;
            List<String> assignedTasks = zk.getChildren(workerAssignmentRoot, false);

            for (String task : assignedTasks) {
                String assignmentPath = workerAssignmentRoot + "/" + task;

                if (zk.exists(assignmentPath, false) != null) {
                    zk.delete(assignmentPath, -1);
                    System.out.println(
                        "[" + leaderName + "] Recovered " + task
                            + " from dead worker " + worker
                    );
                }
            }
        }
    }

    private static boolean isAssigned(ZooKeeper zk, String task) throws Exception {
        List<String> workers = zk.getChildren(ASSIGNMENTS_PATH, false);

        for (String worker : workers) {
            String assignmentPath = ASSIGNMENTS_PATH + "/" + worker + "/" + task;

            if (zk.exists(assignmentPath, false) != null) {
                return true;
            }
        }

        return false;
    }

    private static void workerLoop(ZooKeeper zk, String workerName) {
        String assignmentRoot = ASSIGNMENTS_PATH + "/" + workerName;

        while (true) {
            try {
                List<String> assignments = zk.getChildren(assignmentRoot, false);

                for (String task : assignments) {
                    processTask(zk, workerName, task);
                }

                Thread.sleep(1000);
            } catch (Exception e) {
                System.out.println("[" + workerName + "] Worker loop error: " + e.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    private static void processTask(ZooKeeper zk, String workerName, String task) throws Exception {
        String taskPath = TASKS_PATH + "/" + task;
        String resultPath = RESULTS_PATH + "/" + task;
        String assignmentPath = ASSIGNMENTS_PATH + "/" + workerName + "/" + task;

        if (zk.exists(taskPath, false) == null) {
            safeDelete(zk, assignmentPath);
            return;
        }

        if (zk.exists(resultPath, false) != null) {
            safeDelete(zk, assignmentPath);
            safeDelete(zk, taskPath);
            return;
        }

        byte[] taskBytes = zk.getData(taskPath, false, null);
        String taskData = new String(taskBytes, StandardCharsets.UTF_8);

        System.out.println("[" + workerName + "] Processing " + task + ": " + taskData);

        Thread.sleep(3000);

        String resultData = "Processed by " + workerName + ": " + taskData;

        try {
            zk.create(
                resultPath,
                resultData.getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT
            );
        } catch (KeeperException.NodeExistsException ignored) {
        }

        safeDelete(zk, assignmentPath);
        safeDelete(zk, taskPath);

        System.out.println("[" + workerName + "] Done " + task);
    }

    private static void printStatus(ZooKeeper zk) throws Exception {
        System.out.println("Workers: " + zk.getChildren(WORKERS_PATH, false));
        System.out.println("Tasks: " + zk.getChildren(TASKS_PATH, false));
        System.out.println("Assignments:");

        for (String worker : zk.getChildren(ASSIGNMENTS_PATH, false)) {
            System.out.println("  " + worker + ": "
                + zk.getChildren(ASSIGNMENTS_PATH + "/" + worker, false));
        }

        System.out.println("Results: " + zk.getChildren(RESULTS_PATH, false));
    }

    private static void ensurePath(ZooKeeper zk, String path) throws Exception {
        if (zk.exists(path, false) != null) {
            return;
        }

        try {
            zk.create(
                path,
                new byte[] {},
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT
            );
        } catch (KeeperException.NodeExistsException ignored) {
        }
    }

    private static void safeDelete(ZooKeeper zk, String path) throws Exception {
        try {
            if (zk.exists(path, false) != null) {
                zk.delete(path, -1);
            }
        } catch (KeeperException.NoNodeException ignored) {
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}