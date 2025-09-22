package com.exe.skillverse_backend.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> items;   // Danh sách dữ liệu (nội dung trang)
    private int page;        // Số trang hiện tại (bắt đầu từ 0 hoặc 1 tùy convention)
    private int size;        // Kích thước trang (số phần tử mỗi trang)
    private long total;      // Tổng số phần tử trong DB
}

