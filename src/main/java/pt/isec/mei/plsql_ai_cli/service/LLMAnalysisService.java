package pt.isec.mei.plsql_ai_cli.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
public class LLMAnalysisService {

    private final EmbeddingModel embeddingModel;
    private SimpleVectorStore vectorStore;
    private static final String CACHE_FILE = "cache/vectors_cache.json";

    @Autowired
    public LLMAnalysisService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        // Inicializa o VectorStore (usando builder para compatibilidade com M6)
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // Tenta carregar o cache existente
        File cache = new File(CACHE_FILE);
        if (cache.exists()) {
            log.info("Loading vector store from cache: {}", CACHE_FILE);
            this.vectorStore.load(cache);
        } else {
            log.info("No vector cache found. Starting fresh.");
        }
    }

    public void processAndVectorize(String fileName) {
        String targetFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        Path rootDir = Paths.get("results");

        if (!Files.exists(rootDir)) {
            log.error("Results directory not found.");
            return;
        }

        log.info("Searching for '{}' inside '{}' subdirectories...", targetFileName, rootDir);

        try {
            // Busca recursiva
            Path foundPath;
            try (var stream = Files.walk(rootDir)) {
                foundPath = stream
                        .filter(p -> !Files.isDirectory(p))
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetFileName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("File not found in any results subdirectory: " + targetFileName));
            }

            log.info("File found at: {}", foundPath.toAbsolutePath());

            String rawContent = Files.readString(foundPath, StandardCharsets.UTF_8);

            if (rawContent.isEmpty()) {
                log.warn("File {} is empty.", targetFileName);
                return;
            }

            // LIMPEZA: Remove metadados antes de vetorizar
            String cleanContent = removeFrontmatter(rawContent);

            if (cleanContent.isEmpty()) {
                log.warn("File {} has no content after stripping frontmatter.", targetFileName);
                return;
            }

            String relativePath = rootDir.relativize(foundPath).toString();

            Document document = new Document(cleanContent, Map.of(
                    "filename", targetFileName,
                    "path", relativePath,
                    "type", "documentation_output"
            ));

            log.info("Generating embedding for {}...", targetFileName);
            this.vectorStore.add(List.of(document));

            saveCache();

            log.info("Documentation {} vectorized and saved.", targetFileName);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read or find markdown file", e);
        }
    }

    public int vectorizeAll() {
        Path rootDir = Paths.get("results");
        if (!Files.exists(rootDir)) {
            log.error("Results directory not found.");
            return 0;
        }

        log.info("Starting batch vectorization for ALL files in results/...");

        List<Document> documentsBatch = new ArrayList<>();
        int count = 0;

        try (var stream = Files.walk(rootDir)) {
            List<Path> markdownFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .toList();

            log.info("Found {} markdown files. Processing...", markdownFiles.size());

            for (Path path : markdownFiles) {
                try {
                    String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                    if (rawContent.isBlank()) continue;

                    // LIMPEZA: Remove metadados antes de vetorizar
                    String cleanContent = removeFrontmatter(rawContent);

                    // Ignora arquivos que ficaram vazios ou muito curtos após limpeza
                    if (cleanContent.length() < 10) {
                        log.debug("Skipping {} (empty after cleaning frontmatter)", path.getFileName());
                        continue;
                    }

                    String filename = path.getFileName().toString();
                    String relativePath = rootDir.relativize(path).toString();

                    Document doc = new Document(cleanContent, Map.of(
                            "filename", filename,
                            "path", relativePath,
                            "type", "documentation_output"
                    ));

                    documentsBatch.add(doc);
                    count++;

                } catch (Exception e) {
                    log.error("Failed to read file: {}", path, e);
                }
            }

            if (!documentsBatch.isEmpty()) {
                log.info("Sending {} documents to Ollama for embedding (this may take a while)...", documentsBatch.size());
                this.vectorStore.add(documentsBatch);
                saveCache();
                log.info("Batch vectorization completed. Cache updated.");
            } else {
                log.warn("No valid markdown files found to vectorize.");
            }

        } catch (IOException e) {
            log.error("Error walking through results directory", e);
        }

        return count;
    }

    /**
     * Remove o cabeçalho YAML (Frontmatter) do conteúdo Markdown.
     * O padrão procura por conteúdo entre os primeiros traços (---).
     */
    private String removeFrontmatter(String content) {
        // Regex:
        // (?s)  -> Habilita modo "single line" (ponto inclui quebra de linha)
        // ^---  -> Começa com --- no início da string
        // .*?   -> Qualquer coisa (non-greedy)
        // ---   -> Até o próximo ---
        // \s* -> E qualquer espaço em branco/quebra de linha seguinte
        return content.replaceAll("(?s)^---\\n.*?\\n---\\s*", "").trim();
    }

    private void saveCache() {
        try {
            File cacheFile = new File(CACHE_FILE);

            // NOVO: Garante que a pasta 'cache' existe antes de salvar
            File parentDir = cacheFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (created) {
                    log.info("Created cache directory: {}", parentDir.getAbsolutePath());
                }
            }

            this.vectorStore.save(cacheFile);
            log.info("Vector store saved to: {}", cacheFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("Failed to save vector store cache", e);
        }
    }
}