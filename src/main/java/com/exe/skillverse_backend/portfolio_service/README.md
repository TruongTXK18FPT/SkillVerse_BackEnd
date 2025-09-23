# Portfolio Service Module

## Overview

The Portfolio Service Module is a comprehensive solution for managing user portfolios in the Skillverse Backend application. It allows users to showcase their projects, products, and certificates, providing a complete professional profile that can be used for CV generation and portfolio export.

## Architecture

### Package Structure

```
portfolio_service/
├── controller/          # REST API endpoints
├── dto/                # Data Transfer Objects
├── entity/             # JPA entities
├── exception/          # Custom exceptions
├── repository/         # Spring Data JPA repositories
└── service/            # Business logic implementations (no interfaces)
```

### Design Philosophy

This module follows a simplified architecture without service interfaces, aligning with the rest of the Skillverse Backend codebase. Service classes are implemented directly as Spring components, reducing unnecessary abstraction layers while maintaining clean separation of concerns.

## Entities

### Project Entity
Represents user projects with the following fields:
- **id**: Primary key
- **userId**: Foreign key to User entity
- **title**: Project title (max 200 chars, required)
- **description**: Detailed project description
- **techStack**: Technologies used (max 255 chars)
- **projectUrl**: Project URL/link
- **mediaId**: Foreign key to Media entity
- **completedDate**: Project completion date
- **createdAt/updatedAt**: Audit timestamps

### Product Entity
Represents user products with the following fields:
- **id**: Primary key
- **userId**: Foreign key to User entity
- **name**: Product name (max 200 chars, required)
- **description**: Product description
- **category**: Product category (max 100 chars)
- **productUrl**: Product URL/link
- **mediaId**: Foreign key to Media entity
- **releaseDate**: Product release date
- **createdAt/updatedAt**: Audit timestamps

### UserCertificate Entity
Represents user certificates with the following fields:
- **id**: Primary key
- **userId**: Foreign key to User entity
- **certificateId**: Foreign key to Certificate entity
- **issueDate**: Certificate issue date (required)
- **expiresAt**: Certificate expiration date (optional)
- **fileId**: Foreign key to FileUpload entity
- **verifiedBy**: Foreign key to User entity (verifier)
- **createdAt**: Audit timestamp

## API Endpoints

### Project Management (`/api/portfolio/projects`)
- `POST /` - Create new project
- `PUT /{projectId}` - Update project
- `DELETE /{projectId}` - Delete project
- `GET /{projectId}` - Get project by ID
- `GET /` - Get all user projects
- `GET /paginated` - Get projects with pagination
- `GET /by-technology?technology={tech}` - Filter by technology
- `GET /by-date-range?startDate={date}&endDate={date}` - Filter by date range
- `GET /count` - Get project count

### Product Management (`/api/portfolio/products`)
- `POST /` - Create new product
- `PUT /{productId}` - Update product
- `DELETE /{productId}` - Delete product
- `GET /{productId}` - Get product by ID
- `GET /` - Get all user products
- `GET /paginated` - Get products with pagination
- `GET /by-category?category={cat}` - Filter by category
- `GET /by-date-range?startDate={date}&endDate={date}` - Filter by date range
- `GET /categories` - Get distinct categories
- `GET /count` - Get product count

### Certificate Management (`/api/portfolio/certificates`)
- `POST /` - Create new user certificate
- `PUT /{certificateId}` - Update user certificate
- `DELETE /{certificateId}` - Delete user certificate
- `GET /{certificateId}` - Get certificate by ID
- `GET /` - Get all user certificates
- `GET /paginated` - Get certificates with pagination
- `GET /active` - Get active (non-expired) certificates
- `GET /expired` - Get expired certificates
- `GET /verified` - Get verified certificates
- `GET /count/active` - Get active certificate count
- `GET /count/total` - Get total certificate count

### Portfolio Export (`/api/portfolio`)
- `GET /complete?userId={id}` - Get complete portfolio
- `GET /active?userId={id}` - Get active portfolio (completed projects + active certificates)
- `GET /cv?userId={id}` - Get optimized portfolio for CV generation

## Features

### Security
- User-based access control: users can only access their own portfolio items
- Custom exception handling for not found and access denied scenarios
- Input validation with comprehensive error messages

