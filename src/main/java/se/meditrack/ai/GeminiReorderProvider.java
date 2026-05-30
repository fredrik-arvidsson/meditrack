package se.meditrack.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import se.meditrack.entity.StockItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini-driven påfyllningsagent. Ringer Googles Gemini-API direkt och låter
 * modellen föreslå kvantiteter utifrån faktisk lagerdata.
 *
 * RESILIENS ÄR INBYGGD: providern wrappar RuleBasedReorderProvider som
 * fallback. Saknas API-nyckel, eller failar anropet (timeout, fel, trasig
 * JSON) → faller vi tillbaka på den deterministiska regeln. Appen fungerar
 * därför ALLTID, med eller utan Gemini. Samma idé som CurrentUserProvider:
 * ett beslut isolerat, en tydlig fallback-väg.
 *
 * @Primary gör att Spring väljer DENNA som ReorderSuggestionProvider när
 * ReorderSuggestionService injicerar interfacet — trots att RuleBased också
 * är en böna. RuleBased injiceras hit in som intern fallback.
 *
 * SÄKERHET: modellen får faktisk lagerdata (medicationId + saldo + threshold)
 * och returnerar BARA en kvantitet per post. Den kan inte hitta på vilka
 * läkemedel som finns — vi mappar dess svar tillbaka mot de poster vi skickade,
 * och okända id ignoreras. Saldon/läkemedel kommer alltid från DB.
 */
@Component
@Primary
public class GeminiReorderProvider implements ReorderSuggestionProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiReorderProvider.class);

    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RuleBasedReorderProvider fallback;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Nyckel + modell läses från miljön. Saknas nyckeln → vi går direkt
    // på fallbacken (ingen krasch, ingen tom funktion).
    private final String apiKey = System.getenv("GEMINI_API_KEY");
    private final String model = System.getenv().getOrDefault(
            "GEMINI_MODEL", "gemini-2.5-flash");

    // Spårar vilken väg senaste anropet tog, för sourceLabel().
    private volatile boolean lastCallUsedFallback = false;

    public GeminiReorderProvider(RuleBasedReorderProvider fallback) {
        this.fallback = fallback;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ReorderSuggestion> suggest(List<StockItem> lowStockItems) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("GEMINI_API_KEY saknas — använder regelbaserad fallback.");
            lastCallUsedFallback = true;
            return fallback.suggest(lowStockItems);
        }

        try {
            List<ReorderSuggestion> result = callGemini(lowStockItems);
            lastCallUsedFallback = false;
            return result;
        } catch (Exception e) {
            // Pastoralens läxa: hellre en trygg regel än ett påhittat svar.
            log.warn("Gemini-anrop misslyckades ({}) — faller tillbaka på regel.",
                    e.getMessage());
            lastCallUsedFallback = true;
            return fallback.suggest(lowStockItems);
        }
    }

    private List<ReorderSuggestion> callGemini(List<StockItem> lowStockItems) throws Exception {
        String prompt = buildPrompt(lowStockItems);

        // Gemini generateContent-format. responseMimeType tvingar JSON-svar.
        String requestBody = objectMapper.writeValueAsString(
                new GeminiRequest(prompt));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        String url = API_BASE + model + ":generateContent";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(url, request, String.class);
        return parseResponse(response, lowStockItems);
    }

    /**
     * Bygger prompten. Vi ger modellen exakt de poster den får föreslå för,
     * och ber om strikt JSON tillbaka. Tydliga instruktioner om att aldrig
     * hitta på läkemedel och att returnera positiva heltal.
     */
    private String buildPrompt(List<StockItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du är en lagerassistent på en svensk vårdenhet. ");
        sb.append("Föreslå påfyllningskvantitet för varje läkemedel under tröskelnivå. ");
        sb.append("Ta hänsyn till hur långt under tröskeln saldot ligger. ");
        sb.append("Svara ENDAST med en JSON-array, inget annat. ");
        sb.append("Varje element: {\"medicationId\": <id>, \"suggestedQuantity\": <positivt heltal>, \"reason\": \"<kort motivering på svenska>\"}. ");
        sb.append("Använd EXAKT de medicationId som ges. Hitta aldrig på nya. ");
        sb.append("Läkemedel under tröskel:\n");
        for (StockItem item : items) {
            sb.append(String.format(
                    "- medicationId %d: %s, saldo %d, tröskel %d\n",
                    item.getMedication().getId(),
                    item.getMedication().getName(),
                    item.getQuantity(),
                    item.getThreshold()));
        }
        return sb.toString();
    }

    /**
     * Parsar Gemini-svaret defensivt. Gemini svarar med en struktur där texten
     * ligger i candidates[0].content.parts[0].text — och den texten är vår
     * JSON-array. Vi plockar ut den, parsar, och mappar mot de poster vi
     * skickade (okända id ignoreras). Allt som inte håller → exception →
     * fallback i suggest().
     */
    private List<ReorderSuggestion> parseResponse(String response,
                                                  List<StockItem> items) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        String text = root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();

        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Tomt svar från Gemini");
        }

        // Gemini kan ibland linda JSON i markdown-fences trots instruktionen.
        String cleaned = text.replaceAll("```json", "")
                .replaceAll("```", "").trim();

        JsonNode array = objectMapper.readTree(cleaned);
        if (!array.isArray()) {
            throw new IllegalStateException("Förväntade JSON-array från Gemini");
        }

        List<ReorderSuggestion> result = new ArrayList<>();
        for (JsonNode node : array) {
            long medId = node.path("medicationId").asLong(-1);
            int qty = node.path("suggestedQuantity").asInt(-1);
            String reason = node.path("reason").asText("");

            // Mappa mot de poster vi faktiskt skickade. Okänt id → ignorera
            // (modellen får inte introducera läkemedel).
            StockItem match = items.stream()
                    .filter(i -> i.getMedication().getId() == medId)
                    .findFirst()
                    .orElse(null);

            if (match == null || qty <= 0) {
                continue;
            }

            result.add(new ReorderSuggestion(
                    match.getMedication().getId(),
                    match.getMedication().getName(),
                    match.getQuantity(),
                    match.getThreshold(),
                    qty,
                    reason.isBlank() ? "Föreslaget av AI" : reason));
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Inga giltiga förslag i Gemini-svaret");
        }
        return result;
    }

    @Override
    public String sourceLabel() {
        return lastCallUsedFallback
                ? "Regelbaserad fallback (Gemini ej tillgänglig)"
                : "Gemini (" + model + ")";
    }

    // Minimal request-struktur för Gemini generateContent.
    private record GeminiRequest(Content[] contents, GenerationConfig generationConfig) {
        GeminiRequest(String prompt) {
            this(
                    new Content[]{ new Content(new Part[]{ new Part(prompt) }) },
                    new GenerationConfig("application/json")
            );
        }
    }

    private record Content(Part[] parts) {}
    private record Part(String text) {}
    private record GenerationConfig(String responseMimeType) {}
}
