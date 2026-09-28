package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.AdvisorRequest;
import br.com.nexoestoque.dto.AdvisorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class AdvisorService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public AdvisorService(
            ObjectMapper objectMapper,
            @Value("${nexo.ai.api-key:}") String apiKey,
            @Value("${nexo.ai.model:gpt-5.6-luna}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public AdvisorResponse explain(AdvisorRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AdvisorResponse(
                    deterministicExplanation(request),
                    "rules",
                    "deterministic-fallback"
            );
        }

        try {
            String prompt = """
                    Você é um assistente de operações de estoque.
                    Explique em português, de forma curta e prática, o cenário abaixo.
                    Não altere nem recalcule os números recebidos.
                    Não invente dados, fornecedores, preços ou prazos.
                    Diga o risco, por que ele importa e a ação operacional mais segura.

                    Produto: %s
                    Risco: %s
                    Cobertura: %d dias
                    Compra sugerida pelo motor determinístico: %s unidades
                    Valor estimado em risco: R$ %s
                    Lead time normal: %d dias
                    Atraso simulado: %d dias
                    """.formatted(
                    request.productName(),
                    request.riskLevel(),
                    request.coverageDays(),
                    request.recommendedPurchase(),
                    request.estimatedValueAtRisk(),
                    request.supplierLeadTimeDays(),
                    request.supplierDelayDays()
            );

            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", prompt
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new AdvisorResponse(
                        deterministicExplanation(request),
                        "rules",
                        "deterministic-fallback"
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String explanation = extractOutputText(root);

            if (explanation.isBlank()) {
                explanation = deterministicExplanation(request);
                return new AdvisorResponse(explanation, "rules", "deterministic-fallback");
            }

            return new AdvisorResponse(explanation, "openai", model);
        } catch (Exception exception) {
            return new AdvisorResponse(
                    deterministicExplanation(request),
                    "rules",
                    "deterministic-fallback"
            );
        }
    }

    private String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return "";
        }

        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;

            for (JsonNode part : content) {
                if ("output_text".equals(part.path("type").asText())) {
                    return part.path("text").asText("");
                }
            }
        }
        return "";
    }

    private String deterministicExplanation(AdvisorRequest request) {
        String action = request.recommendedPurchase().signum() > 0
                ? "A prioridade é revisar a reposição sugerida e confirmar o prazo real do fornecedor antes de emitir o pedido."
                : "Não há necessidade imediata de compra pelo cenário atual; acompanhe consumo, validade e alterações no prazo do fornecedor.";

        return "O produto " + request.productName()
                + " está com risco " + request.riskLevel().toLowerCase()
                + " e cobertura estimada de " + request.coverageDays()
                + " dias. " + action
                + " Esta explicação usa apenas os números calculados pelo motor auditável.";
    }
}
