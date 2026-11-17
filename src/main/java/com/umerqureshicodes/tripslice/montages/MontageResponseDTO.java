package com.umerqureshicodes.tripslice.montages;

import com.umerqureshicodes.tripslice.videos.VideoResponseDTO;

import java.util.Date;
import java.util.List;

public record MontageResponseDTO(
        String name,
        List<VideoResponseDTO> videos,
        String prompt,
        Date createdAt,
        int duration,
        String preSignedUrl,
        boolean searchable // true once the montage has an embedding
) {
}
