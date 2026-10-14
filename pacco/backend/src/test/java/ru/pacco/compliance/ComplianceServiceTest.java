package ru.pacco.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import ru.pacco.compliance.internal.ComplianceRulesEntity;
import ru.pacco.compliance.internal.ComplianceRulesRepository;
import ru.pacco.shared.Category;

class ComplianceServiceTest {

    private final ComplianceRulesRepository repo = mock(ComplianceRulesRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ComplianceService service = new ComplianceService(repo, events);

    @BeforeEach
    void rules() {
        when(repo.findById(1)).thenReturn(Optional.of(ComplianceRulesEntity.of(29000,
                "BEAUTY,CLOTHES,HOME,SHOES", "02,03,04,07,08,1601", "2203,2204,2205,2206,2208")));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void bootsOverThresholdViolateEuSanctions() {
        assertThat(codes(new ItemSpec(Category.SHOES, "64039113", 34000, false))).containsExactly("C-01");
    }

    @Test
    void cheeseIsUnderEmbargo() {
        assertThat(codes(new ItemSpec(Category.FOOD, "04069021", 3200, false))).containsExactly("C-03");
    }

    @Test
    void wineIsAlcohol() {
        assertThat(codes(new ItemSpec(Category.FOOD, "22042138", 2400, false))).containsExactly("C-04");
    }

    @Test
    void missingCodeAndAerosolAreReported() {
        assertThat(codes(new ItemSpec(Category.BEAUTY, null, 2400, true))).containsExactly("C-02", "C-04");
    }

    @Test
    void ordinaryLoafersPass() {
        assertThat(service.check(new ItemSpec(Category.SHOES, "64039993", 15800, false))).isEmpty();
    }

    @Test
    void foodIsNotLimitedByPriceThreshold() {
        assertThat(service.check(new ItemSpec(Category.FOOD, "15092000", 50000, false))).isEmpty();
    }

    @Test
    void updatingRulesPublishesEvent() {
        RulesView v = service.updateRules(new RulesUpdate(25000, Set.of(Category.SHOES), List.of("04"), List.of("2204")));
        assertThat(v.thresholdEurCents()).isEqualTo(25000);
        assertThat(v.thresholdCategories()).containsExactly(Category.SHOES);
        verify(events).publishEvent(any(ComplianceRulesChanged.class));
    }

    private List<String> codes(ItemSpec spec) {
        return service.check(spec).stream().map(Violation::code).toList();
    }
}
