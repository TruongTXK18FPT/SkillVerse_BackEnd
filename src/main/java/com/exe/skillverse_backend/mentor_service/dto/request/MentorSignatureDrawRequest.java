package com.exe.skillverse_backend.mentor_service.dto.request;

import java.util.List;
import lombok.Data;

@Data
public class MentorSignatureDrawRequest {
    private Integer canvasWidth;
    private Integer canvasHeight;
    private List<Stroke> strokes;

    @Data
    public static class Stroke {
        private Double lineWidth;
        private List<Point> points;
    }

    @Data
    public static class Point {
        private Double x;
        private Double y;
    }
}
