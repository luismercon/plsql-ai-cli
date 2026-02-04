package pt.isec.mei.plsql_ai_cli.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CacheLoadService {
    private static final String CACHE_FILE = "cache/vectors_cache.json";

    public List<CachedDocumentDTO> loadAllDocuments() {
        File file = new File(CACHE_FILE);
        // Tenta também na pasta cache/ se não encontrar na raiz
        if (!file.exists()) {
            file = new File("cache/" + CACHE_FILE);
        }

        if (!file.exists()) {
            log.error("Cache file not found at {}", file.getAbsolutePath());
            return new ArrayList<>();
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            log.info("Loading vectors directly from raw JSON: {}", file.getAbsolutePath());

            // O SimpleVectorStore guarda um Map<String, Document> ou apenas uma lista?
            // Normalmente é um Map<String, Document>. Vamos tentar ler assim.
            Map<String, CachedDocumentDTO> data = mapper.readValue(file, new TypeReference<Map<String, CachedDocumentDTO>>() {
            });

            log.info("Loaded {} documents with embeddings from cache.", data.size());
            return new ArrayList<>(data.values());

        } catch (IOException e) {
            log.error("Error parsing cache JSON", e);
            return new ArrayList<>();
        }
    }
}
