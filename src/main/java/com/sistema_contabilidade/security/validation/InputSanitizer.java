package com.sistema_contabilidade.security.validation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class InputSanitizer {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
  private static final List<Pattern> ATTACK_PATTERNS =
      List.of(
          Pattern.compile("(?i)javascript\\s*:"),
          Pattern.compile("(?i)vbscript\\s*:"),
          Pattern.compile("(?i)data\\s*:\\s*text/html"),
          Pattern.compile("(?i)on(?:error|load|click|mouseover|focus)\\s*="),
          Pattern.compile("(?i)(union\\s+select|insert\\s+into|drop\\s+table|exec\\s*\\()"),
          Pattern.compile("(?i)(sleep\\s*\\(\\d+\\)|benchmark\\s*\\(|waitfor\\s+delay)"),
          Pattern.compile("(?i)(information_schema|pg_catalog|mysql\\.user|sys\\.tables)"),
          Pattern.compile("(?i)(?:'|%27)\\s*(?:or|and)\\s*(?:'|%27)?\\w+"),
          Pattern.compile("(?i)(--\\s*$|/\\*|\\*/|;\\s*--)"),
          Pattern.compile("(?i)0x[0-9a-f]{4,}"),
          Pattern.compile("(?i)(&#x|&#0|%3cscript|\\\\u003cscript|\\\\x3cscript)"));

  public String sanitizeInlineText(String input, String field, int maxLength) {
    return sanitize(input, field, maxLength, false);
  }

  public String sanitizeMultilineText(String input, String field, int maxLength) {
    return sanitize(input, field, maxLength, true);
  }

  public String sanitizeEmail(String input, String field) {
    String sanitized = sanitizeInlineText(input, field, 320);
    return sanitized == null ? null : sanitized.toLowerCase(Locale.ROOT);
  }

  public Set<String> sanitizeInlineTextSet(Set<String> values, String field, int maxLength) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }

    Set<String> sanitized = new LinkedHashSet<>();
    values.stream()
        .map(value -> sanitizeInlineText(value, field, maxLength))
        .filter(value -> value != null && !value.isBlank())
        .forEach(sanitized::add);
    return Set.copyOf(sanitized);
  }

  private String sanitize(String input, String field, int maxLength, boolean multiline) {
    if (input == null) {
      return null;
    }

    String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).trim();
    normalized = CONTROL_CHARS.matcher(normalized).replaceAll(" ");
    normalized =
        multiline
            ? normalized.replace("\r\n", "\n").replace('\r', '\n')
            : normalized.replaceAll("\\s+", " ");

    if (normalized.length() > maxLength) {
      throw invalidInput(field);
    }

    String decoded = decodeMultipleTimes(normalized).toLowerCase(Locale.ROOT);
    if (containsHtmlTag(decoded)) {
      if (log.isWarnEnabled()) {
        log.warn("Payload suspeito bloqueado no campo {}: {}", field, preview(normalized));
      }
      throw invalidInput(field);
    }
    for (Pattern pattern : ATTACK_PATTERNS) {
      if (pattern.matcher(decoded).find()) {
        if (log.isWarnEnabled()) {
          log.warn("Payload suspeito bloqueado no campo {}: {}", field, preview(normalized));
        }
        throw invalidInput(field);
      }
    }

    return normalized;
  }

  private String decodeMultipleTimes(String input) {
    String decoded = input;
    for (int index = 0; index < 3; index++) {
      String candidate = tryDecode(decoded);
      if (candidate == null || candidate.equals(decoded)) {
        return decoded;
      }
      decoded = candidate;
    }
    return decoded;
  }

  private String tryDecode(String input) {
    try {
      return URLDecoder.decode(input, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private boolean containsHtmlTag(String input) {
    int start = input.indexOf('<');
    while (start >= 0) {
      int candidate = normalizeTagStart(input, start + 1);
      if (isValidTagStart(input, candidate) && hasTagClosure(input, candidate)) {
        return true;
      }
      start = input.indexOf('<', start + 1);
    }
    return false;
  }

  private int normalizeTagStart(String input, int candidate) {
    int normalized = skipWhitespace(input, candidate);
    if (normalized < input.length() && input.charAt(normalized) == '/') {
      normalized++;
    }
    return skipWhitespace(input, normalized);
  }

  private int skipWhitespace(String input, int index) {
    int current = index;
    while (current < input.length() && Character.isWhitespace(input.charAt(current))) {
      current++;
    }
    return current;
  }

  private boolean isValidTagStart(String input, int index) {
    if (index >= input.length()) {
      return false;
    }
    char marker = input.charAt(index);
    return (marker >= 'a' && marker <= 'z') || marker == '!';
  }

  private boolean hasTagClosure(String input, int index) {
    return input.indexOf('>', index + 1) > index;
  }

  private ResponseStatusException invalidInput(String field) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Input invalido: " + field);
  }

  private String preview(String input) {
    if (input == null) {
      return "";
    }
    return input.substring(0, Math.min(120, input.length()));
  }
}
