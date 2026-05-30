package se.meditrack.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meditrack.dto.CreateOrderRequest;
import se.meditrack.dto.OrderLineRequest;
import se.meditrack.dto.OrderResponse;
import se.meditrack.entity.StockItem;
import se.meditrack.exception.ValidationException;
import se.meditrack.repository.StockItemRepository;
import se.meditrack.security.CurrentUserProvider;
import se.meditrack.service.OrderService;

import java.util.List;

/**
 * AI-agent för påfyllning: läser lågt lager → föreslår kvantiteter →
 * skapar ett DRAFT-orderutkast som en människa måste granska och skicka.
 *
 * Säkerhetsgränserna är hela poängen:
 *  - Agenten SLUTAR vid DRAFT. Den skapar aldrig en skickad order.
 *    En människa granskar och skickar — separation of duties, även för AI.
 *  - Vilka läkemedel som är lågt lager och deras saldon hämtas från
 *    DATABASEN (findBelowThreshold), aldrig från modellen. Providern får
 *    bara föreslå kvantiteter; den kan inte hitta på vad som finns.
 *  - Förslagen VALIDERAS innan de blir en order: positiv kvantitet och
 *    medicationId som inte är null. Och även om något slank igenom skulle
 *    createOrder och Bean Validation avvisa det (tre lager skydd).
 *  - Ordern byggs via OrderService.createOrder — ingen ny skrivväg, samma
 *    DRAFT-, tenant- och valideringslogik som allt annat.
 */
@Service
public class ReorderSuggestionService {

    private final StockItemRepository stockItemRepository;
    private final CurrentUserProvider currentUser;
    private final OrderService orderService;
    private final ReorderSuggestionProvider provider;

    public ReorderSuggestionService(StockItemRepository stockItemRepository,
                                    CurrentUserProvider currentUser,
                                    OrderService orderService,
                                    ReorderSuggestionProvider provider) {
        this.stockItemRepository = stockItemRepository;
        this.currentUser = currentUser;
        this.orderService = orderService;
        this.provider = provider;
    }

    /**
     * Genererar ett påfyllningsförslag och skapar ett DRAFT-utkast av det.
     * Transaktionellt: lazy-relationer (medication) laddas innanför
     * sessionen när providern och mappningen rör dem (samma mönster som
     * OrderService 14.1).
     */
    @Transactional
    public ReorderResult suggestAndCreateDraft() {
        Long careUnitId = currentUser.getCurrentCareUnitId();

        // Deterministiskt: VILKA poster som är lågt lager kommer från DB.
        List<StockItem> lowStock =
                stockItemRepository.findBelowThreshold(careUnitId);

        if (lowStock.isEmpty()) {
            // Inget att fylla på — ingen order skapas. Ärligt tomt svar
            // hellre än en tom order.
            return new ReorderResult(
                    null,
                    List.of(),
                    provider.sourceLabel(),
                    "Inga läkemedel under tröskelnivå. Inget utkast skapades.");
        }

        // Providern RESONERAR: föreslår kvantiteter för de poster vi gav den.
        List<ReorderSuggestion> suggestions = provider.suggest(lowStock);

        // Validera innan vi bygger något. Förslag som inte håller avvisas.
        List<OrderLineRequest> lines = suggestions.stream()
                .filter(this::isValid)
                .map(s -> new OrderLineRequest(
                        s.medicationId(),
                        s.suggestedQuantity(),
                        "AI-förslag: " + s.reason()))
                .toList();

        if (lines.isEmpty()) {
            throw new ValidationException(
                    "Inga giltiga påfyllningsförslag kunde genereras.");
        }

        // Bygg DRAFT via befintlig affärslogik — ingen genväg.
        CreateOrderRequest request = new CreateOrderRequest(
                lines,
                "Automatiskt påfyllningsutkast (" + provider.sourceLabel() + ")");
        OrderResponse draft = orderService.createOrder(request);

        return new ReorderResult(
                draft,
                suggestions,
                provider.sourceLabel(),
                "Utkast skapat med " + lines.size() + " rad(er). Granska och skicka manuellt.");
    }

    /**
     * Ett förslag är giltigt om kvantiteten är positiv och medicationId
     * finns. medicationId behöver inte kontrolleras mot DB här — det kom
     * från lowStock som vi hämtade, och createOrder verifierar dessutom att
     * läkemedlet finns i tenanten.
     */
    private boolean isValid(ReorderSuggestion s) {
        return s.suggestedQuantity() > 0 && s.medicationId() != null;
    }
}