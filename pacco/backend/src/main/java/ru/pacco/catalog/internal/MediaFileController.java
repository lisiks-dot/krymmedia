package ru.pacco.catalog.internal;

import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.shared.BusinessException;

/** Отдаёт файлы медиа; для видео работает Range — перемотка без загрузки целиком. */
@RestController
class MediaFileController {

    private final MediaService media;

    MediaFileController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/api/products/{id}/media/{mediaId}")
    ResponseEntity<Resource> file(@PathVariable long id, @PathVariable long mediaId) {
        ProductMedia m = media.require(id, mediaId);
        FileSystemResource file = new FileSystemResource(media.storage().path(m.getStorageKey()));
        if (!file.exists()) {
            throw BusinessException.notFound("Файл");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(file);
    }
}
