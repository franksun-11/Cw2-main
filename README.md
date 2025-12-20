# ILP CW2 - Drone-Based Medication Delivery Service

A comprehensive Spring Boot REST API service for managing autonomous drone-based medication delivery operations, including route optimization, availability checking, and GeoJSON path visualization.

## 🚁 Overview

This project implements a complete microservice for a drone delivery system that:

### **CW1 Foundation - Geographic Services:**
- Euclidean distance calculations between coordinates
- Proximity detection between positions
- Next position calculation based on angle and distance
- Point-in-polygon detection using ray casting algorithm

### **CW2 Extensions - Drone Delivery Management:**
- **Static Drone Queries**: Filter drones by capabilities (cooling, heating, capacity, cost)
- **Dynamic Drone Queries**: Multi-attribute queries with comparison operators (=, !=, <, >)
- **Availability Checking**: Date/time-based drone availability validation at service points
- **Path Optimization**: Calculate optimal delivery routes with multiple strategies
- **GeoJSON Export**: Convert flight paths to GeoJSON format for visualization
- **No-Fly Zone Avoidance**: Respect restricted areas during path calculation
- **Multi-Drone Coordination**: Assign multiple drones to minimize total delivery moves

**Tech Stack:**
- Java 21
- Spring Boot 3.4.3
- Maven 3.9.9
- Lombok
- Jakarta Validation
- Docker & Docker Compose
- RestTemplate for external API integration
- Jackson for JSON processing

---

## Architecture Design

### Layered Architecture

The project follows a clean **layered architecture** pattern with clear separation of concerns:

```
┌──────────────────────────────────────────────┐
│      Controller Layer                        │  ← HTTP Request/Response Handling
│  - GeoController (CW1)                       │
│  - DroneController (CW2)                     │
├──────────────────────────────────────────────┤
│      Service Layer (Interface)               │  ← Business Logic & Algorithms
│  - GeoService: Geographic calculations       │
│  - DroneQueryService: Drone operations       │
├──────────────────────────────────────────────┤
│      Configuration Layer                     │  ← External Service Integration
│  - IlpEndpointConfig: REST client setup      │
│  - Environment variable management           │
├──────────────────────────────────────────────┤
│      DTO Layer                                │  ← Data Transfer & Domain Models
│  - Request/Response DTOs                     │
│  - Domain Models (LngLat, Region, Drone)     │
├──────────────────────────────────────────────┤
│      Data Layer                               │  ← Runtime Configuration
│  - RuntimeEnvironment                        │
└──────────────────────────────────────────────┘
           ↕ RestTemplate
┌──────────────────────────────────────────────┐
│   External ILP REST Service (Azure)          │
│  - Drones data                               │
│  - Service points                            │
│  - Restricted areas                          │
│  - Drone availability schedules              │
└──────────────────────────────────────────────┘
```

### Key Architectural Features

✅ **Microservice Architecture**: Stateless REST API ready for containerization  
✅ **External Data Integration**: All operational data fetched from ILP REST service  
✅ **Environment-Driven Configuration**: ILP_ENDPOINT configurable via environment variable  
✅ **Multi-Strategy Optimization**: Three pathfinding strategies for optimal delivery  
✅ **Clean Separation**: Controllers handle HTTP, Services implement algorithms  
✅ **Dockerized Deployment**: Multi-stage build for production-ready containers  

---

## Layer Responsibilities

### 1. Controller Layer (`controller/`)

**GeoController (CW1):**
- Handles geographic computation endpoints
- Validates input coordinates and regions
- Returns calculated distances, positions, and containment checks

**DroneController (CW2):**
- Manages drone query endpoints (static and dynamic)
- Handles availability checking for dispatches
- Orchestrates delivery path calculation
- Converts paths to GeoJSON format

