package se.meditrack.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.meditrack.dto.CreateOrderRequest;
import se.meditrack.dto.OrderResponse;
import se.meditrack.dto.UpdateOrderStatusRequest;
import se.meditrack.enums.OrderStatus;
import se.meditrack.service.OrderService;

import java.util.List;

/**
 * REST-endpoints för beställningsflödet.
 *
 * Statusövergångar exponeras som action-endpoints (POST /send, /confirm,
 * /deliver, /cancel) istället för PATCH med ny status i body. Motivet:
 * statusövergångar är verb med sidoeffekter och valideringsregler, inte
 * idempotenta attributuppdateringar. URL-stilen matchar Rails-traditionen
 * Medovia jobbar med. Inkonsekvensen med PATCH /threshold på StockController
 * är medveten — domänen skiljer mellan rena attribut och affärshändelser.
 *
 * Action-endpoints tar ingen body — verbet i URL:en är hela avsikten.
 * Under huven anropar alla samma OrderService.updateStatus som validerar
 * övergången mot OrderStateMachine (DRAFT→SENT→CONFIRMED→DELIVERED).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/send")
    public OrderResponse send(@PathVariable Long id) {
        return orderService.updateStatus(id, new UpdateOrderStatusRequest(OrderStatus.SENT));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable Long id) {
        return orderService.updateStatus(id, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));
    }

    @PostMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable Long id) {
        // Den ENDA action som har sidoeffekt på lager (uppdaterar saldon
        // per orderrad atomärt via OrderService.applyDeliveryToStock).
        return orderService.updateStatus(id, new UpdateOrderStatusRequest(OrderStatus.DELIVERED));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }
}