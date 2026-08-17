# Current system sequence

Wire format on every TCP stream: `[length][type][data]`. Registration uses a short connection to the broker on `127.0.0.1:1234`. After that, the broker dials the client’s listen port and keeps one dedicated TCP stream.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant P as Producer
    participant B as Broker :1234
    participant T as Topic + Queue
    participant CG as ConsumerGroup
    participant C as Consumer

    Note over B: startBrokerServer()<br/>accept loop on BROKER_PORT

    rect rgb(240, 248, 255)
        Note over P,B: Producer registration
        P->>B: P_REG (port, topicId)
        B->>T: getOrCreateTopic(topicId)
        Note over T: if new topic: spawn stopAndPop thread (every 50s)
        B-->>B: spawn dedicated-channel thread
        B-->>P: R_P_REG
        Note over P,B: registration socket closed
        B->>P: TCP connect to producerPort
        P->>P: accept() dedicated channel
    end

    rect rgb(245, 255, 245)
        Note over C,B: Consumer registration
        C->>B: C_REG (port, topicId, groupId)
        B->>T: getOrCreateTopic(topicId)
        B->>CG: getOrCreateConsumerGroup(groupId)
        B-->>B: startConsumerGroupConsumption() thread
        B->>C: TCP connect to consumerPort
        C->>C: accept() dedicated channel
        B->>CG: addConsumer(socket)
        B-->>C: R_C_REG
        Note over C,B: registration socket closed
    end

    rect rgb(255, 250, 240)
        Note over User,T: Produce on dedicated channel
        User->>P: stdin line
        P->>B: P_CM (payload)
        B->>T: Queue.push(payload)
        B-->>P: R_P_CM
    end

    rect rgb(255, 245, 245)
        Note over T,C: Consume loop (one thread per group)
        loop while true
            B->>T: Queue.peekAt(group offset)
            alt no message
                B-->>B: continue (busy wait)
            else message at offset
                B->>CG: lock
                B->>C: P_CM (payload) if consumer.isAvailable
                Note over C: sleep 5s (simulates processing)
                C-->>B: R_P_CM
                B->>CG: isAvailable = true<br/>incOffset()
                B->>CG: unlock
            end
        end
    end

    rect rgb(248, 248, 248)
        Note over T,CG: Retention (stopAndPop, every 50s)
        B->>T: lock topic
        B->>CG: minOffset across groups
        B->>T: pop minOffset messages from head
        B->>CG: subtract minOffset from each group offset
        B->>T: unlock topic
    end
```

## Message types used

| Type | Direction | When |
|------|-----------|------|
| `P_REG` / `R_P_REG` | Producer ↔ Broker (broker port) | Register producer listen port and topic |
| `C_REG` / `R_C_REG` | Consumer ↔ Broker (broker port) | Register consumer listen port, topic, group |
| `P_CM` / `R_P_CM` | Producer → Broker (dedicated) | Push payload into the topic queue |
| `P_CM` / `R_P_CM` | Broker → Consumer (dedicated) | Deliver peeked message; ack before offset advances |

## Threads on the broker

- Accept loop on `:1234` (registration only).
- One dedicated-channel thread per producer (read `P_CM`, push, ack).
- One consumption thread per consumer group (peek, send, wait for ack, increment offset).
- One `stopAndPop` thread per topic (trim messages already consumed by every group).
