package ru.pacco.pricing.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.pricing.PricingProperties;

@RestController
class PricingController {

    private final PricingProperties props;

    PricingController(PricingProperties props) {
        this.props = props;
    }

    /** Действующие тарифы — витрина показывает их в карточке и корзине. */
    @GetMapping("/api/pricing/tariffs")
    PricingProperties tariffs() {
        return props;
    }
}
