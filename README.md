# ILP CW1 - Geographic REST API Service

A Spring Boot REST API service that provides geographic calculations and geometric predicates for location-based operations.

##  Overview

This project implements a RESTful web service for performing geographic computations including:
- Euclidean distance calculations between coordinates
- Proximity detection between positions
- Next position calculation based on angle and distance
- Point-in-polygon detection using ray casting algorithm

**Tech Stack:**
- Java 21
- Spring Boot 3.4.3
- Maven 3.9.9
- Lombok
- Jakarta Validation
- Docker

---

## Architecture Design

### Layered Architecture

The project follows a clean **layered architecture** pattern with clear separation of concerns:

```
┌─────────────────────────────────────┐
│      Controller Layer               │  ← HTTP Request/Response Handling
├─────────────────────────────────────┤
│      Service Layer (Interface)      │  ← Business Logic & Algorithms
├─────────────────────────────────────┤
│      Repository Layer               │  ← Data Access (Future: Persistence)
├─────────────────────────────────────┤
│      DTO Layer                       │  ← Data Transfer & Domain Models
├─────────────────────────────────────┤
│      Entity Layer                    │  ← Database Entities (Reserved)
└─────────────────────────────────────┘
```

### Package Structure Philosophy

**Why DTO Contains Domain Models?**

In this stateless REST API, we've adopted a **pragmatic approach**:
- `LngLat` and `Region` serve as both **Value Objects** (domain concepts) and **DTOs** (data transfer)
- They contain validation logic and are used across multiple layers
- No ORM mapping needed = no separate entity layer required
- Reduces unnecessary abstraction while maintaining clean separation

**When to Use `entity` Package:**
- Reserved for future database persistence with JPA/Hibernate
- Will contain `@Entity` annotated classes when persistence is added
- Currently empty as the API is stateless

---

## layer Responsibilities

### 1. Controller Layer (`controller/`)

**Example Controller Method:**
```java
@PostMapping("/distanceTo")
public ResponseEntity<Double> distanceTo(@RequestBody TwoPositionsRequest request) {
    // Validation
    if (request == null || !request.getPosition1().isValid()) {
        return ResponseEntity.badRequest().build();
    }
    // Delegate to service
    double distance = geoService.calculateDistance(
        request.getPosition1(), 
        request.getPosition2()
    );
    return ResponseEntity.ok(distance);
}
```

---

### 2. Service Layer (`service/` & `service/impl/`)

**Interface Design Principles:**
- Define clear business contracts
- Support testability (mockable interfaces)
- Allow multiple implementations

**Implementation Responsibilities:**
- ✅ Core business logic
- ✅ Complex calculations and algorithms
- ✅ Data transformation
- ✅ Orchestrate calls to repositories (when persistence added)
- ✅ Transaction management (future enhancement)

**Key Algorithms Implemented:**

#### 1️⃣ Euclidean Distance Calculation
```java
distance = √((lng1 - lng2)² + (lat1 - lat2)²)
```

#### 2️⃣ Proximity Check
```java
isClose = distance < 0.00015
```
Two positions are considered "close" if their Euclidean distance is less than 0.00015 degrees.

#### 3️⃣ Next Position Calculation
```java
newLng = startLng + MOVE_DISTANCE * cos(angle)
newLat = startLat + MOVE_DISTANCE * sin(angle)
MOVE_DISTANCE = 0.00015
```
- Angle must be a multiple of 22.5° (0°, 22.5°, 45°, ..., 337.5°)
- Movement distance is fixed at 0.00015 degrees
- Angle 999 represents hovering (no movement)

#### 4️⃣ Point-in-Polygon Detection (Ray Casting Algorithm)
- Cast a horizontal ray from the test point to infinity (rightward)
- Count intersections with polygon edges
- **Odd number of intersections** → Point is **inside**
- **Even number of intersections** → Point is **outside**
- Points on the boundary are considered **inside**

---

### 3. Repository Layer (`repository/`)

**Current Status:** Infrastructure prepared for future persistence

**Responsibilities (when implemented):**
- Data access operations (CRUD)
- Query execution
- Data mapping between entities and domain models
- Database transaction coordination

**Future Enhancement:** Will integrate with Spring Data JPA or custom DAO implementations.

---

### 4. DTO Layer (`dto/`)

