package ru.pacco.compliance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.compliance.internal.ComplianceRulesEntity;
import ru.pacco.compliance.internal.ComplianceRulesRepository;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;
import ru.pacco.shared.Money;

/**
 * Комплаенс-движок: санкции ЕС (порог цены за единицу), продэмбарго РФ, почтовые запреты.
 * Правила хранятся в БД и меняются без релиза.
 */
@Service
public class ComplianceService {

    private static final int RULES_ID = 1;
    private static final Pattern HS_CODE = Pattern.compile("\\d{8}");

    private final ComplianceRulesRepository repository;
    private final ApplicationEventPublisher events;

    public ComplianceService(ComplianceRulesRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Violation> check(ItemSpec item) {
        RulesView rules = rules();
        List<Violation> out = new ArrayList<>();
        String hs = item.hsCode() == null ? "" : item.hsCode().replaceAll("\\s", "");

        if (!HS_CODE.matcher(hs).matches()) {
            out.add(new Violation("C-02", "Нужен 8-значный код ТН ВЭД (CN)"));
        }
        if (rules.thresholdCategories().contains(item.category())
                && item.priceEurCents() >= rules.thresholdEurCents()) {
            out.add(new Violation("C-01", "Цена " + Money.eur(item.priceEurCents())
                    + " € за единицу не ниже порога " + Money.eur(rules.thresholdEurCents()) + " € (санкции ЕС)"));
        }
        if (startsWithAny(hs, rules.embargoPrefixes())) {
            out.add(new Violation("C-03", "Код " + hs.substring(0, Math.min(4, hs.length()))
                    + " входит в перечень продэмбарго РФ"));
        }
        if (startsWithAny(hs, rules.alcoholPrefixes())) {
            out.add(new Violation("C-04", "Алкоголь нельзя пересылать в посылках физлицам"));
        }
        if (item.aerosol()) {
            out.add(new Violation("C-04", "Аэрозоли и легковоспламеняющиеся составы не принимают к перевозке"));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public RulesView rules() {
        return toView(load());
    }

    @Transactional
    public RulesView updateRules(RulesUpdate update) {
        ComplianceRulesEntity e = load();
        e.setThresholdEurCents(update.thresholdEurCents());
        e.setThresholdCategories(update.thresholdCategories().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(",")));
        e.setEmbargoPrefixes(join(update.embargoPrefixes()));
        e.setAlcoholPrefixes(join(update.alcoholPrefixes()));
        e.setUpdatedAt(Instant.now());
        RulesView view = toView(repository.save(e));
        events.publishEvent(new ComplianceRulesChanged(view));
        return view;
    }

    private ComplianceRulesEntity load() {
        return repository.findById(RULES_ID)
                .orElseThrow(() -> new BusinessException(500, "rules_missing", "Правила комплаенса не заданы", List.of()));
    }

    private static RulesView toView(ComplianceRulesEntity e) {
        Set<Category> cats = EnumSet.noneOf(Category.class);
        split(e.getThresholdCategories()).forEach(c -> cats.add(Category.valueOf(c)));
        return new RulesView(e.getThresholdEurCents(), cats, split(e.getEmbargoPrefixes()),
                split(e.getAlcoholPrefixes()), e.getUpdatedAt());
    }

    private static boolean startsWithAny(String hs, List<String> prefixes) {
        return !hs.isEmpty() && prefixes.stream().anyMatch(hs::startsWith);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split("[,\\s]+")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String join(List<String> values) {
        return values.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
    }
}
