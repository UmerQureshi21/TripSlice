package com.umerqureshicodes.tripslice.videos;

public class VideoResponseDTO {
    private final Long id; // database id, used to delete the video
    private final String name;
    private final String videoId;
    private String previewUrl;
    private boolean searchable; // true once the video has a summary and an embedding

    public VideoResponseDTO(Long id, String name, String videoId, String previewUrl, boolean searchable) {
        this.id = id;
        this.name = name;
        this.videoId = videoId;
        this.previewUrl = previewUrl;
        this.searchable = searchable;
    }

    public VideoResponseDTO(Long id, String name, String videoId, boolean searchable) {
        this.id = id;
        this.name = name;
        this.videoId = videoId;
        this.searchable = searchable;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public boolean isSearchable() {
        return searchable;
    }


}
