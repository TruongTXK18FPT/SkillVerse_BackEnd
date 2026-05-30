package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.impl.MediaServiceImpl;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseRevisionRepository courseRevisionRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private MediaMapper mediaMapper;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private MediaServiceImpl mediaService;

    @Test
    void detach_clearsCourseAndCourseRevisionThumbnailReferences() {
        Long mediaId = 901L;
        Long actorId = 7L;
        Media media = Media.builder()
                .id(mediaId)
                .url("https://cdn.example.com/thumb.png")
                .type("image/png")
                .uploadedBy(actorId)
                .build();
        Course course = Course.builder().id(100L).thumbnail(media).build();
        CourseRevision revision = CourseRevision.builder().id(200L).thumbnail(media).build();

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(courseRepository.findByThumbnailId(mediaId)).thenReturn(List.of(course));
        when(courseRevisionRepository.findByThumbnailId(mediaId)).thenReturn(List.of(revision));
        when(lessonRepository.findByVideoMediaId(mediaId)).thenReturn(Collections.emptyList());

        mediaService.detach(mediaId, actorId);

        assertNull(course.getThumbnail());
        assertNull(revision.getThumbnail());
        verify(courseRepository).save(course);
        verify(courseRevisionRepository).save(revision);
        verify(mediaRepository).save(media);
    }
}
