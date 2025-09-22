# Course Service MapStruct Mappers

This directory contains comprehensive MapStruct mappers for all course service entities and DTOs. All mappers follow the established pattern using `CustomMapperConfig` for consistent configuration.

## Implemented Mappers

### Core Entity Mappers

1. **CourseMapper** - Maps Course entity with:
   - `CourseDetailDTO` - Full course details with author, thumbnail, and lessons
   - `CourseSummaryDTO` - Summary view with enrollment count
   - `CourseCreateDTO` - For course creation
   - `CourseUpdateDTO` - For course updates

2. **LessonMapper** - Maps Lesson entity with:
   - `LessonBriefDTO` - Brief lesson information for lists
   - `LessonCreateDTO` - For lesson creation
   - `LessonUpdateDTO` - For lesson updates

3. **LessonProgressMapper** - Maps LessonProgress entity with:
   - `LessonProgressDetailDTO` - Progress tracking details
   - `LessonProgressUpdateDTO` - For progress updates

### Interactive Content Mappers

4. **QuizMapper** - Maps Quiz entity with:
   - `QuizDetailDTO` - Complete quiz with questions
   - `QuizCreateDTO` - For quiz creation
   - `QuizUpdateDTO` - For quiz updates

5. **QuizQuestionMapper** - Maps QuizQuestion entity with:
   - `QuizQuestionDetailDTO` - Question with options
   - `QuizQuestionCreateDTO` - For question creation
   - `QuizQuestionUpdateDTO` - For question updates

6. **QuizOptionMapper** - Maps QuizOption entity with:
   - `QuizOptionDetailDTO` - Option details
   - `QuizOptionCreateDTO` - For option creation
   - `QuizOptionUpdateDTO` - For option updates

7. **AssignmentMapper** - Maps Assignment entity with:
   - `AssignmentDetailDTO` - Assignment details
   - `AssignmentCreateDTO` - For assignment creation
   - `AssignmentUpdateDTO` - For assignment updates

8. **AssignmentSubmissionMapper** - Maps AssignmentSubmission entity with:
   - `AssignmentSubmissionDetailDTO` - Submission details with grading
   - `AssignmentSubmissionCreateDTO` - For submission creation

### Coding Exercise Mappers

9. **CodingExerciseMapper** - Maps CodingExercise (Codelab) entity with:
   - `CodingExerciseDetailDTO` - Exercise with test cases
   - `CodingExerciseCreateDTO` - For exercise creation
   - `CodingExerciseUpdateDTO` - For exercise updates

10. **CodingTestCaseMapper** - Maps CodingTestCase entity with:
    - `CodingTestCaseDTO` - Test case details
    - `CodingTestCaseCreateDTO` - For test case creation
    - `CodingTestCaseUpdateDTO` - For test case updates

11. **CodingSubmissionMapper** - Maps CodingSubmission entity with:
    - `CodingSubmissionDetailDTO` - Submission with execution results
    - `CodingSubmissionCreateDTO` - For submission creation

### Enrollment & Purchase Mappers

12. **EnrollmentMapper** - Maps CourseEnrollment entity with:
    - `EnrollmentDTO` - Enrollment details
    - `EnrollRequestDTO` - For enrollment requests

13. **PurchaseMapper** - Maps CoursePurchase entity with:
    - `CoursePurchaseDTO` - Purchase details
    - `CoursePurchaseRequestDTO` - For purchase requests

14. **CertificateMapper** - Maps Certificate entity with:
    - `CertificateDTO` - Certificate details
    - `CertificateIssueRequestDTO` - For certificate issuance

## Key Features

### Configuration
- All mappers use `CustomMapperConfig` for consistent Spring integration
- Unmapped target policy set to IGNORE for flexibility
- Constructor injection strategy for better performance

### Mapping Patterns
- **Entity → DTO**: Complete mapping for API responses
- **Create DTO → Entity**: Mapping for creation with proper defaults
- **Update DTO → Entity**: Partial updates with null value handling
- **Complex expressions**: Using MapStruct expressions for computed fields

### Relationships
- Proper handling of lazy-loaded relationships
- Mapping between entity references and ID fields
- Circular dependency management with selective mapping

### Special Handling
- Embedded ID entities (CourseEnrollment, LessonProgress) handled properly
- Package-private classes noted with helper methods in service layer
- Timestamp fields auto-generated where appropriate
- Status field defaults set correctly

## Usage Examples

```java
@Service
public class CourseService {
    
    @Autowired
    private CourseMapper courseMapper;
    
    public CourseDetailDTO getCourseDetails(Long courseId) {
        Course course = courseRepository.findById(courseId);
        return courseMapper.toDetailDto(course);
    }
    
    public Course createCourse(CourseCreateDTO createDto, User author, Media thumbnail) {
        return courseMapper.toEntity(createDto, author, thumbnail);
    }
}
```

## Notes

1. **Shared Dependencies**: Mappers reference `UserMapper` and `MediaMapper` from shared package
2. **Error Handling**: Package-private embedded IDs require service-layer entity creation
3. **Performance**: MapStruct generates compile-time implementations for optimal performance
4. **Extensibility**: Easy to add new mapping methods as requirements evolve