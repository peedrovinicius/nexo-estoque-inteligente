package br.com.nexoestoque.dto;

import java.time.Instant;

public record AdvisorChatResponse(
        String answer,
        String source,
        String model,
        Instant generatedAt
) {}
