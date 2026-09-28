package br.com.nexoestoque.service;

import br.com.nexoestoque.dto.AdvisorChatResponse;
import br.com.nexoestoque.dto.AdvisorRequest;
import br.com.nexoestoque.dto.AdvisorResponse;
import br.com.nexoestoque.model.BlindInventorySession;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.StockBatch;
import br.com.nexoestoque.repository.BlindInventoryProcedureRepository;
import br.com.nexoestoque.repository.ProductProcedureRepository;
import br.com.nexoestoque.repository.StockBatchProcedureRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AdvisorService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final ProductProcedureRepository productRepository;
    private final StockBatchProcedureRepository batchRepository;
    private final BlindInventoryProcedureRepository inventoryRepository;

    public AdvisorService(
            ObjectMapper objectMapper,
            ProductProcedureRepository productRepository,
            StockBatchProcedureRepository batchRepository,
            BlindInventoryProcedureRepository inventoryRepository,
            @Value("${nexo.ai.api-key:}") String apiKey,
            @Value("${nexo.ai.model:gpt-5.6-luna}") String model
    ) {
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.inventoryRepository = inventoryRepository;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public AdvisorResponse explain(AdvisorRequest request) {
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

        Optional<String> answer = callOpenAi(prompt);
        if (answer.isPresent()) {
            return new AdvisorResponse(answer.get(), "openai", model);
        }

        return new AdvisorResponse(
                deterministicExplanation(request),
                "rules",
                "deterministic-fallback"
        );
    }

    public AdvisorChatResponse chat(String question) {
        try {
            List<Product> products = productRepository.findAll().stream()
                    .filter(Product::active)
                    .toList();
            List<StockBatch> batches = batchRepository.findBatches(null);
            List<BlindInventorySession> sessions = inventoryRepository.findAll();

            InventorySnapshot snapshot = buildSnapshot(products, batches, sessions);
            String prompt = buildChatPrompt(question, snapshot);

            Optional<String> answer = callOpenAi(prompt);
            if (answer.isPresent()) {
                return new AdvisorChatResponse(
                        answer.get(),
                        "openai",
                        model,
                        Instant.now()
                );
            }

            return new AdvisorChatResponse(
                    deterministicChat(question, snapshot),
                    "rules",
                    "deterministic-fallback",
                    Instant.now()
            );
        } catch (Exception exception) {
            return new AdvisorChatResponse(
                    "Não consegui consultar a base operacional agora. Tente novamente quando o indicador API + MySQL estiver operacional.",
                    "unavailable",
                    "database-unavailable",
                    Instant.now()
            );
        }
    }

    private InventorySnapshot buildSnapshot(
            List<Product> products,
            List<StockBatch> batches,
            List<BlindInventorySession> sessions
    ) {
        BigDecimal totalStock = products.stream()
                .map(Product::currentStock)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Product> critical = products.stream()
                .filter(product -> number(product.currentStock()).compareTo(number(product.minimumStock())) < 0)
                .sorted(Comparator.comparing(
                        product -> number(product.minimumStock()).subtract(number(product.currentStock())),
                        Comparator.reverseOrder()
                ))
                .toList();

        long outOfStock = products.stream()
                .filter(product -> number(product.currentStock()).signum() <= 0)
                .count();

        List<StockBatch> expiryRisk = batches.stream()
                .filter(batch -> batch.quantity() != null && batch.quantity().signum() > 0)
                .filter(batch -> batch.daysToExpiry() != null && batch.daysToExpiry() <= 30)
                .sorted(Comparator.comparing(StockBatch::daysToExpiry))
                .toList();

        BigDecimal expiryRiskValue = expiryRisk.stream()
                .map(batch -> number(batch.quantity()).multiply(number(batch.unitCost())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BlindInventorySession> openInventories = sessions.stream()
                .filter(session -> "OPEN".equalsIgnoreCase(session.status()))
                .sorted(Comparator.comparing(BlindInventorySession::startedAt))
                .toList();

        BlindInventorySession latestClosed = sessions.stream()
                .filter(session -> "CLOSED".equalsIgnoreCase(session.status()))
                .filter(session -> session.closedAt() != null)
                .max(Comparator.comparing(BlindInventorySession::closedAt))
                .orElse(null);

        Double inventoryAccuracy = null;
        if (latestClosed != null && latestClosed.countedItems() > 0) {
            inventoryAccuracy = Math.max(
                    0,
                    ((latestClosed.countedItems() - latestClosed.divergentItems())
                            * 100.0) / latestClosed.countedItems()
            );
        }

        return new InventorySnapshot(
                products,
                batches,
                critical,
                expiryRisk,
                openInventories,
                latestClosed,
                totalStock,
                outOfStock,
                expiryRiskValue,
                inventoryAccuracy
        );
    }

    private String buildChatPrompt(String question, InventorySnapshot snapshot) {
        StringBuilder context = new StringBuilder();
        context.append("RESUMO OPERACIONAL\n");
        context.append("- Produtos ativos: ").append(snapshot.products().size()).append("\n");
        context.append("- Unidades em estoque: ").append(decimal(snapshot.totalStock())).append("\n");
        context.append("- Produtos abaixo do mínimo: ").append(snapshot.critical().size()).append("\n");
        context.append("- Produtos sem estoque: ").append(snapshot.outOfStock()).append("\n");
        context.append("- Lotes vencidos ou com até 30 dias: ").append(snapshot.expiryRisk().size()).append("\n");
        context.append("- Valor de custo nos lotes de risco: R$ ").append(decimal(snapshot.expiryRiskValue())).append("\n");

        if (snapshot.inventoryAccuracy() != null) {
            context.append("- Precisão do último inventário: ")
                    .append(String.format(Locale.US, "%.1f", snapshot.inventoryAccuracy()))
                    .append("%\n");
        } else {
            context.append("- Precisão do último inventário: sem inventário fechado\n");
        }

        context.append("\nPRODUTOS\n");
        snapshot.products().stream()
                .sorted(Comparator.comparing(Product::name))
                .limit(80)
                .forEach(product -> context.append("- ")
                        .append(product.name())
                        .append(" | SKU ").append(product.sku())
                        .append(" | categoria ").append(product.category())
                        .append(" | saldo ").append(decimal(number(product.currentStock())))
                        .append(" | mínimo ").append(decimal(number(product.minimumStock())))
                        .append("\n"));

        context.append("\nLOTES COM MAIOR RISCO\n");
        snapshot.expiryRisk().stream()
                .limit(20)
                .forEach(batch -> context.append("- ")
                        .append(batch.productName())
                        .append(" | lote ").append(batch.lotCode())
                        .append(" | saldo ").append(decimal(number(batch.quantity())))
                        .append(" | validade ").append(batch.expiresAt())
                        .append(" | dias ").append(batch.daysToExpiry())
                        .append(" | posição FEFO ").append(batch.fefoPosition())
                        .append("\n"));

        context.append("\nINVENTÁRIOS ABERTOS\n");
        if (snapshot.openInventories().isEmpty()) {
            context.append("- Nenhum inventário aberto\n");
        } else {
            snapshot.openInventories().stream()
                    .limit(10)
                    .forEach(session -> context.append("- ")
                            .append(session.name())
                            .append(" | contados ").append(session.countedItems())
                            .append(" | iniciado em ").append(session.startedAt())
                            .append("\n"));
        }

        return """
                Você é o Assistente Nexo, especializado em operação de estoque.
                Responda em português brasileiro, de forma objetiva e profissional.
                Use somente o snapshot operacional abaixo.
                Não invente produtos, quantidades, preços, fornecedores, prazos ou causas.
                Não execute movimentações e não diga que executou ações.
                Quando sugerir uma ação, deixe claro que é uma recomendação operacional.
                Para números de estoque, validade e inventário, trate o snapshot como fonte de verdade.
                Se a pergunta exigir um dado que não existe no snapshot, diga explicitamente que o dado não está disponível.
                Priorize respostas curtas, com no máximo 6 parágrafos ou itens.

                SNAPSHOT:
                %s

                PERGUNTA:
                %s
                """.formatted(context, question);
    }

    private String deterministicChat(String question, InventorySnapshot snapshot) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);

        Optional<Product> mentionedProduct = snapshot.products().stream()
                .filter(product -> normalized.contains(product.name().toLowerCase(Locale.ROOT))
                        || normalized.contains(product.sku().toLowerCase(Locale.ROOT)))
                .findFirst();

        if (mentionedProduct.isPresent()) {
            Product product = mentionedProduct.get();
            BigDecimal stock = number(product.currentStock());
            BigDecimal minimum = number(product.minimumStock());
            String status = stock.compareTo(minimum) < 0 ? "abaixo do mínimo" : "acima do mínimo";

            List<StockBatch> productBatches = snapshot.batches().stream()
                    .filter(batch -> batch.productId().equals(product.id()))
                    .filter(batch -> number(batch.quantity()).signum() > 0)
                    .sorted(Comparator.comparing(
                            batch -> batch.daysToExpiry() == null ? Integer.MAX_VALUE : batch.daysToExpiry()
                    ))
                    .toList();

            String batchText = productBatches.isEmpty()
                    ? "Não há lote com saldo registrado para esse produto."
                    : "O próximo lote na ordem FEFO é " + productBatches.getFirst().lotCode()
                    + (productBatches.getFirst().expiresAt() == null
                    ? ", sem validade informada."
                    : ", com validade em " + productBatches.getFirst().expiresAt() + ".");

            return product.name() + " está com saldo de " + decimal(stock)
                    + " unidades e estoque mínimo de " + decimal(minimum)
                    + ", portanto está " + status + ". " + batchText;
        }

        if (normalized.contains("crític") || normalized.contains("ruptura") || normalized.contains("repor")) {
            if (snapshot.critical().isEmpty()) {
                return "Não há produtos abaixo do estoque mínimo no snapshot atual.";
            }

            StringBuilder answer = new StringBuilder("Produtos abaixo do estoque mínimo: ");
            snapshot.critical().stream().limit(8).forEach(product -> answer
                    .append(product.name())
                    .append(" (saldo ").append(decimal(number(product.currentStock())))
                    .append(", mínimo ").append(decimal(number(product.minimumStock())))
                    .append("); "));
            return answer.toString().replaceAll("; $", ".");
        }

        if (normalized.contains("valid") || normalized.contains("venc") || normalized.contains("fefo") || normalized.contains("lote")) {
            if (snapshot.expiryRisk().isEmpty()) {
                return "Não há lotes vencidos ou com até 30 dias para vencer no snapshot atual.";
            }

            StringBuilder answer = new StringBuilder("Lotes que exigem atenção por validade: ");
            snapshot.expiryRisk().stream().limit(8).forEach(batch -> answer
                    .append(batch.productName())
                    .append(" / ").append(batch.lotCode())
                    .append(" (").append(batch.daysToExpiry() < 0
                            ? Math.abs(batch.daysToExpiry()) + " dias vencido"
                            : batch.daysToExpiry() + " dias")
                    .append("); "));
            return answer.toString().replaceAll("; $", ".");
        }

        if (normalized.contains("invent")) {
            if (!snapshot.openInventories().isEmpty()) {
                BlindInventorySession session = snapshot.openInventories().getFirst();
                return "Há um inventário aberto: " + session.name()
                        + ", com " + session.countedItems() + " itens já contados. "
                        + "O estoque do sistema continua oculto durante a contagem cega.";
            }

            if (snapshot.latestClosed() != null && snapshot.inventoryAccuracy() != null) {
                return "Não há inventário aberto. O último inventário fechado teve precisão de "
                        + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", snapshot.inventoryAccuracy())
                        + "% e " + snapshot.latestClosed().divergentItems() + " divergências.";
            }

            return "Não há inventário aberto nem inventário fechado suficiente para calcular precisão.";
        }

        if (normalized.contains("estoque") || normalized.contains("saldo") || normalized.contains("resumo")) {
            return "O estoque possui " + snapshot.products().size()
                    + " produtos ativos e " + decimal(snapshot.totalStock())
                    + " unidades no total. Há " + snapshot.critical().size()
                    + " produtos abaixo do mínimo, " + snapshot.outOfStock()
                    + " sem estoque e " + snapshot.expiryRisk().size()
                    + " lotes vencidos ou com até 30 dias para vencer.";
        }

        return "Posso consultar o snapshot real do Nexo sobre estoque crítico, saldos, lotes, validade, FEFO e inventários. "
                + "No momento há " + snapshot.critical().size() + " produtos abaixo do mínimo e "
                + snapshot.expiryRisk().size() + " lotes em risco de validade.";
    }

    private Optional<String> callOpenAi(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
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
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String explanation = extractOutputText(root);
            return explanation.isBlank() ? Optional.empty() : Optional.of(explanation);
        } catch (Exception exception) {
            return Optional.empty();
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

    private BigDecimal number(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String decimal(BigDecimal value) {
        BigDecimal normalized = number(value).stripTrailingZeros();
        return normalized.scale() < 0
                ? normalized.setScale(0).toPlainString()
                : normalized.toPlainString();
    }

    private record InventorySnapshot(
            List<Product> products,
            List<StockBatch> batches,
            List<Product> critical,
            List<StockBatch> expiryRisk,
            List<BlindInventorySession> openInventories,
            BlindInventorySession latestClosed,
            BigDecimal totalStock,
            long outOfStock,
            BigDecimal expiryRiskValue,
            Double inventoryAccuracy
    ) {}
}
