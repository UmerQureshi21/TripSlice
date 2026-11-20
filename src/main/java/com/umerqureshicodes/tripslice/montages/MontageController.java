package com.umerqureshicodes.tripslice.montages;

import com.umerqureshicodes.tripslice.users.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class MontageController {

    private final MontageService montageService;
    public MontageController(MontageService montageService) {
        this.montageService = montageService;
    }

    @PostMapping("/montages")
    public MontageResponseDTO createMontage(@RequestBody MontageRequestDTO montageRequestDTO, @AuthenticationPrincipal AppUser appUser) {
        return montageService.createMontage(montageRequestDTO, appUser.getUsername());
    }

    @DeleteMapping("/montages/{id}")
    public ResponseEntity<Void> deleteMontage(@PathVariable Long id, @AuthenticationPrincipal AppUser appUser) {
        montageService.deleteMontage(id, appUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/montages")
    public List<MontageResponseDTO> getUrl(@AuthenticationPrincipal AppUser user){
        return montageService.getMontages(user.getUsername());
    }

    @GetMapping("/montages/most-vids")
    public MontageResponseDTO getMostVideoMontage(@AuthenticationPrincipal AppUser user){
        return montageService.getMostVideoMontage(user.getUsername());
    }

    @GetMapping("/montages/longest")
    public MontageResponseDTO getLongestMontage(@AuthenticationPrincipal AppUser user) throws IOException, InterruptedException {
        return montageService.getLongestMontage(user.getUsername());
    }
}
