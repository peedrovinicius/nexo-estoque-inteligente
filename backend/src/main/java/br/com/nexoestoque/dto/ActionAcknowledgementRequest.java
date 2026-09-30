package br.com.nexoestoque.dto;

import jakarta.validation.constraints.Size;

public record ActionAcknowledgementRequest(
        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
        String note
) {}
