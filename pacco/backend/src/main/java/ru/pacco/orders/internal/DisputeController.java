package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.DisputeService;
import ru.pacco.orders.DisputeStatus;

@RestController
class DisputeController {

    private final DisputeService disputes;

    DisputeController(DisputeService disputes) {
        this.disputes = disputes;
    }

    @GetMapping("/api/admin/disputes")
    List<DisputeService.Case> list(@RequestParam(required = false) DisputeStatus status) {
        return disputes.list(status);
    }

    record Resolve(boolean refund, String resolution) {
    }

    @PostMapping("/api/admin/disputes/{id}/resolve")
    DisputeService.Case resolve(@PathVariable long id, @RequestBody Resolve body) {
        return disputes.resolve(id, body.refund(), body.resolution());
    }
}
