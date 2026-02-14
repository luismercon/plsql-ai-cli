package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.utils.VectorUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClusteringService {

    // --- CONFIGURAÇÕES DO ALGORITMO ---
    private static final double DUPLICATE_THRESHOLD = 0.96; // Se > 96% igual, funde no mesmo documento
    private static final double SIMILARITY_THRESHOLD = 0.95; // Se > 95% igual entre grupos, é unânime
    private static final int K_MEANS_CLUSTERS = 2; // Queremos dividir em 2 grupos (ex: Consistente vs Alucinação)

    // --- MÉTODOS PÚBLICOS (API) ---

    /**
     * Ponto de entrada principal. Recebe todos os documentos crus, agrupa por procedure,
     * e executa a análise completa para cada uma.
     */
    public List<ProcedureAnalysisResult> analyzeAllProcedures(List<CachedDocumentDTO> rawDocs) {
        // 1. Agrupar por pasta (Procedure)
        Map<String, List<CachedDocumentDTO>> docsByProcedure = groupDocumentsByProcedure(rawDocs);
        List<ProcedureAnalysisResult> results = new ArrayList<>();

        // 2. Analisar cada grupo independentemente
        for (Map.Entry<String, List<CachedDocumentDTO>> entry : docsByProcedure.entrySet()) {
            results.add(analyzeSingleProcedure(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    // --- ORQUESTRAÇÃO DA ANÁLISE ---

    private ProcedureAnalysisResult analyzeSingleProcedure(String procName, List<CachedDocumentDTO> procDocs) {
        log.info("Analyzing procedure: {} ({} files)", procName, procDocs.size());

        // ETAPA 1: Deduplicação (Soft Merge)
        List<Document> uniqueDocs = deduplicate(procDocs);

        // Caso Trivial: Se só sobrou 1 (ou 0), não há clusterização a fazer
        if (uniqueDocs.size() < 2) {
            return new ProcedureAnalysisResult(
                    procName, procDocs.size(), uniqueDocs, true,
                    new HashMap<>(), 0.0, "TRIVIAL",
                    uniqueDocs.isEmpty() ? null : uniqueDocs.get(0), null
            );
        }

        // ETAPA 2: Clustering K-Means
        Map<Integer, List<Document>> clusters = clusterKMeans(uniqueDocs, K_MEANS_CLUSTERS);

        // ETAPA 3: Encontrar Representantes (Medoides)
        Document medoid0 = findMedoid(clusters.get(0));
        Document medoid1 = findMedoid(clusters.get(1));

        // ETAPA 4: Tomada de Decisão (Regra de Ouro)
        double similarity = 0.0;
        String decision = "AMBIGUIDADE";
        Document winner = null;
        Document alternative = null;

        if (medoid0 != null && medoid1 != null) {
            similarity = calculateSimilarity(medoid0, medoid1);

            if (similarity >= SIMILARITY_THRESHOLD) {
                // Se os grupos são quase idênticos, fundimos a decisão
                decision = "UNANIMIDADE";
                // O vencedor é o medóide do cluster maior (representa a maioria)
                winner = (clusters.get(0).size() >= clusters.get(1).size()) ? medoid0 : medoid1;
            } else {
                // Se são diferentes, mantemos as duas opções
                decision = "AMBIGUIDADE";
                winner = medoid0;     // Opção A
                alternative = medoid1; // Opção B
            }
        } else {
            // Fallback se algo estranho acontecer (cluster vazio)
            winner = (medoid0 != null) ? medoid0 : medoid1;
            decision = "TRIVIAL"; // Só um cluster populado
        }

        return new ProcedureAnalysisResult(
                procName, procDocs.size(), uniqueDocs, false,
                clusters, similarity, decision, winner, alternative
        );
    }

    // --- ALGORITMOS MATEMÁTICOS ---

    /**
     * Algoritmo de Deduplicação (Soft Merge).
     * Compara N x N documentos. Se similaridade > 0.96, funde e aumenta o peso.
     */
    public List<Document> deduplicate(List<CachedDocumentDTO> dtos) {
        if (dtos.isEmpty()) return new ArrayList<>();

        List<Document> uniqueDocs = new ArrayList<>();
        boolean[] merged = new boolean[dtos.size()];

        // Pré-carrega vetores para performance
        List<RealVector> vectors = new ArrayList<>();
        for (CachedDocumentDTO dto : dtos) {
            vectors.add(VectorUtils.toRealVector(dto.getEmbedding()));
        }

        for (int i = 0; i < dtos.size(); i++) {
            if (merged[i]) continue;

            CachedDocumentDTO baseDto = dtos.get(i);
            RealVector baseVector = vectors.get(i);
            int weight = 1;

            for (int j = i + 1; j < dtos.size(); j++) {
                if (merged[j]) continue;

                RealVector candidateVector = vectors.get(j);
                double sim = VectorUtils.cosineSimilarity(baseVector, candidateVector);

                if (sim >= DUPLICATE_THRESHOLD) {
                    weight++;
                    merged[j] = true; // Marca como absorvido
                }
            }
            uniqueDocs.add(createWeightedDocument(baseDto, weight));
        }
        return uniqueDocs;
    }

    /**
     * K-Means Clustering Simples.
     */
    public Map<Integer, List<Document>> clusterKMeans(List<Document> documents, int k) {
        // Fallback se não houver documentos suficientes para K clusters
        if (documents.size() < k) {
            Map<Integer, List<Document>> fallback = new HashMap<>();
            fallback.put(0, documents);
            for (int i = 1; i < k; i++) fallback.put(i, new ArrayList<>());
            return fallback;
        }

        // 1. Inicialização: Escolhe os K primeiros como centróides iniciais
        List<RealVector> centroids = new ArrayList<>();
        List<RealVector> vectors = extractVectorsList(documents);
        for (int i = 0; i < k; i++) centroids.add(vectors.get(i));

        Map<Integer, List<Document>> clusters = new HashMap<>();
        boolean changed = true;
        int maxIterations = 20; // Limite de segurança

        for (int iter = 0; iter < maxIterations && changed; iter++) {
            // Limpa clusters
            clusters.clear();
            for (int i = 0; i < k; i++) clusters.put(i, new ArrayList<>());

            // Atribuição
            for (int i = 0; i < vectors.size(); i++) {
                RealVector vec = vectors.get(i);
                int bestCluster = 0;
                double maxSim = -1.0;

                for (int c = 0; c < k; c++) {
                    double sim = VectorUtils.cosineSimilarity(vec, centroids.get(c));
                    if (sim > maxSim) {
                        maxSim = sim;
                        bestCluster = c;
                    }
                }
                clusters.get(bestCluster).add(documents.get(i));
            }

            // Atualização de Centróides
            List<RealVector> newCentroids = new ArrayList<>();
            boolean currentIterChanged = false;

            for (int c = 0; c < k; c++) {
                List<Document> clusterDocs = clusters.get(c);
                if (clusterDocs.isEmpty()) {
                    newCentroids.add(centroids.get(c));
                    continue;
                }

                RealVector newCentroid = VectorUtils.calculateCentroid(extractVectorsList(clusterDocs));
                newCentroids.add(newCentroid);

                // Se o centróide moveu menos que 0.0001, consideramos estável
                if (VectorUtils.cosineSimilarity(centroids.get(c), newCentroid) < 0.9999) {
                    currentIterChanged = true;
                }
            }
            centroids = newCentroids;
            changed = currentIterChanged;
        }
        return clusters;
    }

    /**
     * Encontra o Medoide (Documento real mais próximo do centro matemático do cluster).
     */
    public Document findMedoid(List<Document> clusterDocs) {
        if (clusterDocs == null || clusterDocs.isEmpty()) return null;
        if (clusterDocs.size() == 1) return clusterDocs.get(0);

        List<RealVector> vectors = extractVectorsList(clusterDocs);
        RealVector centroid = VectorUtils.calculateCentroid(vectors);

        Document bestDoc = null;
        double bestSim = -1.0;

        for (int i = 0; i < clusterDocs.size(); i++) {
            double sim = VectorUtils.cosineSimilarity(centroid, vectors.get(i));
            if (sim > bestSim) {
                bestSim = sim;
                bestDoc = clusterDocs.get(i);
            }
        }
        return bestDoc;
    }

    // --- MÉTODOS AUXILIARES ---

    // Converte DTO para Document e injeta metadados vitais
    private Document createWeightedDocument(CachedDocumentDTO dto, int weight) {
        Map<String, Object> newMeta = new HashMap<>();
        if (dto.getMetadata() != null) {
            newMeta.putAll(dto.getMetadata());
        }

        newMeta.put("cluster_weight", weight);

        // CRÍTICO: Injeta o embedding nos metadados para uso posterior no K-Means
        if (dto.getEmbedding() != null) {
            newMeta.put("custom_embedding", dto.getEmbedding());
        } else {
            log.warn("Document {} has no embedding!", dto.getBody());
        }

        return new Document(dto.getBody(), newMeta);
    }

    private Map<String, List<CachedDocumentDTO>> groupDocumentsByProcedure(List<CachedDocumentDTO> allDocs) {
        return allDocs.stream().collect(Collectors.groupingBy(dto -> {
            String fullPath = (String) dto.getMetadata().get("path");
            if (fullPath == null) return "UNKNOWN";
            try {
                var parent = Paths.get(fullPath).getParent();
                return parent != null ? parent.getFileName().toString() : "ROOT";
            } catch (Exception e) {
                return "UNKNOWN";
            }
        }));
    }

    private List<RealVector> extractVectorsList(List<Document> docs) {
        List<RealVector> result = new ArrayList<>();
        for (Document d : docs) {
            result.add(extractVector(d));
        }
        return result;
    }

    private RealVector extractVector(Document d) {
        Object meta = d.getMetadata().get("custom_embedding");
        if (meta instanceof List) {
            return VectorUtils.toRealVector((List<Double>) meta);
        }
        throw new RuntimeException("Vector missing in document metadata: " + d.getMetadata().get("filename"));
    }

    private double calculateSimilarity(Document d1, Document d2) {
        return VectorUtils.cosineSimilarity(extractVector(d1), extractVector(d2));
    }
}