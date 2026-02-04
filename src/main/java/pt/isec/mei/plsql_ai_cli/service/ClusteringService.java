package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.utils.VectorUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ClusteringService {

    private static final double DUPLICATE_THRESHOLD = 0.96;

    /**
     * Agora trabalhamos com CachedDocumentDto que garante acesso ao embedding.
     */
    public List<Document> deduplicate(List<CachedDocumentDTO> dtos) {
        if (dtos.isEmpty()) return new ArrayList<>();

        log.info("--- Starting Deduplication (using DTOs) ---");

        List<Document> uniqueDocs = new ArrayList<>();
        boolean[] merged = new boolean[dtos.size()];

        // Extrair vetores dos DTOs
        List<RealVector> vectors = new ArrayList<>();
        for (CachedDocumentDTO dto : dtos) {
            vectors.add(dto.getRealVector());
        }

        for (int i = 0; i < dtos.size(); i++) {
            if (merged[i]) continue;
            if (vectors.get(i) == null) continue;

            CachedDocumentDTO baseDto = dtos.get(i);
            RealVector baseVector = vectors.get(i);
            int weight = 1;

            for (int j = i + 1; j < dtos.size(); j++) {
                if (merged[j]) continue;
                if (vectors.get(j) == null) continue;

                double similarity = VectorUtils.cosineSimilarity(baseVector, vectors.get(j));

                if (similarity >= DUPLICATE_THRESHOLD) {
                    log.debug("MERGE: '{}' absorbed '{}' (Score: {:.4f})",
                            baseDto.getMetadata().get("filename"),
                            dtos.get(j).getMetadata().get("filename"),
                            similarity);
                    weight++;
                    merged[j] = true;
                }
            }

            // Criar o Documento final (retornando ao padrão Spring AI)
            uniqueDocs.add(createWeightedDocument(baseDto, weight));
        }

        log.info("Deduplication: {} -> {} documents.", dtos.size(), uniqueDocs.size());
        return uniqueDocs;
    }

    // --- PARTE 2: K-MEANS CLUSTERING ---

    /**
     * Aplica K-Means simples para dividir os documentos em K clusters.
     * Retorna um Mapa onde a Chave é o ID do Cluster (0 ou 1) e o Valor é a lista de documentos.
     */
    public Map<Integer, List<Document>> clusterKMeans(List<Document> documents, int k) {
        if (documents.size() < k) {
            log.warn("Not enough documents for K-Means (Docs: {}, K: {}). Returning all in Cluster 0.", documents.size(), k);
            return Map.of(0, documents);
        }

        log.info("--- Starting K-Means (K={}) ---", k);

        // 1. Inicialização: Escolher centróides aleatórios iniciais
        // Melhoria: Poderíamos usar K-Means++, mas random funciona para este volume.
        List<RealVector> centroids = new ArrayList<>();
        List<RealVector> vectors = extractVectors(documents); // Helper para extrair dos metadados

        // Pega os K primeiros documentos como centróides iniciais (simples e determinístico para testes)
        for (int i = 0; i < k; i++) {
            centroids.add(vectors.get(i));
        }

        Map<Integer, List<Document>> clusters = new HashMap<>();
        int maxIterations = 10; // Convergência rápida esperada
        boolean changed = true;

        for (int iter = 0; iter < maxIterations && changed; iter++) {
            // Limpa clusters
            clusters.clear();
            for (int i = 0; i < k; i++) clusters.put(i, new ArrayList<>());

            // Passo de Atribuição: Cada documento vai para o centróide mais próximo
            List<Integer> assignments = new ArrayList<>();

            for (int i = 0; i < vectors.size(); i++) {
                RealVector vec = vectors.get(i);
                int bestCluster = 0;
                double maxSim = -1.0; // Cosseno: quanto maior, melhor (perto de 1.0)

                for (int c = 0; c < k; c++) {
                    double sim = VectorUtils.cosineSimilarity(vec, centroids.get(c));
                    if (sim > maxSim) {
                        maxSim = sim;
                        bestCluster = c;
                    }
                }

                clusters.get(bestCluster).add(documents.get(i));
                assignments.add(bestCluster);
            }

            // Passo de Atualização: Recalcular centróides
            List<RealVector> newCentroids = new ArrayList<>();
            boolean currentIterChanged = false;

            for (int c = 0; c < k; c++) {
                List<Document> clusterDocs = clusters.get(c);
                if (clusterDocs.isEmpty()) {
                    // Se um cluster ficou vazio, mantém o antigo (edge case)
                    newCentroids.add(centroids.get(c));
                    continue;
                }

                List<RealVector> clusterVectors = extractVectors(clusterDocs);
                RealVector newCentroid = VectorUtils.calculateCentroid(clusterVectors);
                newCentroids.add(newCentroid);

                // Verifica se o centróide mudou significativamente
                if (VectorUtils.cosineSimilarity(centroids.get(c), newCentroid) < 0.999) {
                    currentIterChanged = true;
                }
            }

            centroids = newCentroids;
            changed = currentIterChanged;
            log.debug("K-Means Iteration {}: Changed? {}", iter + 1, changed);
        }

        return clusters;
    }

    // --- PARTE 3: SELEÇÃO DE MEDOIDE (REPRESENTANTE) ---

    /**
     * Encontra o documento real que está mais próximo do centro matemático do cluster.
     * Esse documento será o "Resumo" ou "Representante" do grupo.
     */
    public Document findMedoid(List<Document> clusterDocs) {
        if (clusterDocs.isEmpty()) return null;
        if (clusterDocs.size() == 1) return clusterDocs.get(0);

        List<RealVector> vectors = extractVectors(clusterDocs);
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

    // --- HELPER ---

    // Extrai RealVectors da lista de Documentos (lendo do Metadata "custom_embedding")
    private List<RealVector> extractVectors(List<Document> docs) {
        List<RealVector> result = new ArrayList<>();
        for (Document d : docs) {
            Object meta = d.getMetadata().get("custom_embedding");

            if (meta instanceof List) {
                result.add(VectorUtils.toRealVector((List<Double>) meta));
            } else {
                // Log de erro mais detalhado para debug
                log.error("CRITICAL: Missing embedding for K-Means in file: {}", d.getMetadata().get("filename"));
                throw new RuntimeException("Missing embedding for K-Means. Pipeline broken.");
            }
        }
        return result;
    }

    // Substitua este método no final da classe ClusteringService.java

    private Document createWeightedDocument(CachedDocumentDTO dto, int weight) {
        // 1. Copia os metadados originais
        Map<String, Object> newMeta = new HashMap<>();
        if (dto.getMetadata() != null) {
            newMeta.putAll(dto.getMetadata());
        }

        // 2. Adiciona o peso do cluster
        newMeta.put("cluster_weight", weight);

        // 3. CRÍTICO: Injeta o vetor explicitamente nos metadados
        // Sem isso, o K-Means não consegue ler o vetor depois
        if (dto.getEmbedding() != null) {
            newMeta.put("custom_embedding", dto.getEmbedding());
        } else {
            log.warn("Warning: Dropping embedding for document {} during conversion!", dto.getBody());
        }

        // Retorna o Documento pronto para o K-Means
        return new Document(dto.getBody(), newMeta);
    }

}