**Contains Two Types of Classes:**

#### A. Request/Response DTOs
- `TwoPositionsRequest` - For distance and proximity endpoints
- `NextPositionRequest` - For next position calculation
- `IsInRegionRequest` - For region containment check
- `Result` - Generic result wrapper

#### B. Domain Models (Value Objects)
- `LngLat` - Geographic coordinate (longitude, latitude)
- `Region` - Polygon region with vertices

**Design Principles:**
- Use Lombok annotations: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Use Jakarta Validation annotations: `@NotNull`, `@Valid`
- Include business validation methods (e.g., `isValid()`, `isClosed()`)
- Immutable where possible (Value Object pattern)

**Example DTO:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoPositionsRequest {
    @NotNull
    private LngLat position1;
    
    @NotNull
    private LngLat position2;
}
```

**Example Domain Model:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class L-colorLat {
    @NotNull
    @JsonProperty("lng")
    private Double lng;
    
    @NotNull
    @JsonProperty("lat")
    private Double lat;
    
    @JsonIgnore
    public boolean isValid() {
        return lng != null && lat != null && 
               lng >= -180 && lng <= 180 &&
               lat >= -90 && lat <= 90;
    }
}
```

---

### 5. Entity Layer (`entity/`)

**Current Status:** Reserved for future use

**Will Contain:**
- JPA/Hibernate annotated entity classes
- `@Entity`, `@Table`, `@Id`, `@Column` annotations
- Relationships: `@OneToMany`, `@ManyToOne`, etc.

**Example (Future):**
```java
@Entity
@Table(name = "positions")
public class PositionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Embedded
    private LngLat coordinates;
    
    private LocalDateTime timestamp;
}
```

---

### 6. Configuration Layer (`configuration/`)

**Contains:**
- `IlpRestServiceConfig` - Bean definitions and REST service URL configuration
- Application-wide configuration beans
- External service clients setup

---

### 7. Data Layer (`data/`)

**Contains:**
- `RuntimeEnvironment` - Runtime configuration and environment variables
- Static data or constants used across the application

---

## SOLID Principles Applied

### Single Responsibility Principle (SRP)
- **Controller**: Only handles HTTP concerns
- **Service**: Only implements business logic
- **DTO**: Only transfers data

### Open/Closed Principle (OCP)
- Use interfaces to allow extension without modification
- New implementations can be added without changing existing code

### Liskov Substitution Principle (LSP)
- Any `GeoService` implementation can replace another

### Interface Segregation Principle (ISP)
- Interfaces contain only necessary methods
- No forced implementation of unused methods

### Dependency Inversion Principle (DIP)
```java
// Depend on abstraction
private final GeoService geoService;

// Depend on implementation
private final GeoServiceImpl geoService;
```

---

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

### 1. Get Welcome Page
```http
GET http://localhost:8080/api/v1/
```

**Using curl:**
```cmd
curl http://localhost:8080/api/v1/
```

Returns an HTML welcome page with service information.

---

### 2. Get Student UID
```http
GET http://localhost:8080/api/v1/uid
```

**Using curl:**
```cmd
curl http://localhost:8080/api/v1/uid
```

**Response:** `s2564099`

---

### 3. Calculate Distance
```http
POST http://localhost:8080/api/v1/distanceTo
Content-Type: application/json

{
  "position1": {"lng": -3.186874, "lat": 55.944494},
  "position2": {"lng": -3.192473, "lat": 55.946233}
}
```

**Using curl:**
```cmd
curl -X POST http://localhost:8080/api/v1/distanceTo ^
  -H "Content-Type: application/json" ^
  -d "{\"position1\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"position2\": {\"lng\": -3.192473, \"lat\": 55.946233}}"
```

**Response:** `0.006489` (Euclidean distance)

---

### 4. Check Proximity
```http
POST http://localhost:8080/api/v1/closeTo
Content-Type: application/json

{
  "position1": {"lng": -3.186874, "lat": 55.944494},
  "position2": {"lng": -3.186900, "lat": 55.944500}
}
```

**Using curl:**
```cmd
curl -X POST http://localhost:8080/api/v1/closeTo ^
  -H "Content-Type: application/json" ^
  -d "{\"position1\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"position2\": {\"lng\": -3.186900, \"lat\": 55.944500}}"
```

**Response:** `true` or `false`

