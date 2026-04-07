# Layer 5 — Upload de Arquivos

> **Mindset pentester:** Extensão de arquivo é decoração. MIME type do Content-Type é
> mentira do cliente. O único dado confiável são os magic bytes reais do arquivo.
> Ataques clássicos: `.php` disfarçado de `.jpg`, polyglot files (GIFAR), SVG com XSS,
> ZIP bombs, path traversal via nome de arquivo, RCE via ImageMagick (ImageTragick).

---

## Por Que Validar Magic Bytes?

```
arquivo.jpg com conteúdo:
GIF89a<script>alert(1)</script>   ← GIF válido + XSS (polyglot)
%PDF-1.4 ... /JS (alert(1))       ← PDF com JavaScript embutido
PK\x03\x04...                     ← ZIP bomb disfarçado de imagem
```

---

## Magic Bytes por Tipo de Arquivo

```java
// utils/MagicBytesValidator.java
@Component
public class MagicBytesValidator {

    private record FileSignature(String mimeType, byte[] signature, int offset) {}

    private static final List<FileSignature> SIGNATURES = List.of(
        // JPEG: FF D8 FF
        new FileSignature("image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}, 0),
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        new FileSignature("image/png",
            new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 0),
        // GIF: GIF87a ou GIF89a
        new FileSignature("image/gif",
            new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61}, 0),
        new FileSignature("image/gif",
            new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}, 0),
        // WebP: RIFF....WEBP
        new FileSignature("image/webp",
            new byte[]{0x52, 0x49, 0x46, 0x46}, 0), // verificar WEBP em offset 8 também
        // PDF: %PDF-
        new FileSignature("application/pdf",
            new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}, 0),
        // ZIP (também DOCX/XLSX/PPTX): PK\x03\x04
        new FileSignature("application/zip",
            new byte[]{0x50, 0x4B, 0x03, 0x04}, 0)
    );

    // Extensões perigosas — NUNCA permitir
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "php", "php3", "php4", "php5", "phtml", "phar",
        "jsp", "jspx", "jspf", "jspa",
        "asp", "aspx", "ascx", "ashx", "asmx",
        "exe", "bat", "cmd", "sh", "bash", "ps1",
        "py", "rb", "pl", "lua",
        "dll", "so", "dylib",
        "htaccess", "htpasswd",
        "svg"  // SVG pode conter JS — tratar separado
    );

    /**
     * Valida magic bytes do arquivo contra o MIME type declarado.
     * Retorna o MIME type real detectado.
     */
    public String validateAndDetect(byte[] fileBytes, String declaredMime,
                                    String originalFilename) {
        // 1. Verificar tamanho mínimo
        if (fileBytes == null || fileBytes.length < 8) {
            throw new InvalidFileException("Arquivo muito pequeno ou corrompido");
        }

        // 2. Verificar extensão perigosa
        String ext = getExtension(originalFilename).toLowerCase();
        if (DANGEROUS_EXTENSIONS.contains(ext)) {
            throw new InvalidFileException("Tipo de arquivo não permitido: " + ext);
        }

        // 3. Detectar MIME real pelos magic bytes
        String detectedMime = detectMimeFromBytes(fileBytes);
        if (detectedMime == null) {
            throw new InvalidFileException("Tipo de arquivo não reconhecido");
        }

        // 4. MIME declarado deve bater com o detectado (evitar spoofing)
        if (!detectedMime.equals(declaredMime)) {
            SecurityEventPublisher.publish(SecurityEvent.FILE_MIME_MISMATCH,
                "Declared: " + declaredMime + " | Real: " + detectedMime);
            throw new InvalidFileException("Tipo de arquivo inconsistente");
        }

        // 5. Verificar polyglot: arquivo que é válido em dois formatos
        detectPolyglot(fileBytes, detectedMime);

        return detectedMime;
    }

    private String detectMimeFromBytes(byte[] bytes) {
        for (FileSignature sig : SIGNATURES) {
            if (matchesSignature(bytes, sig.signature(), sig.offset())) {
                return sig.mimeType();
            }
        }
        return null;
    }

    private boolean matchesSignature(byte[] data, byte[] sig, int offset) {
        if (data.length < offset + sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (data[offset + i] != sig[i]) return false;
        }
        return true;
    }

    private void detectPolyglot(byte[] bytes, String mime) {
        // GIF com conteúdo HTML/JS embutido (GIFAR attack)
        if ("image/gif".equals(mime)) {
            String content = new String(bytes, StandardCharsets.ISO_8859_1);
            if (content.contains("<script") || content.contains("javascript:") ||
                content.contains("<?php") || content.contains("<%")) {
                throw new InvalidFileException("Arquivo contém conteúdo suspeito");
            }
        }

        // PDF com JavaScript
        if ("application/pdf".equals(mime)) {
            String content = new String(bytes, StandardCharsets.ISO_8859_1);
            if (content.contains("/JS ") || content.contains("/JavaScript") ||
                content.contains("/OpenAction")) {
                SecurityEventPublisher.publish(SecurityEvent.MALICIOUS_PDF, "PDF com JS");
                throw new InvalidFileException("PDF com conteúdo executável não permitido");
            }
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
```

