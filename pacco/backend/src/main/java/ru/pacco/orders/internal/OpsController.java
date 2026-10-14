package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.OrderOperations;
import ru.pacco.orders.OrderView;

/** Рабочие места хаба и доставки (роль ops). */
@RestController
@RequestMapping("/api/ops")
class OpsController {

    private final OrderOperations ops;

    OpsController(OrderOperations ops) {
        this.ops = ops;
    }

    @GetMapping("/queues/{name}")
    List<OrderView> queue(@PathVariable String name) {
        return ops.queue(name);
    }

    record Accept(List<OrderOperations.LineCheck> lines) {
    }

    record Pack(int weightGrams) {
    }

    record Tracking(String tracking) {
    }

    record Code(String code) {
    }

    @PostMapping("/orders/{number}/accept")
    OrderView accept(@PathVariable String number, @RequestBody Accept body) {
        return ops.accept(number, body.lines());
    }

    @PostMapping("/orders/{number}/pack")
    OrderView pack(@PathVariable String number, @RequestBody Pack body) {
        return ops.pack(number, body.weightGrams());
    }

    @PostMapping("/orders/{number}/handover")
    OrderView handover(@PathVariable String number, @RequestBody Tracking body) {
        return ops.handover(number, body.tracking());
    }

    @PostMapping("/orders/{number}/customs")
    OrderView customs(@PathVariable String number) {
        return ops.customs(number);
    }

    @PostMapping("/orders/{number}/release")
    OrderView release(@PathVariable String number) {
        return ops.release(number);
    }

    @PostMapping("/orders/{number}/arrive")
    OrderView arrive(@PathVariable String number) {
        return ops.arrive(number);
    }

    @PostMapping("/orders/{number}/issue")
    OrderView issue(@PathVariable String number, @RequestBody Code body) {
        return ops.issue(number, body.code());
    }

    @PostMapping("/orders/{number}/unclaimed")
    OrderView unclaimed(@PathVariable String number) {
        return ops.unclaimed(number);
    }

    @PostMapping("/orders/{number}/complete")
    OrderView complete(@PathVariable String number) {
        return ops.complete(number);
    }
}