---

### 5. Calculate Next Position
```http
POST http://localhost:8080/api/v1/nextPosition
Content-Type: application/json

{
  "start": {"lng": -3.186874, "lat": 55.944494},
  "angle": 90.0
}
```

**Using curl:**
```cmd
curl -X POST http://localhost:8080/api/v1/nextPosition ^
  -H "Content-Type: application/json" ^
  -d "{\"start\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"angle\": 90.0}"
```

**Response:**
```json
{"lng": -3.186724, "lat": 55.944494}
```

---

### 6. Check Point in Region
```http
POST http://localhost:8080/api/v1/isInRegion
Content-Type: application/json

{
  "position": {"lng": -3.186874, "lat": 55.944494},
  "region": {
    "name": "central-area",
    "vertices": [
      {"lng": -3.192473, "lat": 55.946233},
      {"lng": -3.184319, "lat": 55.946233},
      {"lng": -3.184319, "lat": 55.942617},
      {"lng": -3.192473, "lat": 55.942617},
      {"lng": -3.192473, "lat": 55.946233}
    ]
  }
}
```

**Using curl:**
```cmd
curl -X POST http://localhost:8080/api/v1/isInRegion ^
  -H "Content-Type: application/json" ^
  -d "{\"position\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"region\": {\"name\": \"central-area\", \"vertices\": [{\"lng\": -3.192473, \"lat\": 55.946233}, {\"lng\": -3.184319, \"lat\": 55.946233}, {\"lng\": -3.184319, \"lat\": 55.942617}, {\"lng\": -3.192473, \"lat\": 55.942617}, {\"lng\": -3.192473, \"lat\": 55.946233}]}}"
```

**Response:** `true` or `false`

---

### Quick Test - All Endpoints

You can test all endpoints sequentially with these commands:

```cmd
REM 1. Test welcome page
curl http://localhost:8080/api/v1/

REM 2. Test UID
curl http://localhost:8080/api/v1/uid

REM 3. Test distance calculation
curl -X POST http://localhost:8080/api/v1/distanceTo -H "Content-Type: application/json" -d "{\"position1\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"position2\": {\"lng\": -3.192473, \"lat\": 55.946233}}"

REM 4. Test proximity check
curl -X POST http://localhost:8080/api/v1/isCloseTo -H "Content-Type: application/json" -d "{\"position1\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"position2\": {\"lng\": -3.186900, \"lat\": 55.944500}}"

REM 5. Test next position
curl -X POST http://localhost:8080/api/v1/nextPosition -H "Content-Type: application/json" -d "{\"start\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"angle\": 90.0}"

REM 6. Test point in region
curl -X POST http://localhost:8080/api/v1/isInRegion -H "Content-Type: application/json" -d "{\"position\": {\"lng\": -3.186874, \"lat\": 55.944494}, \"region\": {\"name\": \"central-area\", \"vertices\": [{\"lng\": -3.192473, \"lat\": 55.946233}, {\"lng\": -3.184319, \"lat\": 55.946233}, {\"lng\": -3.184319, \"lat\": 55.942617}, {\"lng\": -3.192473, \"lat\": 55.942617}, {\"lng\": -3.192473, \"lat\": 55.946233}]}}"
```

---

## Build & Run

### Prerequisites
- Java 21 or higher
- Maven 3.9.9 or higher
- Docker (optional, for containerized deployment)

### Using Maven (Windows)

**Build the project:**
```cmd
mvnw.cmd clean package
```

**Run the application:**
```cmd
mvnw.cmd spring-boot:run
```

Or run the JAR directly:
```cmd
java -jar target\IlpTutorial1-0.0.1-SNAPSHOT.jar
```

The service will start on `http://localhost:8080`

### Using Docker

**Build the Docker image:**
```cmd
docker build -t ilp-cw1 .
```

**Run the container:**
```cmd
docker run -p 8080:8080 ilp-cw1
```

**With custom service URL environment variable:**
```cmd
docker run -p 8080:8080 -e ILP_SERVICE_URL=https://your-service-url.com ilp-cw1
```

---

## Testing

### Run All Tests
```cmd
mvnw.cmd test
```

### Test Coverage
The project includes:
- **Unit Tests**: Service layer algorithm testing (`GeoServiceUnitTest`)
- **Integration Tests**: Full application context testing (`ApplicationIntegrationTest`, `ApiIntegrationTest`)
- **Web Layer Tests**: Controller endpoint testing (`GeoControllerWebTest`)

