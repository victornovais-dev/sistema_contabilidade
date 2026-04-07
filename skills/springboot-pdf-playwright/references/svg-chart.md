# Gráfico Donut SVG — Cálculo Dinâmico

O template usa um gráfico donut feito com `stroke-dasharray` em SVG puro.
Para torná-lo dinâmico com Thymeleaf é necessário calcular os valores no backend.

---

## Como funciona o donut com stroke-dasharray

O círculo tem `r="82"`, logo:
- Circunferência = `2 × π × 82 ≈ 515.22`

Cada fatia usa dois valores:
- `stroke-dasharray="FATIA RESTANTE"` — tamanho da fatia e espaço restante
- `stroke-dashoffset="-OFFSET"` — deslocamento acumulado das fatias anteriores

```
FATIA     = (percentual / 100) × circunferência
RESTANTE  = circunferência - FATIA
OFFSET    = soma das FATIAS anteriores (negativo)
```

---

## Método utilitário Java — DonutChartCalculator

```java
@Component
public class DonutChartCalculator {

    private static final double RADIUS = 82.0;
    private static final double CIRCUMFERENCE = 2 * Math.PI * RADIUS; // 515.22

    public List<DonutSlice> calculate(List<ReportData.CategoriaItem> categorias) {
        List<DonutSlice> slices = new ArrayList<>();
        double offset = 0.0;

        for (ReportData.CategoriaItem cat : categorias) {
            double fatia = (cat.getPercentual() / 100.0) * CIRCUMFERENCE;
            double restante = CIRCUMFERENCE - fatia;

            slices.add(DonutSlice.builder()
                .cor(cat.getCor())
                .dashArray(round(fatia) + " " + round(restante))
                .dashOffset("-" + round(offset))
                .build());

            offset += fatia;
        }
        return slices;
    }

    private String round(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    @Data
    @Builder
    public static class DonutSlice {
        private String cor;
        private String dashArray;   // ex: "112.74 402.48"
        private String dashOffset;  // ex: "-112.74"
    }
}
```

Injete `DonutChartCalculator` no serviço e adicione as fatias ao modelo:

```java
// Em PlaywrightPdfService ou num serviço de montagem de dados:
List<DonutSlice> fatias = donutChartCalculator.calculate(data.getCategorias());
ctx.setVariable("fatias", fatias);
```

---

## Template Thymeleaf — SVG dinâmico

```html
<svg width="320" height="320" viewBox="0 0 320 320"
     aria-label="Gráfico de pizza das despesas por categoria" role="img">
  <g transform="rotate(-90 160 160)">
    <circle th:each="fatia : ${fatias}"
            cx="160" cy="160" r="82"
            fill="none"
            th:style="'stroke:' + ${fatia.cor} + ';'"
            stroke-width="54"
            th:attr="stroke-dasharray=${fatia.dashArray},
                     stroke-dashoffset=${fatia.dashOffset}"
            stroke-linecap="butt">
    </circle>
  </g>
  <circle cx="160" cy="160" r="52" fill="#ffffff"/>
</svg>
```

---

## Exemplo com os dados do template original

| Categoria  | Valor    | %      | Fatia    | Offset   |
|------------|----------|--------|----------|----------|
| Estrutura  | 2.500,00 | 21,89% | 112,74   | 0        |
| Pessoal    | 5.900,00 | 51,66% | 266,15   | -112,74  |
| Operacional|   820,00 |  7,18% |  36,99   | -378,89  |
| Tecnologia | 1.200,00 | 10,51% |  54,15   | -415,88  |
| Marketing  | 1.000,00 |  8,76% |  45,13   | -470,03  |

---

## Cálculo do percentual no backend

```java
BigDecimal total = despesas.stream()
    .map(LancamentoItem::getValor)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

List<CategoriaItem> categorias = despesas.stream()
    .collect(Collectors.groupingBy(
        LancamentoItem::getCategoria,
        Collectors.reducing(BigDecimal.ZERO, LancamentoItem::getValor, BigDecimal::add)
    ))
    .entrySet().stream()
    .map(e -> {
        double pct = e.getValue().doubleValue() / total.doubleValue() * 100;
        return CategoriaItem.builder()
            .nome(e.getKey())
            .valor(e.getValue())
            .percentual(pct)
            .cor(corParaCategoria(e.getKey())) // mapeamento de cores
            .build();
    })
    .toList();
```
