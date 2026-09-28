package br.com.nexoestoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdvisorChatRequest(
        @NotBlank
        @Size(max = 600)
        String question
) {}