**Example Controller Method:**
```java
@PostMapping("/calcDeliveryPath")
public ResponseEntity<DeliveryPathResponse> calculateDeliveryPath(
        @RequestBody List<MedDispatchRec> dispatches) {
    
    DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);
    
    if (response == null) {
        // Return empty response with 200 OK (all requests valid)
        return ResponseEntity.ok(new DeliveryPathResponse());
    }
    
    return ResponseEntity.ok(response);
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
- ✅ External API integration (ILP REST service)
- ✅ Path optimization strategies

**Key Algorithms Implemented:**

#### CW1 - Geographic Algorithms

##### 1️⃣ Euclidean Distance Calculation
```java
distance = √((lng1 - lng2)² + (lat1 - lat2)²)
```

##### 2️⃣ Proximity Check
```java
isClose = distance < 0.00015
```
Two positions are considered "close" if their Euclidean distance is less than 0.00015 degrees.

##### 3️⃣ Next Position Calculation
```java
newLng = startLng + MOVE_DISTANCE * cos(angle)
newLat = startLat + MOVE_DISTANCE * sin(angle)
MOVE_DISTANCE = 0.00015
```
- Angle must be a multiple of 22.5° (0°, 22.5°, 45°, ..., 337.5°)
- Movement distance is fixed at 0.00015 degrees
- Angle 999 represents hovering (no movement)

##### 4️⃣ Point-in-Polygon Detection (Ray Casting Algorithm)
- Cast a horizontal ray from the test point to infinity (rightward)
- Count intersections with polygon edges
- **Odd number of intersections** → Point is **inside**
- **Even number of intersections** → Point is **outside**
- Points on the boundary are considered **inside**

#### CW2 - Drone Delivery Algorithms

##### 5️⃣ Dynamic Attribute Querying
Uses Java Reflection to query drone capabilities dynamically:
```java
// Support operators: =, !=, <, >
// Example: capacity > 8 AND cooling = true
```

##### 6️⃣ Availability Validation
Checks drone availability based on:
- Day of week (MONDAY-SUNDAY)
- Time ranges (from-until)
- Service point location
- Capacity and special requirements (cooling/heating)

##### 7️⃣ Path Optimization - Three Strategies

**Strategy 1: Single Drone Optimization**
- Find one drone that can handle all deliveries
- Minimize total moves by optimizing delivery order
- Most efficient for clustered deliveries

**Strategy 2: Nearest Service Point Assignment**
- Assign each delivery to its nearest service point
- Use multiple drones for geographically distributed deliveries
- Reduces total moves when deliveries are spread out

**Strategy 3: Multi-Drone Partitioning**
- Partition deliveries by requirements (cooling, heating, capacity)
- Handle conflicting requirements across multiple drones
- Fallback when single drone cannot satisfy all constraints

**Selection Criteria:**
1. Primary: Minimize total moves
2. Secondary: Minimize total cost (if moves are equal)

##### 8️⃣ Cost Calculation (Pro-Rata Distribution)
```java
totalFlightCost = (totalMoves × costPerMove) + costInitial + costFinal
costPerDelivery = totalFlightCost / numberOfDeliveries
```
- Fixed costs (initial + final) distributed equally across all deliveries
- Move costs shared proportionally
- Example: 3 deliveries in 1,200 moves → each carries 1/3 of total cost

##### 9️⃣ Pathfinding with Constraints
- Avoid restricted areas (no-fly zones)
- Respect move limits (maxMoves)
- Follow 22.5° angle increments
- Hover at delivery points (duplicate coordinates indicate delivery)
- Return to origin service point

##### 🔟 GeoJSON Conversion
Converts flight paths to GeoJSON LineString features:
```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": null,
      "geometry": {
        "type": "LineString",
        "coordinates": [[lng, lat], [lng, lat], ...]
      }
    }
  ]
}
```

---

### 3. Configuration Layer (`configuration/`)

**IlpEndpointConfig:**
- Manages external ILP REST service URL
- Reads from environment variable `ILP_ENDPOINT`
- Provides default fallback URL
- Configures RestTemplate bean for HTTP requests

**Environment Variable Support:**
```java
@Bean
public String ilpEndpoint() {
    return System.getenv().getOrDefault(
        "ILP_ENDPOINT",
        "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net"
    );
}
```

**Why Environment Variables?**
- Auto-marker can inject different test endpoints
- Development vs. production configuration
- No code changes needed for different environments
- Docker-friendly configuration

---

### 4. DTO Layer (`dto/`)

**Contains Two Types of Classes:**

#### A. Request/Response DTOs (CW1)
- `TwoPositionsRequest` - For distance and proximity endpoints
- `NextPositionRequest` - For next position calculation
- `IsInRegionRequest` - For region containment check
- `Result` - Generic result wrapper

#### B. Request/Response DTOs (CW2)
- `QueryCondition` - Dynamic query conditions
- `MedDispatchRec` - Medicine dispatch record
- `DeliveryPathResponse` - Delivery path with drone assignments
- `Drone` - Drone capabilities and metadata
- `ServicePoint` - Drone service point location
- `DroneServicePointAvailability` - Availability schedules
- `RestrictedArea` - No-fly zones

#### C. Domain Models (Value Objects)
- `LngLat` - Geographic coordinate (longitude, latitude)
- `Region` - Polygon region with vertices

**Design Principles:**
- Use Lombok annotations: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Use Jakarta Validation annotations: `@NotNull`, `@Valid`
- Use Jackson annotations: `@JsonProperty` for JSON mapping
- Include business validation methods (e.g., `isValid()`, `isClosed()`)
- Immutable where possible (Value Object pattern)

**Example DTO:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedDispatchRec {
    @JsonProperty("id")
    private Integer id;
    
    @JsonProperty("date")
    private LocalDate date;
    
    @JsonProperty("time")
    private LocalTime time;
    
    @JsonProperty("requirements")
    private Requirements requirements;
    
    @JsonProperty("delivery")
    private Delivery delivery;
}
```

