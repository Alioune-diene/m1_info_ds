# Introduction to Distributed Systems

M1 Informatique - Université Grenoble Alpes

## Repository Structure

```
src/main/java/fr/uga/im2ag/m1info/
├── lab1/                          # Lab 1 - Java Sockets
│   ├── echo/                      # Echo server/client
│   ├── chat/                      # Basic chat with sockets
│   ├── calculator/                # RPC-style calculator
│   └── phoneRegistry/             # Phone registry server
│
├── lab2/                          # Lab 2 - Java RMI (exercises 4-6)
│   ├── hello/                     # Exercise 4 - HelloWorld RMI
│   ├── hello1/                    # Exercise 4 - Variant
│   ├── hello2/                    # Exercise 5 - Client identity (callback intro)
│   └── hello3/                    # Exercise 6 - Accounting & multiple services
│
├── tchatsapp_alt/                 # Lab 2 - Exercise 7: Chat App (our implementation)
│   ├── common/
│   │   ├── IChatServer.java       # Server remote interface
│   │   └── IChatClient.java       # Client callback remote interface
│   ├── server/
│   │   ├── ChatServerMain.java    # Entry point (creates registry + binds)
│   │   └── ChatServerImpl.java    # Broadcast, history, persistence
│   └── client/
│       ├── ChatClientMain.java    # Entry point (stdin loop)
│       └── ChatClientImpl.java    # RMI callback (prints messages)
│
├── tchatsapp/                     # Lab 2 - Exercise 7: Chat App (implementation from semester 1)
│   │                              # Full-featured port of the COO project to RMI
│   ├── common/rmi/                # RMI interfaces (ProtocolMessage-based)
│   ├── server/                    # Server with handler chain, routing, user management
│   ├── client/                    # Client with event bus, command pattern, media
│   └── gui/                       # JavaFX GUI
│
└── rabbbitmq/                     # Lab 3 - RabbitMQ (Python)
    ├── docker-compose.yaml        # RabbitMQ broker via Docker
    ├── hello_world/               # Exercise: basic send/receive
    ├── work_queues/               # Exercise: task queues (new_task / worker)
    └── publish_subscrive/         # Exercise: pub/sub (emit_log / rec_logs)
```

---

## Lab 2 - Exercise 7: Chat Application (`tchatsapp_alt`)

An RMI-based chat system built from scratch, implemented in three stages.

### Stage a) Basic chat
Participants can dynamically join, leave, and exchange messages.
The server broadcasts messages to all connected clients via RMI callbacks.

### Stage b) Message history
The server stores all messages in memory. Clients can request the full
history with the `/history` command.

### Stage c) Persistent history
Message history is serialized to `chat_history.dat`. If the server restarts,
previous messages are restored.

### Prerequisites
- Java 21
- Maven

### Build & Run

```bash
# Compile
./chatapp_launcher.sh compile

# Terminal 1 - Start the server
./chatapp_launcher.sh server

# Terminal 2 - Join as Alice
./chatapp_launcher.sh client Alice

# Terminal 3 - Join as Bob
./chatapp_launcher.sh client Bob
```

### Cross-network testing (different machines on same LAN)

```bash
# On the server machine (IP is printed on startup)
./chatapp_launcher.sh server
# => Starting ChatServer on 192.168.x.x ...

# On the client machine
HOST=192.168.x.x ./chatapp_launcher.sh client Alice

# Override server IP manually if needed
SERVER_IP=192.168.x.x ./chatapp_launcher.sh server
```

### Client Commands

| Command      | Description                        |
|--------------|------------------------------------|
| *(any text)* | Send a message to all participants |
| `/history`   | Display all past messages          |
| `/quit`      | Leave the chat cleanly             |

### Testing Persistence (Stage c)

1. Start the server and send some messages
2. Stop the server with `Ctrl+C`
3. Restart: `./chatapp_launcher.sh server`
4. Connect a client and type `/history` — previous messages are restored

---

## Lab 3 - RabbitMQ (`rabbbitmq`)

RabbitMQ exercises implemented in Python. A Docker Compose file is provided
to run the broker without a local installation.

### Start the broker

```bash
cd src/main/java/fr/uga/im2ag/m1info/rabbbitmq
docker compose up -d
```

Broker runs on `localhost:5672` (AMQP) and `localhost:15672` (management UI).
Credentials: `admin` / `admin`.

### hello_world

```bash
# Receiver (start first)
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.hello_world.Recv 

# Sender
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.hello_world.Send
```

### work_queues

```bash
# Start one or more workers
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.work_queues.Worker 

# Send tasks
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.work_queues.NewTask -Dexec.args="Hello..."
```

Note:  The amount of dots " . " represent the amount of seconds for the worker.

### publish_subscribe

```bash
# Start one or more subscribers
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.publish_subscribe.RecLogs 

# Publish a log
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.publish_subscribe.EmitLog 
```


### ping_pong

```bash
# Start node 1
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.ping_pong.Node1

# Start ndoe 2 
mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.rabbbitmq.ping_pong.Node2

```

#### Note:
The ping-pong algorith we did follow the 3 core rules from the lecture.

Does it avoid the lecture's issues ?
YES but, by simplification not by solving them.
* Issue 1 (slide 18): Pushing the button more than once creates multiple parallel ping-pong streams.

Our Node1 has no interactive button, it pushes exactly once at startup. Problem sidestepped.
* Issue 2 (slide 20): Both nodes pushing simultaneously creates two crossing streams.

Our Node2 has no PUSH button at all it's a pure reactor.

### Stop the broker

```bash
docker compose down
```
