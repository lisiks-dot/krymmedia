package ru.pacco.catalog.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.pacco.shared.BusinessException;

/**
 * Фото и видео товара. Тип файла определяем по сигнатуре, а не по имени.
 * Фото — JPEG/PNG/WebP до 10 МБ, до 12 штук; видео — MP4/WebM до 60 МБ, до 2 штук.
 */
@Service
public class MediaService {

    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    private static final long MAX_IMAGE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO = 60L * 1024 * 1024;
    private static final int MAX_IMAGES = 12;
    private static final int MAX_VIDEOS = 2;

    public record MediaDto(long id, String kind, String contentType, String url) {
        static MediaDto of(ProductMedia m) {
            return new MediaDto(m.getId(), m.getKind(), m.getContentType(),
                    "/api/products/" + m.getProductId() + "/media/" + m.getId());
        }
    }

    private final ProductMediaRepository media;
    private final MediaStorage storage;

    public MediaService(ProductMediaRepository media, MediaStorage storage) {
        this.media = media;
        this.storage = storage;
    }

    @Transactional
    public MediaDto upload(long productId, MultipartFile file, String by) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.unprocessable("media_empty", "Выберите файл");
        }
        String type = sniff(file);
        String kind = type.startsWith("video/") ? VIDEO : IMAGE;
        long limit = VIDEO.equals(kind) ? MAX_VIDEO : MAX_IMAGE;
        if (file.getSize() > limit) {
            throw BusinessException.unprocessable("media_too_big", VIDEO.equals(kind) ? "Видео больше 60 МБ" : "Фото больше 10 МБ");
        }
        long count = media.countByProductIdAndKind(productId, kind);
        if (count >= (VIDEO.equals(kind) ? MAX_VIDEOS : MAX_IMAGES)) {
            throw BusinessException.unprocessable("media_limit", VIDEO.equals(kind) ? "У товара уже 2 видео" : "У товара уже 12 фото");
        }
        String ext = switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
        String key = "products/" + productId + "/" + UUID.randomUUID() + ext;
        long size;
        try (InputStream in = file.getInputStream()) {
            size = storage.save(key, in);
        } catch (IOException e) {
            throw BusinessException.unprocessable("media_unreadable", "Не удалось прочитать файл");
        }
        int position = media.findByProductIdOrderByPositionAscIdAsc(productId).size();
        return MediaDto.of(media.save(new ProductMedia(productId, kind, type, size, key, position, by)));
    }

    @Transactional
    public void delete(long productId, long mediaId) {
        ProductMedia m = media.findByIdAndProductId(mediaId, productId).orElseThrow(() -> BusinessException.notFound("Файл"));
        media.delete(m);
        storage.delete(m.getStorageKey());
        renumber(productId);
    }

    /** Новый порядок: id в нужной последовательности; первое фото — обложка карточки. */
    @Transactional
    public List<MediaDto> reorder(long productId, List<Long> ids) {
        List<ProductMedia> all = media.findByProductIdOrderByPositionAscIdAsc(productId);
        List<ProductMedia> ordered = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            all.stream().filter(m -> m.getId().equals(id)).findFirst().ifPresent(ordered::add);
        }
        all.stream().filter(m -> !ordered.contains(m)).forEach(ordered::add);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i);
        return ordered.stream().map(MediaDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MediaDto> of(long productId) {
        return media.findByProductIdOrderByPositionAscIdAsc(productId).stream().map(MediaDto::of).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MediaDto>> of(Collection<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        return media.findByProductIdInOrderByProductIdAscPositionAscIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductMedia::getProductId, Collectors.mapping(MediaDto::of, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public ProductMedia require(long productId, long mediaId) {
        return media.findByIdAndProductId(mediaId, productId).orElseThrow(() -> BusinessException.notFound("Файл"));
    }

    public MediaStorage storage() {
        return storage;
    }

    private void renumber(long productId) {
        List<ProductMedia> all = media.findByProductIdOrderByPositionAscIdAsc(productId);
        for (int i = 0; i < all.size(); i++) all.get(i).moveTo(i);
    }

    private static String sniff(MultipartFile file) {
        byte[] b = new byte[16];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(b, 0, b.length);
        } catch (IOException e) {
            throw BusinessException.unprocessable("media_unreadable", "Не удалось прочитать файл");
        }
        if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (n >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "video/mp4";
        if (n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) return "video/webm";
        throw BusinessException.unprocessable("media_type", "Подходят фото JPEG, PNG, WebP и видео MP4, WebM");
    }
}
