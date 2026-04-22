#!/bin/bash
# analyze.sh — Análise rápida de complexidade ciclomática via PMD CLI
# Uso: ./analyze.sh [caminho-do-projeto] [threshold]
#
# Requisitos: Java 11+, PMD CLI instalado ou download automático

PROJECT_DIR="${1:-.}"
THRESHOLD="${2:-10}"
PMD_VERSION="7.0.0"
PMD_DIR="/tmp/pmd-bin-${PMD_VERSION}"
PMD_URL="https://github.com/pmd/pmd/releases/download/pmd_releases/${PMD_VERSION}/pmd-dist-${PMD_VERSION}-bin.zip"

echo "════════════════════════════════════════════════"
echo "  Análise de Complexidade Ciclomática - Spring  "
echo "════════════════════════════════════════════════"
echo ""
echo "📁 Projeto: $PROJECT_DIR"
echo "⚠️  Threshold: CC > $THRESHOLD"
echo ""

# Baixar PMD se não existir
if [ ! -d "$PMD_DIR" ]; then
    echo "⬇️  Baixando PMD $PMD_VERSION..."
    curl -sL "$PMD_URL" -o /tmp/pmd.zip
    unzip -q /tmp/pmd.zip -d /tmp/
    echo "✅ PMD instalado em $PMD_DIR"
fi

PMD_BIN="$PMD_DIR/bin/pmd"

# Criar regra temporária
RULESET=$(mktemp /tmp/cc-ruleset.XXXXXX.xml)
cat > "$RULESET" <<EOF
<?xml version="1.0"?>
<ruleset name="CC" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0
    https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
  <description>CC Check</description>
  <rule ref="category/java/design.xml/CyclomaticComplexity">
    <properties>
      <property name="methodReportLevel" value="${THRESHOLD}"/>
    </properties>
  </rule>
</ruleset>
EOF

# Encontrar sources
SRC_DIR=""
for candidate in "$PROJECT_DIR/src/main/java" "$PROJECT_DIR/src" "$PROJECT_DIR"; do
    if [ -d "$candidate" ]; then
        SRC_DIR="$candidate"
        break
    fi
done

if [ -z "$SRC_DIR" ]; then
    echo "❌ Não foi possível encontrar diretório de fontes Java."
    exit 1
fi

echo "🔍 Analisando: $SRC_DIR"
echo ""

# Rodar PMD
OUTPUT=$("$PMD_BIN" check \
    --dir "$SRC_DIR" \
    --rulesets "$RULESET" \
    --format text \
    --no-progress 2>/dev/null)

rm -f "$RULESET"

# Processar saída
VIOLATIONS=$(echo "$OUTPUT" | grep -c "CyclomaticComplexity" || true)

if [ "$VIOLATIONS" -eq 0 ]; then
    echo "✅ Nenhuma violação encontrada! CC <= $THRESHOLD em todos os métodos."
    exit 0
fi

echo "📊 VIOLAÇÕES ENCONTRADAS: $VIOLATIONS métodos acima do threshold"
echo ""
echo "$OUTPUT" | grep "CyclomaticComplexity" | while IFS= read -r line; do
    # Extrair CC do texto da violação
    CC=$(echo "$line" | grep -oP 'complexity of \K[0-9]+' || echo "?")
    FILE=$(echo "$line" | cut -d: -f1 | xargs basename)
    
    if [ "$CC" -gt 20 ] 2>/dev/null; then
        ICON="🚨"
        RISK="CRÍTICO"
    elif [ "$CC" -gt 10 ] 2>/dev/null; then
        ICON="🔴"
        RISK="ALTO"
    else
        ICON="⚠️ "
        RISK="MÉDIO"
    fi
    
    echo "  $ICON CC=$CC [$RISK] $FILE"
    echo "     $line" | sed 's/.*\/src\/main\/java\//  src\/main\/java\//'
    echo ""
done

echo ""
echo "💡 Dica: rode 'mvn pmd:pmd' para relatório HTML completo em target/site/pmd.html"
echo "   Ou consulte references/refactoring-patterns.md para padrões de refatoração"
