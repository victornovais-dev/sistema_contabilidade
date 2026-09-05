package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados editaveis no card de comprovante, sem substituir anexos ou dados auxiliares. */
public record ItemDadosUpdateRequest(
    @NotNull(message = "Valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
        @DecimalMax(value = "10000000.00", message = "Valor nao pode ultrapassar 10 milhoes")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Valor deve ter no maximo 8 digitos inteiros e 2 decimais")
        BigDecimal valor,
    @NotNull(message = "Data e obrigatoria") LocalDate data,
    @NotNull(message = "Tipo e obrigatorio") TipoItem tipo,
    @Size(max = 120, message = "Descricao deve ter no maximo 120 caracteres") String descricao,
    @Size(max = 150, message = "Razao social ou nome deve ter no maximo 150 caracteres")
        String razaoSocialNome,
    @Size(max = 32, message = "CNPJ ou CPF deve ter no maximo 32 caracteres") String cnpjCpf,
    @Size(max = 2000, message = "Observacao deve ter no maximo 2000 caracteres")
        String observacao) {}
