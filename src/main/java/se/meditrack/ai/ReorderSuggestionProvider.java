package se.meditrack.ai;

import se.meditrack.entity.StockItem;

import java.util.List;

/**
 * Föreslår påfyllningskvantiteter för lagerposter under sin threshold.
 *
 * Providern RESONERAR bara — den får faktiska lagerposter in (hämtade
 * deterministiskt från databasen) och föreslår kvantiteter ut. Den skapar
 * aldrig ordrar, rör aldrig saldon, och väljer aldrig vilka läkemedel som
 * är lågt lager — det avgörs av databasen, inte av en modell. Att bygga
 * en order av förslagen är ReorderSuggestionService ansvar.
 *
 * Två implementationer bakom interfacet:
 *  - GeminiReorderProvider: Medovias LLM (kräver API-nyckel via env)
 *  - RuleBasedReorderProvider: deterministisk fallback (körs utan nyckel,
 *    och när Gemini inte svarar)
 *
 * Samma mönster som CurrentUserProvider: beslutet om VILKEN implementation
 * isolerat bakom ett interface, så bytet är trivialt.
 */
public interface ReorderSuggestionProvider {

    /**
     * @param lowStockItems lagerposter under threshold (kan vara tom)
     * @return ett förslag per inkommande post, i samma ordning
     */
    List<ReorderSuggestion> suggest(List<StockItem> lowStockItems);

    /**
     * Källetikett för svaret ("Gemini (gemini-2.5-flash)" respektive
     * "Regelbaserad fallback") — så användaren och vi ser vad som
     * faktiskt genererade förslaget. Ärlighet om AI vs regel.
     */
    String sourceLabel();
}