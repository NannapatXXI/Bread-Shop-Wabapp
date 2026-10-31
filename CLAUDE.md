# BreadShop XXI

## Backend

- Java 21
- Spring Boot 4
- MySQL 8

## Architecture

Controller
 -> Service
 -> Repository

## Security

- JWT Authentication
- BCrypt Password Encoder
- Role Based Access Control

## Coding Standards

- Constructor Injection only
- No Field Injection
- Use DTO for Request/Response
- GlobalExceptionHandler required

## API Response

All API responses use ApiResponse<T>

## Frontend

- Next.js
- TypeScript
- TailwindCSS

## Database

- Use Flyway Migration
- Foreign Key required
- Soft Delete preferred