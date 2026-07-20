# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Wrongbook (错题集)** - a full-stack web application for managing and tracking incorrect test questions for students.

## Tech Stack

- **Backend**: Spring Boot 3.2.0, Java 17
- **Database**: MySQL with JPA/Hibernate
- **Frontend**: Pure HTML/CSS/JavaScript
- **Build**: Maven

## Commands

```bash
# Build the project
mvn clean package

# Run the backend
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName
```

## Architecture

### Backend (Spring Boot)
```
src/main/java/com/wrongbook/
├── WrongbookApplication.java     # Main entry point
├── controller/                   # REST API endpoints
│   ├── WrongQuestionController.java
│   └── RetryRecordController.java
├── entity/                      # JPA entities
│   ├── WrongQuestion.java
│   └── RetryRecord.java
├── repository/                  # Data access layer
│   ├── WrongQuestionRepository.java
│   └── RetryRecordRepository.java
└── service/                    # Business logic
    ├── WrongQuestionService.java
    └── RetryRecordService.java
```

### Frontend
```
src/main/webapp/
├── index.html      # Main page
├── css/style.css   # Styles
└── js/app.js      # Frontend logic
```

## Database

- MySQL database: `wrongbook`
- Username: `root`, Password: `123456`
- Port: `localhost:3306`
- Schema: See `src/main/resources/schema.sql`

## Key Features

- CRUD operations for wrong questions
- Retry record tracking with automatic status updates
- CSV import for batch adding questions
- Filter by grade (default filter, saved to localStorage), subject, status, and keyword
- Status auto-updates: 错误 → 通过, 反复错, 反复错后通过

## API Endpoints

**Wrong Questions**: `GET/POST/PUT/DELETE /api/wrong-questions`
- `POST /api/wrong-questions/import` - CSV import

**Retry Records**: `GET/POST/DELETE /api/retry-records`
- `GET /api/wrong-questions/{id}/retry-records` - Get retry records for a question
