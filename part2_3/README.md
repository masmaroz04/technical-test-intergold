# Part 2: Order Validation Module

## Requirements
- Java 11 or higher
- Maven 3.6 or higher (for Maven build)

## How to Run

### Option 1: Plain Java
```bash
javac src/main/java/*.java -d out
java -cp out Main
```

### Option 2: Maven
```bash
# Windows (PowerShell)
mvn compile; mvn exec:java "-Dexec.mainClass=Main"

# Mac / Linux
mvn compile && mvn exec:java -Dexec.mainClass=Main
```

## How to Run Unit Tests
```bash
mvn test
```