---
- Cast a horizontal ray from the test point to infinity (rightward)
- Count intersections with polygon edges
- **Odd number of intersections** → Point is **inside**
- **Even number of intersections** → Point is **outside**
- Points on the boundary are considered **inside**

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
- Any `DroneQueryService` implementation can replace another

### Interface Segregation Principle (ISP)
- Interfaces contain only necessary methods
- No forced implementation of unused methods

### Dependency Inversion Principle (DIP)
```java
// ✅ Good: Depend on abstraction
private final GeoService geoService;

// ❌ Bad: Depend on implementation
private final GeoServiceImpl geoService;
```

---

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

### CW1 - Geographic Services

#### 1. Get Welcome Page
```http
GET http://localhost:8080/api/v1/
```

**Using curl:**
```cmd
curl http://localhost:8080/api/v1/
```

Returns an HTML welcome page with service information.

---

#### 2. Get Student UID
```http
GET http://localhost:8080/api/v1/uid
```

**Using curl:**
```cmd
curl http://localhost:8080/api/v1/uid
```

**Response:** `s2564099`

---

#### 3. Calculate Distance
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

#### 4. Check Proximity
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

#### 5. Calculate Next Position
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

#### 6. Check Point in Region
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

### CW2 - Drone Delivery Services

#### 7. Get Drones with Cooling
```http
GET http://localhost:8080/api/v1/dronesWithCooling/{state}
```

**Example:**
```cmd
curl http://localhost:8080/api/v1/dronesWithCooling/true
```

**Response:**
```json
[1, 5, 8, 9]
```

Returns array of drone IDs that have (true) or don't have (false) cooling capability.

---

#### 8. Get Drone Details by ID
```http
GET http://localhost:8080/api/v1/droneDetails/{id}
```

**Example:**
```cmd
curl http://localhost:8080/api/v1/droneDetails/4
```

**Response:**
```json
{
  "name": "Drone 4",
  "id": 4,
  "capability": {
    "cooling": false,
    "heating": true,
    "capacity": 8.0,
    "maxMoves": 1000,
    "costPerMove": 0.02,
    "costInitial": 1.4,
    "costFinal": 2.5
  }
}
```

**Note:** Returns 404 if drone ID not found (exception to 200-only rule).

---

#### 9. Query Drones by Single Attribute (Path Variable)
```http
GET http://localhost:8080/api/v1/queryAsPath/{attributeName}/{attributeValue}
```

**Example:**
```cmd
curl http://localhost:8080/api/v1/queryAsPath/capacity/8
```

**Response:**
```json
[2, 4, 7, 9]
```

Returns drone IDs where the attribute equals the specified value.

**Supported Attributes:**
- `capacity` - Drone cargo capacity
- `cooling` - true/false
- `heating` - true/false
- `maxMoves` - Maximum flight moves
- `costPerMove` - Cost per move

