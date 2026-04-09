package com.sistema_contabilidade.home.dto;

public record HomeDashboardMonthResponse(
    String label, double income, double expense, boolean currentMonth) {}
