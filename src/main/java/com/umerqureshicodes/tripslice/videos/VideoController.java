package com.umerqureshicodes.tripslice.videos;

import com.umerqureshicodes.tripslice.users.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping("/videos")
    public List<VideoResponseDTO> getAllVideos(@AuthenticationPrincipal AppUser user) {
        // user is the one from JWT
        String email = user.getUsername();
        return videoService.getVideos(email);
    }

    @PostMapping("/videos")
    public List<VideoResponseDTO> uploadVideoToBucket(
            @AuthenticationPrincipal AppUser user,
            @RequestParam("files") List<MultipartFile> multipartFiles
    ) {
        String email = user.getUsername();   // pulled from JWT-authenticated user
        return videoService.save(multipartFiles, email);
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        videoService.deleteVideo(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