### Data Management
- Proper JPA relationships with lazy loading
- Optimistic locking for data integrity
- Audit trails with creation and update timestamps
- Pagination support for large datasets

### Portfolio Export
- **Complete Portfolio**: All projects, products, and certificates
- **Active Portfolio**: Completed projects and non-expired certificates only
- **CV Portfolio**: Curated selection for professional presentation
  - 5 most recent completed projects
  - 3 most recent products
  - All verified and active certificates

### Advanced Querying
- Technology-based project filtering
- Category-based product filtering
- Date range filtering for projects and products
- Certificate status filtering (active/expired/verified)
- Statistical summaries and counts

## DTOs and Validation

### Request DTOs
All request DTOs include validation annotations:
- `@NotBlank` for required string fields
- `@Size` for length constraints
- `@NotNull` for required fields
- `@Valid` for nested object validation

### Response DTOs
Response DTOs include:
- All entity fields
- Computed fields (e.g., `isExpired` for certificates)
- Related entity information (e.g., media URLs, certificate names)
- Audit information

## Error Handling

### Custom Exceptions
- `PortfolioNotFoundException`: For non-existent resources
- `PortfolioAccessDeniedException`: For unauthorized access attempts

### Global Exception Handler
Integrated with the application's global exception handler to provide:
- Consistent error response format
- Proper HTTP status codes
- Detailed error messages
- Request path information
- Timestamp logging

## Database Relationships

### Foreign Key Relationships
- **Project** → User (many-to-one)
- **Project** → Media (many-to-one, optional)
- **Product** → User (many-to-one)
- **Product** → Media (many-to-one, optional)
- **UserCertificate** → User (many-to-one)
- **UserCertificate** → Certificate (many-to-one)
- **UserCertificate** → FileUpload (many-to-one, optional)
- **UserCertificate** → User as verifier (many-to-one, optional)

## Usage Examples

### Creating a Project
```json
POST /api/portfolio/projects?userId=1
{
  "title": "E-commerce Website",
  "description": "Full-stack e-commerce platform with React and Spring Boot",
  "techStack": "React, Spring Boot, PostgreSQL, Docker",
  "projectUrl": "https://github.com/user/ecommerce",
  "completedDate": "2024-09-15"
}
```

### Getting Portfolio for CV
```json
GET /api/portfolio/cv?userId=1
{
  "userId": 1,
  "userFullName": "John Doe",
  "userEmail": "john.doe@example.com",
  "generatedAt": "2024-09-21T10:30:00",
  "projects": [...], // 5 most recent completed projects
  "products": [...], // 3 most recent products
  "certificates": [...], // All verified active certificates
  "totalProjects": 5,
  "totalProducts": 3,
  "totalCertificates": 8,
  "activeCertificates": 8
}
```

## Dependencies

### Required Dependencies
- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Validation
- Lombok
- SpringDoc OpenAPI (Swagger)

### Internal Dependencies
- `auth_service` (User entity and repository)
- `shared` (Media, Certificate, FileUpload entities)
- `shared.exception` (Global exception handler)

## Future Enhancements

### Potential Features
1. **Portfolio Templates**: Predefined portfolio layouts
2. **Export Formats**: PDF generation, JSON export
3. **Portfolio Sharing**: Public portfolio URLs
4. **Analytics**: Portfolio view statistics
5. **Recommendations**: Project/skill recommendations
6. **Collaboration**: Team project support
7. **Version Control**: Portfolio change history
8. **Integration**: Third-party platform integration (LinkedIn, GitHub)

## Testing Recommendations

### Unit Tests
- Service layer business logic
- DTO validation
- Custom exception scenarios
- Repository query methods

### Integration Tests
- Controller endpoints
- Database transactions
- Security access controls
- Portfolio export functionality

## Monitoring and Logging

### Logging Strategy
- INFO level for create/update/delete operations
- DEBUG level for read operations
- WARN level for access violations
- ERROR level for unexpected errors

### Performance Considerations
- Lazy loading for related entities
- Pagination for large datasets
- Efficient queries with JOIN FETCH
- Indexed database columns for search operations

This portfolio module provides a solid foundation for managing and showcasing user achievements, projects, and certifications in the Skillverse platform.