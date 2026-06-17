# UDPT N03 - Phát triển Apache ZooKeeper

Dự án này được phát triển dựa trên source Apache ZooKeeper, demo các cơ chế xử lý phân tán.
Các tính năng chính đã thêm:

- **Tính năng 1:** Distributed Task Queue kết hợp Leader Election.
- **Tính năng 2:** Distributed Barrier.
- Worker registration bằng ephemeral znode.
- Theo dõi trạng thái phân tán qua ZooKeeper znode.
- Demo failover khi leader hoặc worker bị dừng đột ngột.

## Cấu trúc file demo
```text
zookeeper-recipes/zookeeper-recipes-election/src/main/java/org/apache/zookeeper/recipes/leader/DistributedTaskQueueDemo.java
zookeeper-recipes/zookeeper-recipes-election/src/main/java/org/apache/zookeeper/recipes/barrier/DistributedBarrierDemo.java
```

## Yêu cầu môi trường
- JDK đã cài đặt và chạy được lệnh `java`.
- Maven đã cài đặt và chạy được lệnh `mvn`.
- ZooKeeper server chạy local tại `localhost:2181`.

Kiểm tra:
```bash
java -version
mvn -version
```

## Build project
### Windows PowerShell
```powershell
cd C:\Users\V\Documents\UDPT_N03_Push
mvn -pl zookeeper-recipes/zookeeper-recipes-election -am package -DskipTests '-Dmaven.javadoc.skip=true'
```

Ghi chú: trên PowerShell nên đặt `'-Dmaven.javadoc.skip=true'` trong dấu nháy để Maven nhận đúng tham số.

### MacBook / Linux
```bash
cd ~/Documents/UDPT_N03_Push
mvn -pl zookeeper-recipes/zookeeper-recipes-election -am package -DskipTests -Dmaven.javadoc.skip=true
```

Sau khi build thành công, file jar cần dùng là:
```text
zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar
```

## Chạy ZooKeeper server
### Windows PowerShell
```powershell
cd C:\Users\V\Documents\UDPT_N03_Push\zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin
copy .\conf\zoo_sample.cfg .\conf\zoo.cfg
.\bin\zkServer.cmd
```

Nếu file `conf/zoo.cfg` đã tồn tại thì có thể bỏ qua lệnh `copy`.

### MacBook / Linux
```bash
cd ~/Documents/UDPT_N03_Push/zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin
cp conf/zoo_sample.cfg conf/zoo.cfg
chmod +x bin/*.sh
./bin/zkServer.sh start-foreground
```

Nếu file `conf/zoo.cfg` đã tồn tại thì có thể bỏ qua lệnh `cp`.
Giữ terminal chạy ZooKeeper server trong suốt quá trình demo.

## Tính năng 1: Distributed Task Queue với Leader Election
### Mục tiêu
Nhiều worker cùng chạy, nhưng chỉ một worker được bầu làm leader. Leader chịu trách nhiệm gán task cho các worker. Nếu leader chết, worker khác được bầu làm leader mới và tiếp tục xử lý.

### Các znode sử dụng
```text
/election                  Znode gốc cho leader election
/workers                   Danh sách worker đang sống
/workers/Node-A            Ephemeral znode của worker Node-A
/tasks                     Hàng đợi task chờ xử lý
/tasks/task-0000000000     Task được tạo bằng persistent sequential znode
/assignments               Danh sách task đã được gán cho worker
/assignments/Node-A        Task của worker Node-A
/results                   Kết quả xử lý task
/results/task-0000000000   Kết quả của task
```

### Chạy trên Windows PowerShell
Mở các cửa sổ PowerShell khác nhau và đều đứng ở thư mục gốc project:
```powershell
cd C:\Users\V\Documents\UDPT_N03_Push
```

Chạy worker `Node-A`:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-A
```

Chạy worker `Node-B`:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-B
```

Chạy worker `Node-C`:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-C
```

Gửi task:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo submit "Xử lý đơn hàng 1"
```

Xem trạng thái:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo status
```

### Chạy trên MacBook / Linux
Mở các terminal khác nhau và đều đứng ở thư mục gốc project:
```bash
cd ~/Documents/UDPT_N03_Push
```

Chạy worker `Node-A`:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-A
```

Chạy worker `Node-B`:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-B
```

Chạy worker `Node-C`:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo worker Node-C
```

Gửi task:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo submit "Xử lý đơn hàng 1"
```

Xem trạng thái:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.leader.DistributedTaskQueueDemo status
```

### Kết quả mong đợi
Một worker sẽ được bầu làm leader:
```text
[Node-A] >>> I AM LEADER <<<
```

Các worker còn lại là follower:
```text
[Node-B] I AM FOLLOWER
```

