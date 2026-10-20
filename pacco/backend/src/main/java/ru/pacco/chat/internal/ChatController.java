package ru.pacco.chat.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.buyers.BuyerProfiles;
import ru.pacco.chat.ChatService;
import ru.pacco.sellers.SellerDirectory;

@RestController
class ChatController {

    private final ChatService chat;
    private final SellerDirectory sellers;
    private final BuyerProfiles profiles;

    ChatController(ChatService chat, SellerDirectory sellers, BuyerProfiles profiles) {
        this.chat = chat;
        this.sellers = sellers;
        this.profiles = profiles;
    }

    record Start(long productId, String orderNumber, String text) {
    }

    record Text(String text) {
    }

    @PostMapping("/api/me/chats")
    @ResponseStatus(HttpStatus.CREATED)
    ChatService.Thread start(@AuthenticationPrincipal Jwt jwt, @RequestBody Start body) {
        return chat.start(jwt.getSubject(), name(jwt), body.productId(), body.orderNumber(), body.text());
    }

    @GetMapping("/api/me/chats")
    List<ChatService.ConversationView> mine(@AuthenticationPrincipal Jwt jwt) {
        return chat.buyerList(jwt.getSubject());
    }

    @GetMapping("/api/me/chats/{id}")
    ChatService.Thread myThread(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return chat.buyerThread(jwt.getSubject(), id);
    }

    @PostMapping("/api/me/chats/{id}/messages")
    ChatService.Thread myPost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Text body) {
        return chat.buyerPost(jwt.getSubject(), id, name(jwt), body.text());
    }

    @GetMapping("/api/seller/chats")
    List<ChatService.ConversationView> sellerList(@AuthenticationPrincipal Jwt jwt) {
        return chat.sellerList(sellers.requireActive(jwt.getSubject()).id());
    }

    @GetMapping("/api/seller/chats/{id}")
    ChatService.Thread sellerThread(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return chat.sellerThread(sellers.requireActive(jwt.getSubject()).id(), id);
    }

    @PostMapping("/api/seller/chats/{id}/messages")
    ChatService.Thread sellerPost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Text body) {
        return chat.sellerPost(sellers.requireActive(jwt.getSubject()).id(), id, body.text());
    }

    @GetMapping("/api/admin/chats")
    List<ChatService.ConversationView> staffList(@RequestParam(required = false) String q) {
        return chat.staffList(q);
    }

    @GetMapping("/api/admin/chats/{id}")
    ChatService.Thread staffThread(@PathVariable long id) {
        return chat.staffThread(id);
    }

    @PostMapping("/api/admin/chats/{id}/messages")
    ChatService.Thread staffPost(@PathVariable long id, @RequestBody Text body) {
        return chat.staffPost(id, body.text());
    }

    /** Имя покупателя в чате: «Анна С.». */
    private String name(Jwt jwt) {
        String n = profiles.get(jwt.getSubject(), null).name();
        if (n == null || n.isBlank()) {
            String given = jwt.getClaimAsString("given_name");
            String family = jwt.getClaimAsString("family_name");
            n = given != null ? given + (family != null ? " " + family : "") : jwt.getClaimAsString("preferred_username");
        }
        if (n == null || n.isBlank()) return "Покупатель";
        String[] parts = n.trim().split("\\s+");
        return parts.length > 1 ? parts[0] + " " + parts[1].charAt(0) + "." : parts[0];
    }
}
