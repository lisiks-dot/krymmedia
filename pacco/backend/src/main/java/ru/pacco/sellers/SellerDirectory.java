package ru.pacco.sellers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.sellers.internal.Seller;
import ru.pacco.sellers.internal.SellerRepository;
import ru.pacco.shared.BusinessException;

/**
 * Продавцы. Учётная запись — пользователь Keycloak; продавец привязан к ней по {@code sub} токена.
 * Права продавца определяет статус в нашей базе, а не роль в Keycloak, поэтому одобрение — одно действие модератора.
 */
@Service
public class SellerDirectory {

    private final SellerRepository sellers;

    public SellerDirectory(SellerRepository sellers) {
        this.sellers = sellers;
    }

    @Transactional(readOnly = true)
    public Optional<SellerView> bySubject(String subject) {
        return sellers.findByKeycloakSub(subject).map(Seller::toView);
    }

    /** Продавец текущего пользователя; если его нет или он не одобрен — 403. */
    @Transactional(readOnly = true)
    public SellerView requireActive(String subject) {
        SellerView s = bySubject(subject).orElseThrow(() -> new BusinessException(403, "seller_missing",
                "Сначала подайте заявку продавца", List.of()));
        if (s.status() != SellerStatus.ACTIVE) {
            throw new BusinessException(403, "seller_not_active", "Заявка продавца ещё не одобрена", List.of());
        }
        return s;
    }

    @Transactional
    public SellerView apply(String subject, SellerApplication a) {
        Optional<Seller> existing = sellers.findByKeycloakSub(subject);
        if (existing.isPresent() && existing.get().getStatus() != SellerStatus.REJECTED) {
            throw BusinessException.unprocessable("already_applied", "Заявка уже подана");
        }
        sellers.findByPartitaIva(a.partitaIva())
                .filter(s -> !s.getKeycloakSub().equals(subject))
                .ifPresent(s -> {
                    throw BusinessException.unprocessable("piva_taken", "Компания с этой Partita IVA уже зарегистрирована");
                });
        Seller s = existing.orElseGet(() -> new Seller(subject));
        s.applyWith(a.legalName().trim(), a.partitaIva(), a.iban(), blankToNull(a.region()), a.contactEmail().trim());
        return sellers.save(s).toView();
    }

    @Transactional(readOnly = true)
    public List<SellerView> list(SellerStatus status) {
        List<Seller> all = status == null ? sellers.findAll(Sort.by("id")) : sellers.findByStatusOrderById(status);
        return all.stream().map(Seller::toView).toList();
    }

    @Transactional(readOnly = true)
    public SellerView get(long id) {
        return require(id).toView();
    }

    @Transactional
    public SellerView approve(long id) {
        Seller s = require(id);
        s.approve();
        return s.toView();
    }

    @Transactional
    public SellerView reject(long id, String reason) {
        Seller s = require(id);
        s.reject(reason == null || reason.isBlank() ? "Заявка отклонена модератором" : reason.trim());
        return s.toView();
    }

    private Seller require(long id) {
        return sellers.findById(id).orElseThrow(() -> BusinessException.notFound("Продавец"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
