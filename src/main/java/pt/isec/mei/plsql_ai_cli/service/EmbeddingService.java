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
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private SimpleVectorStore vectorStore;

    // Caminho para o ficheiro de cache dos vetores
    private static final String CACHE_FILE = "cache/vectors_cache.json";

    // Limite máximo de tokens de completion aceites para vetorização
    private static final int MAX_TOKENS = 8192;

    // Padrão regex para extrair o número de tokens de completion do frontmatter YAML
    private static final Pattern COMPLETION_TOKENS_PATTERN =
            Pattern.compile("^completion_tokens:\\s*(\\d+)$", Pattern.MULTILINE);

    @Autowired
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File cache = new File(CACHE_FILE);
        if (cache.exists()) {
            log.info("Loading vector cache from: {}", CACHE_FILE);
            this.vectorStore.load(cache);
        } else {
            log.info("Vector cache not found. Starting with an empty store.");
        }
    }

    /**
     * Vetoriza todos os ficheiros Markdown encontrados recursivamente em results/.
     * Ficheiros sem frontmatter ou que excedam MAX_TOKENS são ignorados.
     */
    public VectorizeResult vectorizeAll() {
        Path rootDir = Paths.get("results");
        if (!Files.exists(rootDir)) return new VectorizeResult(0, List.of());

        log.info("Starting bulk vectorization of all files in results/...");

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
                        log.warn("Skipped (no frontmatter): {}", filename);
                        skipped.add(filename + " (no frontmatter)");
                        continue;
                    }
                    if (completionTokens.getAsInt() > MAX_TOKENS) {
                        log.warn("Skipped (completion_tokens={} > {}): {}", completionTokens.getAsInt(), MAX_TOKENS, filename);
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
                    log.error("Error reading file: {}", path, e);
                }
            }

            if (!documentsBatch.isEmpty()) {
                log.info("Sending {} document(s) to the embedding model...", documentsBatch.size());
                this.vectorStore.add(documentsBatch);
                saveCache();
            }

        } catch (IOException e) {
            log.error("Error traversing results directory", e);
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
        // Remove o bloco YAML inicial (entre ---) para não enviesar a análise semântica
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
            log.info("Vector cache saved successfully.");

        } catch (Exception e) {
            log.error("Failed to save vector store cache", e);
        }
    }
}

