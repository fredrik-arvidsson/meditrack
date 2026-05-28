package se.meditrack.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.meditrack.dto.AdjustStockRequest;
import se.meditrack.dto.CreateStockItemRequest;
import se.meditrack.dto.StockItemResponse;
import se.meditrack.dto.UpdateThresholdRequest;
import se.meditrack.service.StockService;

import java.util.List;

/**
 * REST-endpoints för lagerhantering. Följer samma tunna mönster som
 * MedicationController — all logik bor i StockService.
 *
 * Filtrering via query-params (?belowThreshold=true) istället för
 * sub-resource. Konsistent mönster för framtida filter (form, search,
 * pagination) utan att uppfinna nya URL-prefix för varje dimension.
 *
 * Justeringar (POST /{id}/adjustments) modelleras som "skapa ny justering"
 * — varje anrop genererar en StockMovement i historiken, inte en
 * fältuppdatering. PATCH skulle felaktigt antyda idempotent
 * attribut-ändring. Threshold är däremot ren konfiguration → PATCH.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public List<StockItemResponse> findAll(
            @RequestParam(name = "belowThreshold", required = false, defaultValue = "false")
            boolean belowThreshold) {
        // Query-param-baserad filtrering. När fler filter tillkommer
        // (form, search) läggs de till som ytterligare @RequestParam.
        return belowThreshold
                ? stockService.findBelowThreshold()
                : stockService.findAll();
    }

    @PostMapping
    public ResponseEntity<StockItemResponse> create(
            @Valid @RequestBody CreateStockItemRequest request) {
        StockItemResponse created = stockService.createStockItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/threshold")
    public StockItemResponse updateThreshold(
            @PathVariable Long id,
            @Valid @RequestBody UpdateThresholdRequest request) {
        // PATCH — partiell uppdatering, inget historikspår. Threshold är
        // konfiguration, inte saldoförändring.
        return stockService.updateThreshold(id, request);
    }

    @PostMapping("/{id}/adjustments")
    public ResponseEntity<StockItemResponse> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody AdjustStockRequest request) {
        // POST — varje anrop skapar en StockMovement (immutabel historikrad).
        // Inte idempotent: två POST → två rader, även med samma payload.
        // 201 Created reflekterar att en justeringshändelse skapades.
        StockItemResponse result = stockService.adjustStock(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}