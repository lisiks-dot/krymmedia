package ru.pacco.orders;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.pacco.orders.internal.DisputeRepository;
import ru.pacco.orders.internal.OrderPhoto;
import ru.pacco.orders.internal.OrderPhotoRepository;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.shared.BusinessException;

/**
 * Фото с приёмки на хабе — доказательство для покупателя и для споров; фото покупателя — вложения к спору.
 * Хранятся в базе (до 5 МБ, JPEG/PNG/WebP); при росте — перенос в S3-совместимое хранилище.
 */
@Service
public class OrderPhotos {

    public static final String HUB = "HUB";
    public static final String DISPUTE = "DISPUTE";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DISPUTE = 5;
    private static final int MAX_HUB = 20;
    private static final Set<OrderStatus> HUB_STATUSES = Set.of(OrderStatus.TO_HUB, OrderStatus.AT_HUB, OrderStatus.CONSOLIDATED);

    public record Photo(String contentType, byte[] data) {
    }

    private final OrderRepository orders;
    private final OrderPhotoRepository photos;
    private final DisputeRepository disputes;
    private final OrderService service;

    public OrderPhotos(OrderRepository orders, OrderPhotoRepository photos, DisputeRepository disputes, OrderService service) {
        this.orders = orders;
        this.photos = photos;
        this.disputes = disputes;
        this.service = service;
    }

    /** Хаб: фото при приёмке и упаковке. */
    @Transactional
    public OrderView.PhotoView hubUpload(String number, Integer lineNo, MultipartFile file) {
        PurchaseOrder o = service.require(number);
        if (!HUB_STATUSES.contains(o.getStatus())) {
            throw BusinessException.unprocessable("wrong_status", "Фото с хаба добавляют при приёмке и упаковке");
        }
        if (photos.countByOrderIdAndKind(o.getId(), HUB) >= MAX_HUB) {
            throw BusinessException.unprocessable("too_many_photos", "Для заказа уже загружено " + MAX_HUB + " фото");
        }
        checkLine(o, lineNo);
        OrderPhoto p = photos.save(new OrderPhoto(o.getId(), lineNo, HUB, type(file), bytes(file), "hub", Instant.now()));
        return view(p);
    }

    /** Покупатель: вложения к открытому спору. */
    @Transactional
    public OrderView.PhotoView disputeUpload(String number, UUID key, String sub, Integer lineNo, MultipartFile file) {
        PurchaseOrder o = service.requireOwned(number, key, sub);
        if (!disputes.existsByOrderIdAndStatus(o.getId(), DisputeStatus.OPEN)) {
            throw BusinessException.unprocessable("no_open_dispute", "Фото можно приложить к открытому спору");
        }
        if (photos.countByOrderIdAndKind(o.getId(), DISPUTE) >= MAX_DISPUTE) {
            throw BusinessException.unprocessable("too_many_photos", "К спору можно приложить до " + MAX_DISPUTE + " фото");
        }
        checkLine(o, lineNo);
        OrderPhoto p = photos.save(new OrderPhoto(o.getId(), lineNo, DISPUTE, type(file), bytes(file), "buyer", Instant.now()));
        o.log("Покупатель приложил фото к спору", null, "buyer", Instant.now());
        return view(p);
    }

    @Transactional(readOnly = true)
    public Photo forBuyer(String number, UUID key, String sub, long id) {
        return load(service.requireOwned(number, key, sub), id);
    }

    @Transactional(readOnly = true)
    public Photo forStaff(String number, long id) {
        return load(service.require(number), id);
    }

    private Photo load(PurchaseOrder o, long id) {
        OrderPhoto p = photos.findByIdAndOrderId(id, o.getId()).orElseThrow(() -> BusinessException.notFound("Фото"));
        return new Photo(p.getContentType(), p.getData());
    }

    private static OrderView.PhotoView view(OrderPhoto p) {
        return new OrderView.PhotoView(p.getId(), p.getLineNo(), p.getKind(), p.getCreatedAt());
    }

    private static void checkLine(PurchaseOrder o, Integer lineNo) {
        if (lineNo != null && (lineNo < 0 || lineNo >= o.getLines().size())) {
            throw BusinessException.unprocessable("bad_line", "Нет такой строки в заказе");
        }
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.unprocessable("photo_empty", "Выберите фото");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BusinessException.unprocessable("photo_too_big", "Фото больше 5 МБ");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BusinessException.unprocessable("photo_unreadable", "Не удалось прочитать файл");
        }
    }

    /** Тип определяем по сигнатуре файла, а не по заголовку браузера. */
    private static String type(MultipartFile file) {
        byte[] b = bytes(file);
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        throw BusinessException.unprocessable("photo_type", "Подходят JPEG, PNG или WebP");
    }
}
