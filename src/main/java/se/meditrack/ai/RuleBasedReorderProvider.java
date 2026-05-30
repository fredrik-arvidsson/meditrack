package se.meditrack.ai;

import org.springframework.stereotype.Component;
import se.meditrack.entity.StockItem;

import java.util.List;

/**
 * Deterministisk fallback för påfyllningsförslag. Körs när Gemini inte är
 * konfigurerad (ingen nyckel) eller inte svarar — och fungerar därför helt
 * utan externa beroenden, vilket gör projektet körbart direkt vid klon.
 *
 * Heuristik: fyll till dubbla tröskelnivån.
 *   suggestedQuantity = (threshold × 2) − currentQuantity
 *
 * Motiv: threshold är nivån under vilken vi larmar. Att fylla precis till
 * threshold gör att nästa uttag genast utlöser ny varning; att fylla till
 * 2× ger en buffert så normal förbrukning inte omedelbart larmar igen.
 * Enkel och förklarbar — den nyanserade optimeringen (förbrukningstakt,
 * ledtider) är vad LLM-providern bidrar med när den är tillgänglig.
 *
 * Aldrig negativt: om en post av någon anledning redan ligger över 2×
 * threshold föreslås minst 1 (posten är ju med i listan = under threshold,
 * så detta är ett teoretiskt skyddsräcke snarare än ett reellt fall).
 */
@Component
public class RuleBasedReorderProvider implements ReorderSuggestionProvider {

    @Override
    public List<ReorderSuggestion> suggest(List<StockItem> lowStockItems) {
        return lowStockItems.stream()
                .map(this::suggestForItem)
                .toList();
    }

    private ReorderSuggestion suggestForItem(StockItem item) {
        int threshold = item.getThreshold();
        int current = item.getQuantity();
        int target = threshold * 2;
        int suggested = Math.max(1, target - current);

        String reason = String.format(
                "Saldo %d under tröskel %d. Föreslår påfyllning till 2× tröskel (%d).",
                current, threshold, target);

        return new ReorderSuggestion(
                item.getMedication().getId(),
                item.getMedication().getName(),
                current,
                threshold,
                suggested,
                reason);
    }

    @Override
    public String sourceLabel() {
        return "Regelbaserad fallback";
    }
}