Khi task được gửi vào queue, worker được gán task sẽ xử lý:
```text
[Node-B] Processing task-0000000000: Xử lý đơn hàng 1
[Node-B] Done task-0000000000
```

Demo failover:
1. Tắt terminal của worker đang là leader bằng `Ctrl + C`.
2. Quan sát worker khác được bầu làm leader mới.
3. Gửi thêm task mới.
4. Nếu task vẫn được xử lý thì hệ thống đã failover thành công.

## Tính năng 2: Distributed Barrier
### Mục tiêu
Distributed Barrier dùng để chặn các worker tại một điểm đồng bộ. Chỉ khi đủ số lượng worker yêu cầu cùng đi tới barrier thì barrier mới mở và tất cả worker được đi tiếp.

Ví dụ: đặt threshold là `3`, worker `worker-1`, `worker-2`, `worker-3` đều phải đăng ký vào barrier. Khi đủ 3 worker, barrier mở.

### Các znode sử dụng
```text
/barrier_node              Znode gốc của barrier, lưu threshold
/barrier_node/worker-1     Ephemeral znode của worker-1
/barrier_node/worker-2     Ephemeral znode của worker-2
/barrier_node/worker-3     Ephemeral znode của worker-3
/barrier_node/ready        Persistent znode báo barrier đã mở
```

### Các lệnh hỗ trợ
Class `DistributedBarrierDemo` có 3 chế độ chạy:
```text
setup <barrierPath> <threshold>     Tạo barrier và đặt số worker cần chờ
worker <barrierPath> <workerName>   Chạy worker và chờ barrier mở
status <barrierPath>                Xem trạng thái barrier
```

### Chạy trên Windows PowerShell
Mở PowerShell tại thư mục gốc project:
```powershell
cd C:\Users\V\Documents\UDPT_N03_Push
```

Tạo barrier với threshold bằng `3`:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo setup /barrier_node 3
```

Mở 3 PowerShell khác nhau để chạy 3 worker.

Worker 1:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-1
```

Worker 2:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-2
```

Worker 3:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-3
```

Xem trạng thái:
```powershell
java -cp "zookeeper-recipes\zookeeper-recipes-election\target\zookeeper-recipes-election-3.10.0-SNAPSHOT.jar;zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin\lib\*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo status /barrier_node
```
### Chạy trên MacBook / Linux
Mở terminal tại thư mục gốc project:
```bash
cd ~/Documents/UDPT_N03_Push
```

Tạo barrier với threshold bằng `3`:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo setup /barrier_node 3
```

Mở 3 terminal khác nhau để chạy 3 worker.

Worker 1:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-1
```

Worker 2:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-2
```

Worker 3:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo worker /barrier_node worker-3
```

Xem trạng thái:
```bash
java -cp "zookeeper-recipes/zookeeper-recipes-election/target/zookeeper-recipes-election-3.10.0-SNAPSHOT.jar:zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin/lib/*" org.apache.zookeeper.recipes.barrier.DistributedBarrierDemo status /barrier_node
```

### Kết quả mong đợi
Khi chưa đủ số lượng worker:
```text
[worker-1] Reached barrier, registered at /barrier_node/worker-1
[worker-1] Waiting workers: 1/3
```

Khi đủ 3 worker:
```text
[worker-3] Threshold reached, OPENING barrier
[worker-1] >>> BARRIER PASSED, proceeding <<<
[worker-2] >>> BARRIER PASSED, proceeding <<<
[worker-3] >>> BARRIER PASSED, proceeding <<<
```

## Kiểm tra trực tiếp bằng zkCli
### Windows PowerShell

```powershell
cd C:\Users\V\Documents\UDPT_N03_Push\zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin
.\bin\zkCli.cmd -server 127.0.0.1:2181
```

### MacBook / Linux
```bash
cd ~/Documents/UDPT_N03_Push/zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin
./bin/zkCli.sh -server 127.0.0.1:2181
```

Một số lệnh kiểm tra:
```text
ls /workers
ls /tasks
ls /assignments
ls /results
ls /barrier_node
get /barrier_node
```

## Các điểm phân tán được minh hoạ
- Leader Election: nhiều worker cùng tranh quyền leader, chỉ một worker làm leader tại một thời điểm.
- Membership: worker sống/chết được quản lý bằng ephemeral znode.
- Distributed Queue: task được lưu trong ZooKeeper bằng persistent sequential znode.
- Coordination: leader gán task cho worker qua `/assignments`.
- Fault Tolerance: khi leader chết, leader mới được bầu và tiếp tục xử lý.
- Distributed Barrier: nhiều worker bị chặn tại một điểm đồng bộ cho đến khi đủ số lượng yêu cầu.
- Watcher: worker đợi thay đổi trên znode để biết khi nào barrier mở.
