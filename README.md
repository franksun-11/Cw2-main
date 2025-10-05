# ILP CW1 - REST API Service

## 项目架构设计文档 (Project Architecture Design)

### 📋 项目概述

这是一个基于 Spring Boot 的 REST API 服务，实现地理位置计算和几何判断功能。

**技术栈：**
- Java 21
- Spring Boot 3.4.3
- Lombok
- Jakarta EE
- Maven
- Docker

---

## 🏛️ 架构设计原则

### 分层架构 (Layered Architecture)---

## 🎯 各层职责详解

### 1. Controller Layer（控制器层）

**✅ 应该做：**
- 接收HTTP请求
- 参数验证（语法层面）
- 调用Service层方法
- 返回HTTP响应和状态码
- 异常处理（委托给GlobalExceptionHandler）

**❌ 不应该做：**
- 包含业务逻辑
- 直接进行复杂计算
- 访问数据库（如果有）
- 包含算法实现
---

### 2. Service Layer（服务层）

**接口设计原则：**
- 定义业务契约
- 方便测试（可Mock）
- 支持多实现

**实现类职责：**
- ✅ 核心业务逻辑
- ✅ 复杂计算
- ✅ 数据转换
- ✅ 调用其他服务
- ✅ 事务管理（如有数据库）

**关键算法实现：**

1. **欧几里得距离计算**
   ```java
   distance = √((lng1 - lng2)² + (lat1 - lat2)²)
   ```

2. **接近判断**
   ```java
   isClose = distance < 0.00015
   ```

3. **下一位置计算**
   ```java
   newLng = startLng + MOVE_DISTANCE * cos(angle)
   newLat = startLat + MOVE_DISTANCE * sin(angle)
   MOVE_DISTANCE = 0.00015
   ```

4. **点在多边形内判断（射线法）**
    - 从点向右发射射线
    - 统计与多边形边的交点数
    - 奇数 = 在内部，偶数 = 在外部

---

### 3. DTO Layer（数据传输对象）

**设计原则：**
- 使用 `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` (Lombok)
- 使用 Jakarta Validation 注解验证
- 不包含业务逻辑
- 可以包含简单的辅助方法（如 `isValid()`）

---

### 4. Exception Layer（异常处理层）

**GlobalExceptionHandler 处理：**
- `MethodArgumentNotValidException` → 400 Bad Request
- `HttpMessageNotReadableException` → 400 Bad Request
- `Exception` → 400 Bad Request（根据作业要求）

**原则：**
- 集中处理，避免在Controller中try-catch
- 统一返回格式
- 记录日志

**详细流程：**

1. **请求到达** → `@PostMapping` 接收
2. **JSON转DTO** → Spring自动反序列化
3. **参数验证** → `@Valid` 触发验证
4. **业务处理** → 调用 `Service` 方法
5. **计算结果** → Service 执行算法
6. **返回响应** → `ResponseEntity.ok(result)`
7. **异常处理** → `GlobalExceptionHandler` 捕获

---

## 📐 SOLID 设计原则应用

### 1. 单一职责原则 (Single Responsibility)
- Controller: 只负责HTTP处理
- Service: 只负责业务逻辑
- DTO: 只负责数据传输

### 2. 开闭原则 (Open/Closed)
- 使用接口，可扩展新实现
- 不修改现有代码，通过继承扩展

### 3. 里氏替换原则 (Liskov Substitution)
- Service接口的任何实现都可以替换

### 4. 接口隔离原则 (Interface Segregation)
- 接口方法精简，不强迫实现不需要的方法

### 5. 依赖倒置原则 (Dependency Inversion)
```java
// ✅ 依赖接口
private final GeoService geoService;

// ❌ 依赖实现
private final GeoServiceImpl geoService;
```