---

#### 10. Query Drones by Multiple Conditions (POST)
```http
POST http://localhost:8080/api/v1/query
Content-Type: application/json

[
  {
    "attribute": "capacity",
    "operator": ">",
    "value": "8"
  },
  {
    "attribute": "cooling",
    "operator": "=",
    "value": "true"
  }
]
```

**Using curl:**
```cmd
curl -X POST http://localhost:8080/api/v1/query ^
  -H "Content-Type: application/json" ^
  -d "[{\"attribute\":\"capacity\",\"operator\":\">\",\"value\":\"8\"},{\"attribute\":\"cooling\",\"operator\":\"=\",\"value\":\"true\"}]"
```

**Response:**
```json
[8]
```

**Supported Operators:**
- `=` - Equals (all types)
- `!=` - Not equals (numerical attributes)
- `<` - Less than (numerical attributes)
- `>` - Greater than (numerical attributes)

**Conditions are joined with AND** (all must be true).

---

#### 11. Query Available Drones for Dispatches
```http
POST http://localhost:8080/api/v1/queryAvailableDrones
Content-Type: application/json

[
  {
    "id": 123,
    "date": "2025-12-22",
    "time": "14:30",
    "requirements": {
      "capacity": 0.75,
      "cooling": false,
      "heating": true,
      "maxCost": 13.5
    },
    "delivery": {
      "lng": -3.186874,
      "lat": 55.944494
    }
  }
]
```

**Response:**
```json
[2, 4, 6, 7]
```

Returns drone IDs that can fulfill **ALL** dispatches in the array.

**Validation Checks:**
- ✅ Capacity sufficient
- ✅ Cooling/heating requirements met
- ✅ Availability at specified date/time
- ✅ Estimated moves within maxMoves limit
- ✅ Estimated cost within maxCost (if specified)

**AND Condition:** Only drones that can handle **every single dispatch** are returned.

---

#### 12. Calculate Delivery Path
```http
POST http://localhost:8080/api/v1/calcDeliveryPath
Content-Type: application/json

[
  {
    "id": 123,
    "date": "2025-12-22",
    "time": "14:30",
    "requirements": {
      "capacity": 0.75,
      "cooling": false,
      "heating": true
    },
    "delivery": {
      "lng": -3.186874,
      "lat": 55.944494
    }
  },
  {
    "id": 124,
    "date": "2025-12-22",
    "time": "15:00",
    "requirements": {
      "capacity": 1.2,
      "heating": true
    },
    "delivery": {
      "lng": -3.189543,
      "lat": 55.945523
    }
  }
]
```

**Response:**
```json
{
  "totalCost": 45.67,
  "totalMoves": 1234,
  "dronePaths": [
    {
      "droneId": 4,
      "deliveries": [
        {
          "deliveryId": 123,
          "flightPath": [
            {"lng": -3.1863580788986368, "lat": 55.94468066708487},
            {"lng": -3.186359, "lat": 55.94468066708487},
            {"lng": -3.186874, "lat": 55.944494},
            {"lng": -3.186874, "lat": 55.944494}
          ]
        },
        {
          "deliveryId": 124,
          "flightPath": [
            {"lng": -3.186874, "lat": 55.944494},
            {"lng": -3.189543, "lat": 55.945523},
            {"lng": -3.189543, "lat": 55.945523},
            {"lng": -3.1863580788986368, "lat": 55.94468066708487}
          ]
        }
      ]
    }
  ]
}
```

**Response Structure:**
- `totalCost`: Total cost for all deliveries (pro-rata distributed)
- `totalMoves`: Total number of moves across all drones
- `dronePaths`: Array of drone delivery sequences

**Flight Path Rules:**
- Start at service point
- Duplicate coordinates = hover (delivery made)
- Return to same service point
- Respect no-fly zones
- Follow 22.5° angle increments
- Stay within maxMoves limit

**Optimization Strategies:**
1. Single drone optimization (best for clustered deliveries)
2. Nearest service point assignment (best for distributed deliveries)
3. Multi-drone partitioning (handles conflicting requirements)

**Selection:** Minimizes moves first, then cost.

---

