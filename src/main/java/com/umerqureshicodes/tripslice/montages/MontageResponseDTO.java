package com.umerqureshicodes.tripslice.montages;

import com.umerqureshicodes.tripslice.videos.VideoResponseDTO;

import java.util.Date;
import java.util.List;

public record MontageResponseDTO(
        Long id, // database id, used to delete the montage
        String name,
        List<VideoResponseDTO> videos,
        String prompt,
        Date createdAt,
        int duration,
        String preSignedUrl,
        boolean searchable // true once the montage has an embedding
) {
}
