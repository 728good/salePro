package io.github.good728.sale.app.web;

import io.github.good728.sale.app.domain.Entities.Program;
import io.github.good728.sale.app.payment.PaymentStrategy.PaymentResult;
import io.github.good728.sale.app.service.CaptchaService;
import io.github.good728.sale.app.service.OrderService;
import io.github.good728.sale.app.service.PaymentService;
import io.github.good728.sale.app.service.ProgramService;
import io.github.good728.sale.app.service.SeatInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ProgramService programs;
    private final SeatInventoryService inventory;
    private final CaptchaService captchas;
    private final OrderService orders;
    private final PaymentService payments;

    public ApiController(ProgramService programs, SeatInventoryService inventory,
                         CaptchaService captchas, OrderService orders, PaymentService payments) {
        this.programs = programs;
        this.inventory = inventory;
        this.captchas = captchas;
        this.orders = orders;
        this.payments = payments;
    }

    @GetMapping("/programs")
    public Object search(@RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long size) {
        return programs.search(keyword, page, size);
    }

    @GetMapping("/programs/{id}")
    public ResponseEntity<Program> detail(@PathVariable Long id) {
        return ResponseEntity.ofNullable(programs.detail(id));
    }

    @PostMapping("/programs/{id}/inventory/warm")
    public Map<String, Integer> warm(@PathVariable Long id) {
        return Map.of("availableSeats", inventory.warm(id));
    }

    @GetMapping("/captcha")
    public CaptchaService.Captcha captcha() {
        return captchas.create();
    }

    @PostMapping("/orders/reserve")
    public Map<String, String> reserve(@Valid @RequestBody ReserveRequest request) {
        return Map.of("orderNo", orders.reserve(request.userId(), request.programId(),
                request.seatIds(), request.paymentType()), "status", "QUEUED");
    }

    @GetMapping("/orders/{orderNo}")
    public ResponseEntity<?> order(@PathVariable String orderNo) {
        return ResponseEntity.ofNullable(orders.find(orderNo));
    }

    @PostMapping("/orders/{orderNo}/pay")
    public PaymentResult pay(@PathVariable String orderNo) {
        return payments.pay(orderNo);
    }

    public record ReserveRequest(@NotNull Long userId, @NotNull Long programId,
                                 @NotEmpty List<@NotNull Long> seatIds,
                                 @NotBlank String paymentType) {}
}
