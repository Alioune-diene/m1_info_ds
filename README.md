# Introduction to Distributed Systems - Lab 2: Java RMI

M1 Informatique - Universite Grenoble Alpes

## Repository Structure

```
src/main/java/fr/uga/im2ag/m1info/
├── lab2/
│   ├── hello/       # Exercise 4 - HelloWorld RMI example
│   ├── hello1/      # Exercise 4 - Variant
│   ├── hello2/      # Exercise 5 - Modify Hello (client identity)
│   └── hello3/      # Exercise 6 - Continue Hello (accounting, registry)
│
└── tchatsapp/       # Exercise 7 - The Chat Application
    ├── common/      # Shared RMI interfaces and legacy protocol classes
    │   ├── IChatServer.java      # Server remote interface
    │   ├── IChatClient.java      # Client callback remote interface
    │   ├── messagefactory/       # (legacy) Protocol messages from COO project
    │   ├── repository/           # (legacy) Generic CRUD repository pattern
    │   └── model/                # (legacy) GroupInfo model
    ├── server/
    │   ├── ChatServerMain.java   # Server entry point (creates registry + binds)
    │   └── ChatServerImpl.java   # Server implementation (broadcast, history, persistence)
    ├── client/
    │   ├── ChatClientMain.java   # Client entry point (stdin loop)
    │   └── ChatClientImpl.java   # Client callback implementation (prints messages)
    ├── crypto/      # (legacy) E2E encryption from COO project
    └── storage/     # (legacy) Encrypted key storage from COO project
```

The `common/`, `crypto/`, and `storage/` packages were imported from a previous
COO semester project (TchatsApp - a full chat application with NIO sockets and E2E
encryption). For this RMI lab, only the core RMI classes are used; the legacy
packages are kept for reference.

## Exercise 7 - The Chat Application

An RMI-based chat system implemented in four stages:

### Stage a) Basic chat
Participants can dynamically join, leave, and exchange messages.
The server broadcasts messages to all connected clients via RMI callbacks.

### Stage b) Message history
The server stores all messages in memory. Clients can request the full
history with the `/history` command.

### Stage c) Persistent history
Message history is serialized to `chat_history.dat`. If the server restarts,
previous messages are restored.

## How to Test the Chat Application

### Prerequisites
- Java 21
- Maven

### Build

```bash
./chatapp_launcher.sh compile
```

### Run

Open 3 terminals:

**Terminal 1 - Start the server:**
```bash
./chatapp_launcher.sh server
```

**Terminal 2 - Join as Alice:**
```bash
./chatapp_launcher.sh client Alice
```

**Terminal 3 - Join as Bob:**
```bash
./chatapp_launcher.sh client Bob
```

### Client Commands

| Command     | Description                           |
|-------------|---------------------------------------|
| *(text)*    | Send a message to all participants    |
| `/history`  | Display all past messages             |
| `/quit`     | Leave the chat cleanly                |

### Testing Persistence (Stage c)

1. Start the server and send some messages
2. Stop the server with `Ctrl+C`
3. Restart the server: `./chatapp_launcher.sh server`
4. Connect a client and type `/history` to see the recovered messages

### Architecture

The chat uses the **RMI callback pattern**:
- `IChatServer` is registered in the RMI registry by the server
- Clients look up `IChatServer` and call `join(username, clientRef)`
- The client passes itself (`IChatClient`) as a remote object
- The server calls `receiveMessage()` on each client to broadcast messages
- No separate `rmiregistry` process needed (created in `ChatServerMain`)
