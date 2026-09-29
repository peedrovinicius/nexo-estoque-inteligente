package br.com.nexoestoque.dto;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @Size(max = 255, message = "Motivo deve ter no máximo 255 caracteres")
        String reason
) {}
