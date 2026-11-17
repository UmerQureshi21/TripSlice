package com.umerqureshicodes.tripslice.montages;

import com.umerqureshicodes.tripslice.videos.VideoRequestDTO;

import java.util.List;

public record MontageRequestDTO(
        String name,
        List<VideoRequestDTO> videoRequestDTOs,
        String prompt
) {
}