#### 13. Calculate Delivery Path as GeoJSON
```http
POST http://localhost:8080/api/v1/calcDeliveryPathAsGeoJson
Content-Type: application/json

[
  {
    "id": 123,
    "date": "2025-12-22",
    "time": "14:30",
    "requirements": {
      "capacity": 0.75,
      "heating": true
    },
    "delivery": {
      "lng": -3.186874,
      "lat": 55.944494
    }
  }
]
```

**Response:**
```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": null,
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [-3.1863580788986368, 55.94468066708487],
          [-3.186359, 55.94468066708487],
          [-3.186874, 55.944494],
          [-3.186874, 55.944494],
          [-3.1863580788986368, 55.94468066708487]
        ]
      }
    }
  ]
}
```

**GeoJSON Format:**
- Each delivery becomes a separate LineString feature
- Can be visualized at https://geojson.io
- Multiple trips = multiple LineString features
- Properties set to null (automarker only checks paths)
- No labels or markers (just the flight paths)

---

## Build & Run

### Prerequisites
- Java 21 or higher
- Maven 3.9.9 or higher
- Docker (for containerized deployment)

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
java -jar target\ilp_submission_image.jar
```

The service will start on `http://localhost:8080`

### Using Docker

**Build the Docker image:**
```cmd
docker build -t ilp-submission-2 .
```

**Save Docker image to TAR file (for submission):**
```cmd
docker save ilp-submission-2 -o ilp_submission_image.tar
```

**Load Docker image from TAR file:**
```cmd
docker load -i ilp_submission_image.tar
```

**Run the container:**
```cmd
docker run -p 8080:8080 ilp-submission-2
```

**With custom ILP endpoint environment variable:**
```cmd
docker run -p 8080:8080 -e ILP_ENDPOINT=https://your-ilp-service-url.com ilp-submission-2
```

**Test the service:**
```cmd
curl http://localhost:8080/api/v1/uid
```

### Docker Image Management

**List Docker images:**
```cmd
docker images
```

**Remove a Docker image:**
```cmd
docker rmi ilp-submission-2
```

**Stop running container:**
```cmd
docker ps
docker stop <container_id>
```

---

## Environment Variables

### ILP_ENDPOINT

The service connects to an external ILP REST API for drone, service point, and restricted area data.

**Default URL:**
```
https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net
```

**Override via environment variable:**
```cmd
REM Windows CMD
set ILP_ENDPOINT=https://your-custom-endpoint.com
mvnw.cmd spring-boot:run

REM Docker
docker run -p 8080:8080 -e ILP_ENDPOINT=https://your-custom-endpoint.com ilp-submission-2
```

**Testing endpoint switching:**
```cmd
REM Test with Google (should fail with 404)
docker run -p 8080:8080 -e ILP_ENDPOINT=https://www.google.com ilp-submission-2
```

If your service is correctly configured, it will attempt to call `https://www.google.com/drones` and receive a 404 error, confirming that the environment variable is being read properly.

---

## Submission Package

The submission should be a ZIP file containing:

```
ilp_submission_2/
├── ilp_submission_image.tar      # Docker image (TAR format)
├── src/                          # Java source files
├── pom.xml                       # Maven configuration
├── Dockerfile                    # Docker build configuration
├── README.md                     # This documentation
└── (other project files)
```

**Create submission package:**
```cmd
cd C:\Users\franksun\Desktop\uni_of_edinburgh\Year3\Informatics_Large_Pratical\coursework\Ilp_submission_2
Compress-Archive -Path Cw2-main\* -DestinationPath ilp_submission_2.zip
```

