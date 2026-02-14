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
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File cache = new File(CACHE_FILE);
        if (cache.exists()) {
            log.info("A carregar cache de vetores: {}", CACHE_FILE);
            this.vectorStore.load(cache);
        } else {
            log.info("Cache de vetores não encontrado. A iniciar nova base.");
        }
    }

    public void processAndVectorize(String fileName) {
        String targetFileName = fileName.endsWith(".md") ? fileName : fileName + ".md";
        Path rootDir = Paths.get("results");

        if (!Files.exists(rootDir)) {
            log.error("Diretoria de resultados não encontrada.");
            return;
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
            if (rawContent.isEmpty()) return;

            String cleanContent = removeFrontmatter(rawContent);
            if (cleanContent.isEmpty()) return;

            String relativePath = rootDir.relativize(foundPath).toString();

            Document document = new Document(cleanContent, Map.of(
                    "filename", targetFileName,
                    "path", relativePath,
                    "type", "documentation_output"
            ));

            log.info("A gerar embedding para {}...", targetFileName);
            this.vectorStore.add(List.of(document));
            saveCache();

        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar ficheiro markdown", e);
        }
    }

    public int vectorizeAll() {
        Path rootDir = Paths.get("results");
        if (!Files.exists(rootDir)) return 0;

        log.info("A iniciar vetorização em massa de todos os ficheiros em results/...");

        List<Document> documentsBatch = new ArrayList<>();
        int count = 0;

        try (var stream = Files.walk(rootDir)) {
            List<Path> markdownFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().toLowerCase().endsWith(".md"))
                    .toList();

            for (Path path : markdownFiles) {
                try {
                    String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                    String cleanContent = removeFrontmatter(rawContent);

                    if (cleanContent.length() < 10) continue;

                    Document doc = new Document(cleanContent, Map.of(
                            "filename", path.getFileName().toString(),
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

        return count;
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