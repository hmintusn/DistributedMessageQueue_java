A nested class is usually appropriate when the inner class is tightly coupled to the outer class in terms of meaning or usage.

Typical cases:

* implementation detail
* helper class
* small data structure
* logically scoped to the outer class
* not useful independently

Example:

```java
class ConsumerGroup {
    static class ConsumerConnection {
        Socket socket;
        boolean active;
    }
}
```

`ConsumerConnection` only makes sense within the context of a `ConsumerGroup`.

---

A useful rule of thumb:

> If the class name feels incomplete without the outer class context, nesting probably makes sense.

For example:

```text
Map.Entry
Thread.State
Parser.Token
ConsumerGroup.ConsumerConnection
```

`Entry`, `State`, `Token`, `ConsumerConnection` are all context-dependent names.

---

On the other hand, if the class starts becoming:

* reusable
* independently testable
* shared across modules
* behavior-heavy
* lifecycle-heavy

then it usually deserves its own file/class.

For example:

```java
class ConsumerConnection {
    reconnect();
    heartbeat();
    poll();
    authenticate();
}
```

At that point it is no longer just an implementation detail — it became a real subsystem/component.