---

## Sanitização de Nome de Arquivo (Path Traversal via filename)

```java
// utils/FilenameSanitizer.java
@Component
public class FilenameSanitizer {

    /**
     * Atacante envia: "../../../../etc/passwd"
     * ou: "file.jpg\x00.php" (null byte injection)
     * ou: "CON.jpg" (Windows reserved names)
     */
    public String sanitize(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return UUID.randomUUID().toString();
        }

        // Remover null bytes
        String name = originalFilename.replace("\0", "");

        // Extrair apenas o nome base — eliminar qualquer path
        name = Paths.get(name).getFileName().toString();

        // Remover caracteres perigosos
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        // Limitar tamanho
        if (name.length() > 100) {
            String ext = getExtension(name);
            name = name.substring(0, 90) + "." + ext;
        }

        // Windows reserved names (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
        if (name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            name = "_" + name;
        }

        // NUNCA usar o nome original como chave — sempre gerar UUID + extensão sanitizada
        String ext = getExtension(name);
        return UUID.randomUUID().toString() + (ext.isBlank() ? "" : "." + ext);
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase();
        // Apenas extensões seguras conhecidas
        Set<String> safe = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv");
        return safe.contains(ext) ? ext : "";
    }
}
```

---

## Proteção contra ZIP Bomb e Arquivos Grandes

