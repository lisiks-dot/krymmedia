package ru.pacco.sourcing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ru.pacco.catalog.ProductCatalog;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;
import ru.pacco.sourcing.internal.FeedCsvParser;
import ru.pacco.sourcing.internal.Fetcher;
import ru.pacco.sourcing.internal.Hints;
import ru.pacco.sourcing.internal.ImportItem;
import ru.pacco.sourcing.internal.ImportItemRepository;
import ru.pacco.sourcing.internal.ImportJob;
import ru.pacco.sourcing.internal.ImportJobRepository;
import ru.pacco.sourcing.internal.RobotsPolicy;
import ru.pacco.sourcing.internal.ZalandoParser;

/**
 * Импорт товаров Zalando в каталог ilpacco. Модель — закупка площадкой: товар публикуется как товар оператора,
 * при заказе площадка выкупает его у ритейлера и проверяет на хабе.
 * <p>
 * Что сохраняем: бренд, название, цвет, цену, размеры в наличии, ссылку. Фото и описания поставщика
 * на витрину не попадают (авторские права): на ilpacco — фирменные иллюстрации и собственный текст.
 * Загрузка вежливая: robots.txt, честный User-Agent, пауза между запросами, без обхода защиты.
 */
@Service
public class SourcingService {

    public static final String ZALANDO = "ZALANDO";
    private static final Logger log = LoggerFactory.getLogger(SourcingService.class);
    private static final int MAX_PAGES = 20;

    public record JobView(long id, String source, String kind, String url, int maxItems, String status, int found,
                          int imported, int failed, String message, String startedBy, Instant createdAt, Instant finishedAt) {
    }

    public record ItemView(long id, String source, String url, String sku, String brand, String title, String color,
                           Long priceEurCents, Long suggestedPriceEurCents, List<String> sizes, boolean available,
                           String imageUrl, String status, Long productId, String productStatus, String rejectReason,
                           Category hintCategory, String hintHs, int hintWeight, String hintKind, Instant fetchedAt) {
    }

    /** Решение модератора. Код ТН ВЭД — 8 цифр, обязательно: от него зависит таможня. */
    public record Approve(String title, Category category, String hsCode, Integer weightGrams, BigDecimal markupPercent,
                          Long priceEurCents, List<String> sizes) {
    }

    private final ImportJobRepository jobs;
    private final ImportItemRepository items;
    private final Fetcher fetcher;
    private final RobotsPolicy robots;
    private final ProductCatalog catalog;
    private final SourcingProperties props;
    private final TransactionTemplate tx;

    public SourcingService(ImportJobRepository jobs, ImportItemRepository items, Fetcher fetcher, RobotsPolicy robots,
                           ProductCatalog catalog, SourcingProperties props, TransactionTemplate tx) {
        this.jobs = jobs;
        this.items = items;
        this.fetcher = fetcher;
        this.robots = robots;
        this.catalog = catalog;
        this.props = props;
        this.tx = tx;
    }

