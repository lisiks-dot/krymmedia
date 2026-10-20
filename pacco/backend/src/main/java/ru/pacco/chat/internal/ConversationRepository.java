package ru.pacco.chat.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByBuyerSubOrderByLastMessageAtDesc(String buyerSub);

    List<Conversation> findBySellerIdOrderByLastMessageAtDesc(Long sellerId);

    List<Conversation> findAllByOrderByLastMessageAtDesc(Pageable page);

    Optional<Conversation> findFirstByBuyerSubAndSellerIdAndProductId(String buyerSub, Long sellerId, Long productId);

    Optional<Conversation> findFirstByBuyerSubAndSellerIdIsNullAndProductId(String buyerSub, Long productId);
}
