# SmartSchedulerApp – Final Project Part 2

A modular Java system for assigning tasks to team members using interchangeable strategies. Supports both SQLite and file-based storage, includes full Create, Read, Update, Delete (CRUD) support, follows layered architecture, and is equipped for future web expansion.

---

## 🧱 Architecture Overview

```plaintext
[ AlgorithmModule.jar ]     → includes logic and models
          ↓
[ TaskAssignmentService ]   → controls flow
          ↓
[ IDao<T> (File / SQLite) ] → handles persistence
          ↓
[ DataSource.txt | DataSource.db ]
```

---

## ✅ Features Implemented

- [x] Strategy pattern with `ITaskAssignment`
- [x] Two strategies: `GreedyEarliestFit`, `BalancedLoad`
- [x] File-based DAO using ObjectOutputStream (Java serialization)
- [x] SQLite DAO using JDBC (`DataSource.db`)
- [x] Full CRUD support: `load`, `save`, `deleteById`, `deleteAll`
- [x] Hybrid-ready DAO layer
- [x] Professional JUnit tests
- [x] Modular design: strategy module packaged as `AlgorithmModule.jar`
- [x] Clean separation: DAO / Service / Logic
- [x] Future-ready for Web API layer

---

## 🔀 Strategies

| Strategy           | Description                                         |
|--------------------|-----------------------------------------------------|
| `GreedyEarliestFit` | Assign task to the first available matching member |
| `BalancedLoad`      | Distribute tasks to balance member load            |

---

## 💾 Persistence Options

### 1. SQLite
- Stored in: `src/main/resources/DataSource.db`
- Tables: `tasks`, `members`, `assignments`
- Handled by: `SQLiteDaoImpl<T>`

### 2. File-Based Storage
- Stores each entity in a separate `.txt` file using Java's built-in serialization (`ObjectOutputStream` / `ObjectInputStream`)
- Files:
    - `tasks.txt` → List<Task>
    - `members.txt` → List<TeamMember>
    - `assignments.txt` → List<Assignment>
- Handled by: `FileDaoImpl<T>` (binary format)
- Requires: all model classes implement `Serializable`

---

## 🧪 Test Coverage

| Test Class                        | Purpose                               |
|----------------------------------|----------------------------------------|
| `TestServiceTaskAssignmentFull`  | Greedy + File full CRUD validation     |
| `TestServiceTaskAssignmentSQLiteFull` | Greedy + SQLite full flow         |

All test classes use real data and clean up after themselves.   
*The tests covered Greedy strategy but applied to Balanced as well.

---

## 📄 Example Output (Greedy + SQLite)

```
✅ [SQLite] Greedy strategy test passed with 3 assignments.
Task t1 → Member m1
Task t2 → Member m2
```

---

## 📚 Technologies Used

- Java 17+
- SQLite + JDBC (Xerial driver)
- Jackson (databind + jsr310)
- JUnit 4
- IntelliJ IDEA

---

## 📦 Project Structure

```plaintext
SmartSchedulerApp/
├── src/
│   ├── main/java/com/hit/dao/
│   ├── main/java/com/hit/service/
│   ├── main/java/com/hit/model/
│   ├── main/resources/assignments.txt
│   ├── main/resources/members.txt
│   ├── main/resources/tasks.txt
│   ├── main/resources/DataSource.db
│   └── test/java/hit/service/
├── lib/
│   └── jackson-databind, jsr310...
├── AlgorithmModule.jar
```

---

## 👤 Author

**Yehonatan Ravoch**  
B.Sc. Computer Science, Holon Institute of Technology  
Advanced Programming – Final Project Part 2  
May 2025
