package com.exe.skillverse_backend.shared.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.exe.skillverse_backend.shared.service.impl.CloudinaryServiceImpl;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryServiceImpl(cloudinary);
        ReflectionTestUtils.setField(service, "baseFolder", "skillverse");
        ReflectionTestUtils.setField(service, "useFilename", true);
        ReflectionTestUtils.setField(service, "uniqueFilename", true);
        ReflectionTestUtils.setField(service, "overwrite", false);
    }

    @Test
    @DisplayName("uploadImage should reject invalid image types")
    void uploadImage_ShouldRejectInvalidImageTypes() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.uploadImage(file, "avatars"));
    }

    @Test
    @DisplayName("uploadVideo should use chunked upload for files larger than 100MB")
    void uploadVideo_ShouldUseChunkedUploadForFilesLargerThan100Mb() throws Exception {
        MultipartFile file = oversizedVideo();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.uploadLarge(any(InputStream.class), anyMap())).thenReturn(Map.of(
                "public_id", "video-public-id",
                "secure_url", "https://cdn.skillverse.vn/video.mp4"));

        Map<String, Object> result = service.uploadVideo(file, "courses");

        assertEquals("video-public-id", result.get("public_id"));
        verify(uploader).uploadLarge(any(InputStream.class), anyMap());
        verify(uploader, never()).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("deleteFile should reject blank public ids")
    void deleteFile_ShouldRejectBlankPublicIds() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteFile(" ", "image"));
    }

    private MultipartFile oversizedVideo() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("clip.mp4");
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.getSize()).thenReturn(101L * 1024 * 1024);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        return file;
    }
}