**Verify TAR file exists:**
```cmd
dir Cw2-main\ilp_submission_image.tar
```

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/uk/ac/ed/acp/cw2/
│   │   ├── Application.java                    # Main Spring Boot application
│   │   ├── configuration/
│   │   │   └── IlpEndpointConfig.java          # REST client & environment config
│   │   ├── controller/
│   │   │   ├── GeoController.java              # CW1: Geographic endpoints
│   │   │   └── DroneController.java            # CW2: Drone delivery endpoints
│   │   ├── service/
│   │   │   ├── GeoService.java                 # CW1: Geographic service interface
│   │   │   ├── DroneQueryService.java          # CW2: Drone service interface
│   │   │   └── impl/
│   │   │       ├── GeoServiceImpl.java         # CW1: Geographic algorithms
│   │   │       └── DroneQueryServiceImpl.java  # CW2: Drone delivery logic
│   │   ├── dto/
│   │   │   ├── LngLat.java                     # Domain: Coordinate
│   │   │   ├── Region.java                     # Domain: Polygon region
│   │   │   ├── TwoPositionsRequest.java        # CW1: Request DTO
│   │   │   ├── NextPositionRequest.java        # CW1: Request DTO
│   │   │   ├── IsInRegionRequest.java          # CW1: Request DTO
│   │   │   ├── Result.java                     # CW1: Response wrapper
│   │   │   ├── Drone.java                      # CW2: Drone entity
│   │   │   ├── ServicePoint.java               # CW2: Service point entity
│   │   │   ├── RestrictedArea.java             # CW2: No-fly zone entity
│   │   │   ├── DroneServicePointAvailability.java  # CW2: Availability schedule
│   │   │   ├── MedDispatchRec.java             # CW2: Dispatch request
│   │   │   ├── QueryCondition.java             # CW2: Query condition
│   │   │   └── DeliveryPathResponse.java       # CW2: Delivery path response
│   │   └── data/
│   │       └── RuntimeEnvironment.java         # Runtime configuration
│   └── resources/
│       └── application.yml                      # Application configuration
└── test/
    └── java/uk/ac/ed/acp/cw2/
        ├── GeoServiceUnitTest.java              # CW1: Service layer tests
        ├── GeoControllerWebTest.java            # CW1: Controller tests
        ├── ApiIntegrationTest.java              # Integration tests
        ├── ApplicationIntegrationTest.java      # Full app context tests
        └── AcpCw2ApplicationTests.java          # Basic application tests
```

### Architecture Highlights

✅ **Clean Separation**: CW1 (Geographic) + CW2 (Drone Delivery) clearly separated  
✅ **Testability**: Interface-based design allows easy mocking  
✅ **External Data Integration**: All drone/service point data from ILP REST API  
✅ **Pragmatic Design**: DTOs double as domain models (no over-engineering)  
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
7. **Test with external ILP service** - Always verify against real data

---

## 📄 License

This project is part of the Informatics Large Practical coursework at the University of Edinburgh.

---

## 👤 Author

**Student ID:** s2564099  
**Course:** Informatics Large Practical - Coursework 2  
**Institution:** University of Edinburgh - Year 3  
**Academic Year:** 2024/2025

---

## 📚 External Dependencies

### ILP REST Service

This service depends on an external REST API hosted on Azure:
- **Base URL**: `https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net`
- **Endpoints Used**:
  - `/drones` - Drone capabilities and metadata
  - `/service-points` - Drone service point locations
  - `/restricted-areas` - No-fly zones
  - `/drones-for-service-points` - Drone availability schedules

**Data Structure Examples:**

**Drone:**
```json
{
  "name": "Drone 1",
  "id": 1,
  "capability": {
    "cooling": true,
    "heating": true,
    "capacity": 4.0,
    "maxMoves": 2000,
    "costPerMove": 0.01,
    "costInitial": 4.3,
    "costFinal": 6.5
  }
}
```

**Service Point:**

  "name": "Appleton Tower",
  "id": 1,
  "location": {
    "lng": -3.1863580788986368,
    "lat": 55.94468066708487,
    "alt": 50.0
  }
}
```

**Restricted Area:**
```json
{
  "name": "George Square Area",
  "id": 1,
  "limits": {"lower": 0, "upper": -1},
  "vertices": [
    {"lng": -3.190578818321228, "lat": 55.94402412577528},
    {"lng": -3.1899887323379517, "lat": 55.94284650540911},
    ...
  ]
}
```

**Drone Availability:**
```json
{
  "servicePointId": 1,
  "drones": [
    {
      "id": "1",
      "availability": [
        {
          "dayOfWeek": "MONDAY",
          "from": "00:00:00",
          "until": "23:59:59"
        }
      ]
    }
  ]
}
```
---

**Last Updated:** 2025-11-24  
**Version:** 2.0 (CW2 Complete)

