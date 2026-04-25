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

        if (!file.exists()) {
            log.error("Cache file not found at: {}", file.getAbsolutePath());
            return new ArrayList<>();
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            log.info("Loading vector embeddings from JSON cache: {}", file.getAbsolutePath());

            // O SimpleVectorStore do Spring AI persiste os dados como um Map (ID -> Documento)
            Map<String, CachedDocumentDTO> data = mapper.readValue(
                    file,
                    new TypeReference<>() {
                    }
            );

            log.info("Success: {} document(s) with embeddings loaded.", data.size());
            return new ArrayList<>(data.values());

        } catch (IOException e) {
            log.error("Error parsing cache JSON", e);
            return new ArrayList<>();
        }
    }
}