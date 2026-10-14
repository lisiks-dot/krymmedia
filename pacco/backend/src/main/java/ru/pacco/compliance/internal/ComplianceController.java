package ru.pacco.compliance.internal;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.compliance.ComplianceService;
import ru.pacco.compliance.ItemSpec;
import ru.pacco.compliance.RulesUpdate;
import ru.pacco.compliance.RulesView;
import ru.pacco.compliance.Violation;

@RestController
class ComplianceController {

    private final ComplianceService service;

    ComplianceController(ComplianceService service) {
        this.service = service;
    }

    /** Проверка товара до публикации — доступна продавцам и витрине. */
    @PostMapping("/api/compliance/check")
    CheckResult check(@Valid @RequestBody ItemSpec item) {
        List<Violation> v = service.check(item);
        return new CheckResult(v.isEmpty(), v);
    }

    @GetMapping("/api/admin/compliance/rules")
    RulesView rules() {
        return service.rules();
    }

    @PutMapping("/api/admin/compliance/rules")
    RulesView update(@Valid @RequestBody RulesUpdate update) {
        return service.updateRules(update);
    }

    record CheckResult(boolean passed, List<Violation> violations) {
    }
}