    /** Загрузка по адресу страницы Zalando: каталог, бренд, поиск или карточка товара. Работает в фоне. */
    public JobView startPage(String url, Integer maxItems, String user) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.unprocessable("bad_url", "Не похоже на адрес страницы");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith("zalando.it")) {
            throw BusinessException.unprocessable("bad_url", "Нужен адрес страницы https://www.zalando.it/…");
        }
        int max = Math.max(1, Math.min(maxItems == null ? 48 : maxItems, props.maxItems()));
        ImportJob job = jobs.save(new ImportJob(ZALANDO, "PAGE", uri.toString(), max, user));
        long id = job.getId();
        Thread.ofVirtual().name("import-" + id).start(() -> runPage(id, uri.toString(), max));
        return view(job);
    }

    /** Загрузка фида партнёрской программы (CSV). Работает сразу, без обращений к сайту. */
    public JobView importFeed(String csv, String user) {
        List<Scraped> rows;
        try {
            rows = FeedCsvParser.parse(csv);
        } catch (IllegalArgumentException e) {
            throw BusinessException.unprocessable("bad_feed", e.getMessage());
        }
        ImportJob job = jobs.save(new ImportJob(ZALANDO, "FEED", null, rows.size(), user));
        long id = job.getId();
        update(id, j -> j.found(rows.size()));
        for (Scraped s : rows) {
            try {
                upsert(id, s);
                update(id, ImportJob::imported);
            } catch (RuntimeException e) {
                update(id, j -> j.failed(s.url() + ": " + e.getMessage()));
            }
        }
        update(id, j -> j.finish("DONE", null));
        return job(id);
    }

    private void runPage(long jobId, String url, int max) {
        try {
            if (!robots.allowed(url)) {
                update(jobId, j -> j.finish("FAILED", "robots.txt поставщика запрещает загрузку этого адреса"));
                return;
            }
            List<String> links = new ArrayList<>();
            if (ZalandoParser.isProductUrl(url)) {
                links.add(url);
            } else {
                Set<String> seen = new LinkedHashSet<>();
                for (int p = 1; p <= MAX_PAGES && seen.size() < max; p++) {
                    String page = ZalandoParser.page(url, p);
                    if (!robots.allowed(page)) break;
                    int before = seen.size();
                    seen.addAll(ZalandoParser.productLinks(fetcher.get(page), page));
                    if (seen.size() == before) break;
                }
                links.addAll(seen.stream().limit(max).toList());
            }
            int found = links.size();
            update(jobId, j -> j.found(found));
            if (links.isEmpty()) {
                update(jobId, j -> j.finish("DONE", "На странице не нашлось карточек товаров — проверьте адрес"));
                return;
            }
            for (String link : links) {
                try {
                    if (!robots.allowed(link)) {
                        update(jobId, j -> j.failed(link + ": запрещено robots.txt"));
                        continue;
                    }
                    Scraped s = ZalandoParser.product(fetcher.get(link), link)
                            .orElseThrow(() -> new IllegalStateException("на странице нет данных товара"));
                    upsert(jobId, s);
                    update(jobId, ImportJob::imported);
                } catch (Fetcher.FetchException e) {
                    update(jobId, j -> j.failed(link + ": " + e.getMessage()));
                    if (e.status() == 403 || e.status() == 429) {
                        update(jobId, j -> j.finish("FAILED", "Поставщик ограничил доступ (" + e.status()
                                + "). Остановились, чтобы не нагружать сайт. Надёжный путь — фид партнёрской программы (CSV)"));
                        return;
                    }
                } catch (RuntimeException e) {
                    update(jobId, j -> j.failed(link + ": " + e.getMessage()));
                }
            }
            update(jobId, j -> j.finish("DONE", null));
        } catch (Fetcher.FetchException e) {
            update(jobId, j -> j.finish("FAILED", e.status() == 403 || e.status() == 429
                    ? "Поставщик ограничил доступ (" + e.status() + "). Надёжный путь — фид партнёрской программы (CSV)"
                    : e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("Импорт {} упал", jobId, e);
            update(jobId, j -> j.finish("FAILED", e.getMessage()));
        }
    }

    /** Новый товар — в очередь модерации; уже опубликованный — обновляем цену и наличие в каталоге. */
    private void upsert(Long jobId, Scraped s) {
        tx.executeWithoutResult(t -> {
            ImportItem item = items.findBySourceUrl(s.url()).orElse(null);
            if (item == null) {
                items.save(new ImportItem(jobId, ZALANDO, s));
                return;
            }
            item.apply(s);
            if (jobId != null) item.attachTo(jobId);
            if ("APPROVED".equals(item.getStatus())) pushOffer(item);
        });
    }

    private void pushOffer(ImportItem item) {
        if (item.getProductId() == null) return;
        Long price = salePrice(item, item.getMarkupPercent());
        catalog.updateOffer(item.getProductId(), price == null ? 0 : price, item.sizeList(), item.isAvailable() && price != null);
    }

    private void update(long jobId, java.util.function.Consumer<ImportJob> change) {
        tx.executeWithoutResult(t -> jobs.findById(jobId).ifPresent(change));
    }

    @Transactional
    public ItemView approve(long id, Approve a) {
        ImportItem item = items.findById(id).orElseThrow(() -> BusinessException.notFound("Товар импорта"));
        if ("APPROVED".equals(item.getStatus())) {
            throw BusinessException.unprocessable("already_approved", "Товар уже в каталоге");
        }
        String hs = a.hsCode() == null ? "" : a.hsCode().replaceAll("\\D", "");
        if (hs.length() != 8) {
            throw BusinessException.unprocessable("hs_required", "Укажите 8-значный код ТН ВЭД");
        }
        if (a.category() == null) throw BusinessException.unprocessable("category_required", "Выберите категорию");
        Hints.Hint hint = hint(item);
        int weight = a.weightGrams() == null || a.weightGrams() <= 0 ? hint.weightGrams() : a.weightGrams();
        BigDecimal markup = a.markupPercent() == null ? props.markupPercent() : a.markupPercent();
        Long price = a.priceEurCents() != null && a.priceEurCents() > 0 ? a.priceEurCents() : salePrice(item, markup);
        if (price == null) throw BusinessException.unprocessable("price_required", "У товара нет цены в евро — укажите цену");
        if (!item.isAvailable()) throw BusinessException.unprocessable("not_available", "Товара нет в наличии у поставщика");
        String title = a.title() == null || a.title().isBlank() ? item.getTitle() : a.title().trim();
        List<String> sizes = a.sizes() == null || a.sizes().isEmpty() ? item.sizeList() : a.sizes();
        String brand = item.getBrand() == null ? "Italia" : item.getBrand();
        String description = "Оригинальный товар бренда " + brand + (item.getColor() != null ? ", цвет: " + item.getColor() : "")
                + ". Выкупаем у официального ритейлера в Италии, проверяем и фотографируем на хабе в Милане.";
        long productId = catalog.publishOperatorProduct(new ProductCatalog.NewOperatorProduct(brand, title, a.category(), hs,
                price, weight, sizes, description));
        item.approve(productId, a.priceEurCents() != null && a.priceEurCents() > 0 ? null : markup);
        return view(item);
    }

    @Transactional
    public ItemView reject(long id, String reason) {
        ImportItem item = items.findById(id).orElseThrow(() -> BusinessException.notFound("Товар импорта"));
        if ("APPROVED".equals(item.getStatus())) {
            throw BusinessException.unprocessable("already_approved", "Товар уже в каталоге — снимите его в разделе «Карточки»");
        }
        item.reject(reason == null || reason.isBlank() ? "Не подходит для ilpacco" : reason.trim());
        return view(item);
    }

    @Transactional
    public ItemView reopen(long id) {
        ImportItem item = items.findById(id).orElseThrow(() -> BusinessException.notFound("Товар импорта"));
        if ("REJECTED".equals(item.getStatus())) item.reopen();
        return view(item);
    }

    /** Раз в сутки — цены и наличие опубликованных товаров. Вежливо: та же пауза между запросами. */
    @Scheduled(cron = "${pacco.sourcing.refresh-cron:0 30 4 * * *}", zone = "Europe/Moscow")
    public void refreshApproved() {
        List<Long> ids = items.findByStatusOrderByIdAsc("APPROVED").stream().map(ImportItem::getId).toList();
        for (Long id : ids) {
            try {
                refresh(id);
            } catch (Fetcher.FetchException e) {
                if (e.status() == 403 || e.status() == 429) {
                    log.warn("Обновление цен остановлено: поставщик ответил {}", e.status());
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("Не обновили товар импорта {}: {}", id, e.getMessage());
            }
        }
    }

    /** Обновить один товар с сайта поставщика сейчас. */
    public ItemView refresh(long id) {
        ImportItem item = items.findById(id).orElseThrow(() -> BusinessException.notFound("Товар импорта"));
        if (!robots.allowed(item.getSourceUrl())) {
            throw BusinessException.unprocessable("robots", "robots.txt поставщика запрещает загрузку");
        }
        Scraped s = ZalandoParser.product(fetcher.get(item.getSourceUrl()), item.getSourceUrl())
                .orElseThrow(() -> BusinessException.unprocessable("no_data", "На странице нет данных товара"));
        upsert(null, s);
        return tx.execute(t -> view(items.findById(id).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public List<JobView> jobs() {
        return jobs.findAllByOrderByIdDesc(PageRequest.of(0, 20)).stream().map(SourcingService::view).toList();
    }

    @Transactional(readOnly = true)
    public JobView job(long id) {
        return jobs.findById(id).map(SourcingService::view).orElseThrow(() -> BusinessException.notFound("Загрузка"));
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(String status, int limit) {
        return items.findByStatusOrderByIdDesc(status == null ? "NEW" : status, PageRequest.of(0, Math.min(Math.max(limit, 1), 500)))
                .stream().map(this::view).toList();
    }

    private Long salePrice(ImportItem item, BigDecimal markup) {
        if (item.getPriceEurCents() == null || !"EUR".equalsIgnoreCase(item.getCurrency())) return null;
        BigDecimal m = markup == null ? props.markupPercent() : markup;
        // наценка и округление вверх до 0,10 €
        long raw = BigDecimal.valueOf(item.getPriceEurCents()).multiply(BigDecimal.ONE.add(m.movePointLeft(2)))
                .setScale(0, RoundingMode.CEILING).longValue();
        return (raw + 9) / 10 * 10;
    }

    private static Hints.Hint hint(ImportItem i) {
        return Hints.of(i.getTitle(), i.getCategoryHint(), i.getSourceUrl());
    }

    private ItemView view(ImportItem i) {
        Hints.Hint h = hint(i);
        return new ItemView(i.getId(), i.getSource(), i.getSourceUrl(), i.getSku(), i.getBrand(), i.getTitle(), i.getColor(),
                i.getPriceEurCents(), salePrice(i, i.getMarkupPercent()), i.sizeList(), i.isAvailable(), i.getImageUrl(),
                i.getStatus(), i.getProductId(), i.getProductId() == null ? null : catalog.statusOf(i.getProductId()).orElse(null),
                i.getRejectReason(), h.category(), h.hsHeading(), h.weightGrams(), h.kind(), i.getFetchedAt());
    }

    private static JobView view(ImportJob j) {
        return new JobView(j.getId(), j.getSource(), j.getKind(), j.getUrl(), j.getMaxItems(), j.getStatus(), j.getFound(),
                j.getImported(), j.getFailed(), j.getMessage(), j.getStartedBy(), j.getCreatedAt(), j.getFinishedAt());
    }
}