### Writing Tests
- **Unit Tests**: Focus on testing Service implementations and algorithms
- **Controller Tests**: Use MockMvc or WebTestClient
- **Integration Tests**: Test the full request/response cycle

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/uk/ac/ed/acp/cw2/
│   │   ├── Application.java                    # Main application entry point
│   │   ├── configuration/
│   │   │   └── IlpRestServiceConfig.java       # Configuration beans
│   │   ├── controller/
│   │   │   └── GeoController.java              # REST API endpoints
│   │   ├── service/
│   │   │   ├── GeoService.java                 # Service interface
│   │   │   └── impl/
│   │   │       └── GeoServiceImpl.java         # Business logic & algorithms
│   │   ├── repository/
│   │   │   └── SimpleRepository.java           # Data access layer (prepared)
│   │   ├── dto/
│   │   │   ├── TwoPositionsRequest.java        # Request DTOs
│   │   │   ├── NextPositionRequest.java
│   │   │   ├── IsInRegionRequest.java
│   │   │   ├── Result.java                     # Response wrapper
│   │   │   ├── LngLat.java                     # Domain model: Coordinate
│   │   │   └── Region.java                     # Domain model: Polygon region
│   │   ├── entity/                             # Reserved for JPA entities
│   │   └── data/
│   │       └── RuntimeEnvironment.java         # Environment configuration
│   └── resources/
│       └── application.yml                      # Application configuration
└── test/
    └── java/uk/ac/ed/acp/cw2/
        ├── GeoServiceUnitTest.java              # Service layer tests
        ├── GeoControllerWebTest.java            # Controller tests
        ├── ApiIntegrationTest.java              # API integration tests
        └── ApplicationIntegrationTest.java      # Full app context tests
```

### Architecture Highlights

✅ **Clean Separation**: Each package has a single, clear responsibility  
✅ **Testability**: Interface-based design allows easy mocking  
✅ **Extensibility**: Repository and entity layers ready for persistence  
✅ **Pragmatic**: Combined DTOs and domain models to avoid over-engineering  
✅ **SOLID Principles**: Dependency inversion, single responsibility throughout  

---

## 📝 Request Flow

1. **Request Arrival** → `@PostMapping` in `GeoController` receives HTTP request
2. **JSON Deserialization** → Spring automatically converts JSON to DTO
3. **Input Validation** → Jakarta Validation (`@Valid`, `@NotNull`) triggers
4. **Business Processing** → Controller delegates to `GeoService` interface
5. **Algorithm Execution** → `GeoServiceImpl` performs calculations
6. **Response Generation** → `ResponseEntity.ok(result)` returns JSON response
7. **Exception Handling** → `GlobalExceptionHandler` catches and formats errors

```
Client Request
     ↓
[Controller Layer] ← Validates input, delegates
     ↓
[Service Layer] ← Executes business logic
     ↓
[Repository Layer] ← (Future: Data access)
     ↓
[Entity Layer] ← (Future: Database)
```

---

## 🤝 Contributing

When contributing to this project:

1. **Follow the layered architecture** - Respect package boundaries
2. **Keep controllers thin** - Delegate to services
3. **Add unit tests** for new algorithms or service logic
4. **DTOs should be simple** - Validation-driven with minimal logic
5. **Document complex algorithms** - Add comments for maintainability
6. **Follow Java naming conventions** - Use standard code style
7. **Repository/Entity layers** - Plan for future persistence needs

### Architecture Decision Records

**Why combine domain models with DTOs?**
- Stateless API doesn't require separate domain layer
- `LngLat` and `Region` are immutable value objects
- Reduces boilerplate without sacrificing clarity
- Easy to refactor when persistence is added

**When to refactor?**
- When adding database persistence, create true `@Entity` classes
- Move business logic from DTOs to domain services
- Implement repository interfaces with Spring Data JPA

---

## 📄 License

This project is part of the Informatics Large Practical coursework at the University of Edinburgh.

---

## 👤 Author

**Student ID:** s2564099  
**Course:** Informatics Large Practical - Coursework 1  
**Institution:** University of Edinburgh - Year 3  
**Academic Year:** 2024/2025
