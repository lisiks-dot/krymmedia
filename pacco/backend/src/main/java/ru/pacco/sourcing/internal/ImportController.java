package ru.pacco.sourcing.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.pacco.shared.BusinessException;
import ru.pacco.sourcing.SourcingService;

/** Импорт каталога поставщика: роли admin и moderator. */
@RestController
@RequestMapping("/api/admin/import")
class ImportController {

    private final SourcingService sourcing;

    ImportController(SourcingService sourcing) {
        this.sourcing = sourcing;
    }

    record Start(String url, Integer maxItems) {
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SourcingService.JobView start(@RequestBody Start body, @AuthenticationPrincipal Jwt jwt) {
        return sourcing.startPage(body.url(), body.maxItems(), user(jwt));
    }

    @PostMapping(path = "/feed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SourcingService.JobView feed(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        try {
            return sourcing.importFeed(new String(file.getBytes(), StandardCharsets.UTF_8), user(jwt));
        } catch (IOException e) {
            throw BusinessException.unprocessable("bad_feed", "Не удалось прочитать файл");
        }
    }

    @GetMapping("/jobs")
    List<SourcingService.JobView> jobs() {
        return sourcing.jobs();
    }

    @GetMapping("/jobs/{id}")
    SourcingService.JobView job(@PathVariable long id) {
        return sourcing.job(id);
    }

    @GetMapping("/items")
    List<SourcingService.ItemView> items(@RequestParam(defaultValue = "NEW") String status, @RequestParam(defaultValue = "200") int limit) {
        return sourcing.items(status, limit);
    }

    @PostMapping("/items/{id}/approve")
    SourcingService.ItemView approve(@PathVariable long id, @RequestBody SourcingService.Approve body) {
        return sourcing.approve(id, body);
    }

    record Reason(String reason) {
    }

    @PostMapping("/items/{id}/reject")
    SourcingService.ItemView reject(@PathVariable long id, @RequestBody(required = false) Reason body) {
        return sourcing.reject(id, body == null ? null : body.reason());
    }

    @PostMapping("/items/{id}/reopen")
    SourcingService.ItemView reopen(@PathVariable long id) {
        return sourcing.reopen(id);
    }

    @PostMapping("/items/{id}/refresh")
    SourcingService.ItemView refresh(@PathVariable long id) {
        return sourcing.refresh(id);
    }

    private static String user(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
