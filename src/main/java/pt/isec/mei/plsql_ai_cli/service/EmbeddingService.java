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


@Service
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private SimpleVectorStore vectorStore;

    // Caminho para o ficheiro de cache dos vetores
    private static final String CACHE_FILE = "cache/vectors_cache.json";

    // nomic-embed-text has a 2048-token context window; ~3 chars/token → 6000 chars is a safe limit
    private static final int MAX_EMBED_CHARS = 12000;//8192;

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
     * Ficheiros sem frontmatter YAML ou cujo conteúdo exceda MAX_EMBED_CHARS são ignorados.
     */
    public VectorizeResult vectorizeAll() {
        Path rootDir = Paths.get("results");
        if (!Files.exists(rootDir)) return new VectorizeResult(0, List.of(), List.of());

        log.info("Starting bulk vectorization of all files in results/...");

        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int count = 0;

        try (var stream = Files.walk(rootDir)) {
            List<Path> markdownFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .toList();

            for (Path path : markdownFiles) {
                String filename = path.getFileName().toString();
                try {
                    String rawContent = Files.readString(path, StandardCharsets.UTF_8);

                    if (!rawContent.startsWith("---")) {
                        log.warn("Skipped (no frontmatter): {}", filename);
                        skipped.add(filename + " (no frontmatter)");
                        continue;
                    }

                    String cleanContent = removeFrontmatter(rawContent);
                    if (cleanContent.length() < 10) continue;

                    if (cleanContent.length() > MAX_EMBED_CHARS) {
                        log.warn("Skipped (content too long: {} chars > {}): {}", cleanContent.length(), MAX_EMBED_CHARS, filename);
                        skipped.add(String.format("%s (%d chars, exceeds embed limit of %d)", filename, cleanContent.length(), MAX_EMBED_CHARS));
                        continue;
                    }

                    // Use filename as deterministic ID — it contains the timestamp, so it is unique and stable across re-runs
                    Document doc = new Document(filename, cleanContent, Map.of(
                            "filename", filename,
                            "path", rootDir.relativize(path).toString(),
                            "type", "documentation_output"
                    ));

                    log.debug("Vectorizing: {}", filename);
                    this.vectorStore.add(List.of(doc));
                    count++;

                } catch (Exception e) {
                    long fileBytes;
                    try {
                        fileBytes = Files.size(path);
                    } catch (IOException ignored) {
                        fileBytes = -1;
                    }
                    String errorMsg = String.format("%s (file size: %d bytes) → %s", filename, fileBytes, e.getMessage());
                    log.error("Error vectorizing file: {}", path, e);
                    System.out.println("ERROR vectorizing: " + errorMsg);
                    failed.add(errorMsg);
                }
            }

            if (count > 0) {
                saveCache();
            }

        } catch (IOException e) {
            log.error("Error traversing results directory", e);
        }

        return new VectorizeResult(count, skipped, failed);
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

