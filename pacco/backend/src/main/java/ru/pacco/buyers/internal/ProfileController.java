package ru.pacco.buyers.internal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.buyers.BuyerProfileView;
import ru.pacco.buyers.BuyerProfiles;

@RestController
class ProfileController {

    private final BuyerProfiles profiles;

    ProfileController(BuyerProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/me/profile")
    BuyerProfileView get(@AuthenticationPrincipal Jwt jwt) {
        return profiles.get(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    @PutMapping("/api/me/profile")
    BuyerProfileView save(@AuthenticationPrincipal Jwt jwt, @RequestBody BuyerProfileView body) {
        return profiles.save(jwt.getSubject(), body);
    }
}
