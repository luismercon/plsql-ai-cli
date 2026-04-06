package pt.isec.mei.plsql_ai_cli.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import pt.isec.mei.plsql_ai_cli.model.VectorizeResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LLMAnalysisService {

    private final EmbeddingModel embeddingModel;
    private SimpleVectorStore vectorStore;
    private static final String CACHE_FILE = "cache/vectors_cache.json";
    private static final int MAX_TOKENS = 8192;
    private static final Pattern COMPLETION_TOKENS_PATTERN = Pattern.compile("^completion_tokens:\\s*(\\d+)$", Pattern.MULTILINE);

    @Autowired
    public LLMAnalysisService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File cache = new File(CACHE_FILE);
        if (cache.exists()) {
            log.info("A carregar cache de vetores: {}", CACHE_FILE);
            this.vectorStore.load(cache);
        } else {
            log.info("Cache de vetores não encontrado. A iniciar nova base.");
        }
    }

    public String processAndVectorize(String fileName) {
        String targetFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        Path rootDir = Paths.get("results");

        if (!Files.exists(rootDir)) {
            log.error("Diretoria de resultados não encontrada.");
            return "Erro: diretoria de resultados não encontrada.";
        }

        try {
            Path foundPath;
            try (var stream = Files.walk(rootDir)) {
                foundPath = stream
                        .filter(p -> !Files.isDirectory(p))
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetFileName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Ficheiro não encontrado: " + targetFileName));
            }

            String rawContent = Files.readString(foundPath, StandardCharsets.UTF_8);
            if (rawContent.isEmpty()) return "Erro: ficheiro vazio.";

            OptionalInt completionTokens = parseCompletionTokens(rawContent);
            if (completionTokens.isEmpty()) {
                log.warn("Ignorado (sem frontmatter): {}", targetFileName);
                return "Ignorado: " + targetFileName + " não contém frontmatter.";
            }
            if (completionTokens.getAsInt() > MAX_TOKENS) {
                log.warn("Ignorado (completion_tokens={} > {}): {}", completionTokens.getAsInt(), MAX_TOKENS, targetFileName);
                return String.format("Ignorado: %s excede o limite de tokens (%d > %d).",
                        targetFileName, completionTokens.getAsInt(), MAX_TOKENS);
            }

            String cleanContent = removeFrontmatter(rawContent);
            if (cleanContent.isEmpty()) return "Erro: conteúdo vazio após remover frontmatter.";

            String relativePath = rootDir.relativize(foundPath).toString();

            Document document = new Document(cleanContent, Map.of(
                    "filename", targetFileName,
                    "path", relativePath,
                    "type", "documentation_output"
            ));

            log.info("A gerar embedding para {}...", targetFileName);
            this.vectorStore.add(List.of(document));
            saveCache();

            return "Sucesso: " + targetFileName + " vetorizado.";

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar ficheiro markdown", e);
        }
    }

    public VectorizeResult vectorizeAll() {
        Path rootDir = Paths.get("results");
        if (!Files.exists(rootDir)) return new VectorizeResult(0, List.of());

        log.info("A iniciar vetorização em massa de todos os ficheiros em results/...");

        List<Document> documentsBatch = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int count = 0;

        try (var stream = Files.walk(rootDir)) {
            List<Path> markdownFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .toList();

            for (Path path : markdownFiles) {
                try {
                    String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                    String filename = path.getFileName().toString();

                    OptionalInt completionTokens = parseCompletionTokens(rawContent);
                    if (completionTokens.isEmpty()) {
                        log.warn("Ignorado (sem frontmatter): {}", filename);
                        skipped.add(filename + " (sem frontmatter)");
                        continue;
                    }
                    if (completionTokens.getAsInt() > MAX_TOKENS) {
                        log.warn("Ignorado (completion_tokens={} > {}): {}", completionTokens.getAsInt(), MAX_TOKENS, filename);
                        skipped.add(String.format("%s (%d completion tokens)", filename, completionTokens.getAsInt()));
                        continue;
                    }

                    String cleanContent = removeFrontmatter(rawContent);
                    if (cleanContent.length() < 10) continue;

                    Document doc = new Document(cleanContent, Map.of(
                            "filename", filename,
                            "path", rootDir.relativize(path).toString(),
                            "type", "documentation_output"
                    ));

                    documentsBatch.add(doc);
                    count++;

                } catch (Exception e) {
                    log.error("Erro ao ler ficheiro: {}", path, e);
                }
            }

            if (!documentsBatch.isEmpty()) {
                log.info("A enviar {} documentos para o modelo de embeddings...", documentsBatch.size());
                this.vectorStore.add(documentsBatch);
                saveCache();
            }

        } catch (IOException e) {
            log.error("Erro ao percorrer diretoria de resultados", e);
        }

        return new VectorizeResult(count, skipped);
    }

    private OptionalInt parseCompletionTokens(String rawContent) {
        if (!rawContent.startsWith("---")) return OptionalInt.empty();
        Matcher matcher = COMPLETION_TOKENS_PATTERN.matcher(rawContent);
        if (!matcher.find()) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }

    private String removeFrontmatter(String content) {
        // Remove o bloco YAML inicial (entre ---) para não sujar a análise semântica
        return content.replaceAll("(?s)^---\\n.*?\\n---\\s*", "").trim();
    }

    private void saveCache() {
        try {
            File cacheFile = new File(CACHE_FILE);
            File parentDir = cacheFile.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            this.vectorStore.save(cacheFile);
            log.info("Cache de vetores guardado com sucesso.");

        } catch (Exception e) {
            log.error("Falha ao guardar cache do vector store", e);
        }
    }
}