```java
// service/FileUploadService.java
@Service
public class FileUploadService {

    @Value("${upload.max-size-bytes:10485760}")      // 10MB
    private long maxFileSizeBytes;

    @Value("${upload.max-image-dimension:4096}")
    private int maxImageDimension;

    @Autowired private MagicBytesValidator magicBytesValidator;
    @Autowired private FilenameSanitizer filenameSanitizer;

    public StoredFile processUpload(MultipartFile file, UUID userId,
                                    Set<String> allowedMimes) {
        // 1. Verificar tamanho antes de ler o conteúdo (evitar OOM)
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException("Arquivo excede o tamanho máximo permitido");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Erro ao ler arquivo");
        }

        // 2. Validar magic bytes e MIME
        String detectedMime = magicBytesValidator.validateAndDetect(
            bytes, file.getContentType(), file.getOriginalFilename());

        // 3. Verificar se MIME é permitido para este endpoint
        if (!allowedMimes.contains(detectedMime)) {
            throw new InvalidFileException("Tipo não permitido: " + detectedMime);
        }

        // 4. Para imagens: verificar dimensões (proteger contra DecompressionBomb)
        if (detectedMime.startsWith("image/")) {
            validateImageDimensions(bytes, detectedMime);
        }

        // 5. Sanitizar nome e gerar storage key
        String safeFilename = filenameSanitizer.sanitize(file.getOriginalFilename());

        // 6. Armazenar FORA do webroot — nunca em pasta pública
        String storagePath = storeOutsideWebroot(bytes, safeFilename, userId);

        return new StoredFile(UUID.randomUUID(), safeFilename, detectedMime,
            bytes.length, storagePath, userId);
    }

    private void validateImageDimensions(byte[] bytes, String mime) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ImageInputStream iis = ImageIO.createImageInputStream(bais);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (!readers.hasNext()) throw new InvalidFileException("Imagem inválida");

            ImageReader reader = readers.next();
            reader.setInput(iis, true, true);

            // Ler apenas metadados — não decodificar imagem inteira (evitar bomb)
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);

            if (width > maxImageDimension || height > maxImageDimension) {
                throw new InvalidFileException(
                    "Dimensões da imagem excedem o permitido: " + width + "x" + height);
            }

            // Verificar ratio extremo (1x100000 — outro vetor de decompression bomb)
            if ((double) Math.max(width, height) / Math.min(width, height) > 100) {
                throw new InvalidFileException("Proporção de imagem inválida");
            }

            reader.dispose();
        } catch (IOException e) {
            throw new InvalidFileException("Não foi possível validar a imagem");
        }
    }

    private String storeOutsideWebroot(byte[] bytes, String filename, UUID userId) {
        // Armazenar em path sem acesso HTTP direto
        Path uploadDir = Paths.get(System.getenv("UPLOAD_BASE_PATH"),
            userId.toString()); // isolado por usuário
        try {
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(filename).normalize();

            // Garantir que o path final está dentro do diretório esperado
            if (!targetPath.startsWith(uploadDir)) {
                throw new SecurityException("Path traversal detectado");
            }

            Files.write(targetPath, bytes);
            return targetPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao armazenar arquivo", e);
        }
    }
}
```

---

## Download Seguro (evitar path traversal no acesso)

```java
@GetMapping("/files/{fileId}")
public ResponseEntity<Resource> download(@PathVariable UUID fileId,
                                         @AuthenticationPrincipal UserDetails user) {
    StoredFile file = fileRepository.findById(fileId)
        .orElseThrow(ResourceNotFoundException::new);

    // Ownership check
    if (!file.getOwnerId().equals(extractUserId(user)))
        throw new ResourceNotFoundException();

    // Servir via stream — NUNCA redirecionar para path interno
    byte[] bytes = Files.readAllBytes(Paths.get(file.getStoragePath()));

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getMimeType()))
        // Content-Disposition: attachment — forçar download, não execução
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getSafeName() + "\"")
        // Sem cache para arquivos privados
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
        .header("X-Content-Type-Options", "nosniff")
        .body(new ByteArrayResource(bytes));
}
```

---

## Checklist Layer 5

- [ ] Magic bytes validados — NUNCA confiar em extensão ou Content-Type do cliente
- [ ] Extensões perigosas bloqueadas (php, jsp, asp, sh, svg etc.)
- [ ] MIME declarado deve coincidir com o detectado pelos magic bytes
- [ ] Detecção de polyglot files (GIFAR, PDF com JS)
- [ ] Null byte injection prevenido no nome do arquivo
- [ ] Nome do arquivo sanitizado — apenas UUID gerado como storage key
- [ ] Windows reserved filenames tratados (CON, NUL, COM1...)
- [ ] Tamanho verificado ANTES de ler o conteúdo (evitar OOM)
- [ ] Dimensões de imagem verificadas sem decodificar (evitar decompression bomb)
- [ ] Arquivos armazenados FORA do webroot
- [ ] Path.normalize() + verificação de prefix para evitar path traversal no storage
- [ ] Download via stream com Content-Disposition: attachment
- [ ] Storage path isolado por userId
