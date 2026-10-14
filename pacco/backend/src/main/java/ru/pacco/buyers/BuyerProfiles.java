package ru.pacco.buyers;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.buyers.internal.BuyerProfile;
import ru.pacco.buyers.internal.BuyerProfileRepository;
import ru.pacco.shared.BusinessException;

/** Профиль покупателя с аккаунтом: данные для оформления по умолчанию и настройки уведомлений. */
@Service
public class BuyerProfiles {

    private final BuyerProfileRepository profiles;

    public BuyerProfiles(BuyerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public BuyerProfileView get(String sub, String tokenEmail) {
        return profiles.findById(sub).map(BuyerProfile::view).orElse(BuyerProfileView.empty(tokenEmail));
    }

    @Transactional
    public BuyerProfileView save(String sub, BuyerProfileView p) {
        String phone = p.phone() == null ? null : p.phone().replaceAll("\\D", "");
        if (phone != null && !phone.isEmpty() && phone.length() != 11) {
            throw BusinessException.unprocessable("bad_phone", "Телефон должен содержать 11 цифр");
        }
        BuyerProfile e = profiles.findById(sub).orElseGet(() -> new BuyerProfile(sub));
        e.update(blank(p.name()), blank(phone), blank(p.email()), p.pickupPointId(), p.notifySms(), p.notifyEmail(), Instant.now());
        return profiles.save(e).view();
    }

    /** После оформления заполняем пустые поля профиля — следующий заказ оформится быстрее. */
    @Transactional
    public void rememberCheckout(String sub, String name, String phone, String email, Long pickupPointId) {
        BuyerProfile e = profiles.findById(sub).orElseGet(() -> new BuyerProfile(sub));
        e.fillBlanks(name, phone, blank(email), pickupPointId, Instant.now());
        profiles.save(e);
    }

    /** Каналы уведомлений; без профиля — оба включены. */
    @Transactional(readOnly = true)
    public Channels channels(String sub) {
        if (sub == null) {
            return new Channels(true, true);
        }
        return profiles.findById(sub).map(b -> new Channels(b.view().notifySms(), b.view().notifyEmail()))
                .orElse(new Channels(true, true));
    }

    public record Channels(boolean sms, boolean email) {
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
