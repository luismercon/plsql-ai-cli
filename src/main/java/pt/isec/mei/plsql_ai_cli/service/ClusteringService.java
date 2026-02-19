package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.utils.VectorUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ClusteringService {

    private static final double DUPLICATE_THRESHOLD = 0.96;
    private static final double ALTERNATIVE_CLUSTER_THRESHOLD = 0.90;
    private static final double MAX_SIMILARITY_SCORE = 1.0;


    public List<ProcedureAnalysisResult> analyzeAllProcedures(List<CachedDocumentDTO> allDocs) {
        if (allDocs == null || allDocs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<CachedDocumentDTO>> groupedByProcedure = allDocs.stream()
                .collect(Collectors.groupingBy(dto -> {
                    String path = (String) dto.getMetadata().get("path");
                    return extractProcedureFromPathOrFile(path != null ? path : (String) dto.getMetadata().get("filename"));
                }));

        List<ProcedureAnalysisResult> finalResults = new ArrayList<>();
        for (Map.Entry<String, List<CachedDocumentDTO>> entry : groupedByProcedure.entrySet()) {
            List<Document> weightedDocs = deduplicate(entry.getValue());
            finalResults.add(performFullAnalysis(entry.getKey(), weightedDocs, entry.getValue().size()));
        }
        return finalResults;
    }


    private List<Document> deduplicate(List<CachedDocumentDTO> dtos) {
        List<RealVector> vectors = dtos.stream()
                .map(dto -> VectorUtils.toRealVector(dto.getEmbedding()))
                .toList();

        List<Integer> representatives = new ArrayList<>();
        Map<Integer, Integer> weights = new HashMap<>();

        IntStream.range(0, dtos.size()).forEach(i -> {
            int match = representatives.stream()
                    .filter(rep -> VectorUtils.cosineSimilarity(vectors.get(rep), vectors.get(i)) >= DUPLICATE_THRESHOLD)
                    .findFirst()
                    .orElse(-1);

            if (match == -1) {
                representatives.add(i);
                weights.put(i, 1);
            } else {
                weights.merge(match, 1, Integer::sum);
            }
        });

        return representatives.stream()
                .map(i -> createWeightedDocument(dtos.get(i), weights.get(i)))
                .toList();
    }

    private ProcedureAnalysisResult performFullAnalysis(String procedureName, List<Document> uniqueDocs, int totalFiles) {
        // 1. Identifica o Medoide (Vencedor)
        Document winner = uniqueDocs.stream()
                .max(Comparator.comparingInt(d -> (int) d.getMetadata().get("cluster_weight")))
                .orElse(uniqueDocs.get(0));

        // 2. Calcula similaridade individual e separa em clusters manualmente se houver divergência
        List<Document> cluster0 = new ArrayList<>();
        List<Document> cluster1 = new ArrayList<>();

        RealVector winnerVec = VectorUtils.toRealVector(VectorUtils.getEmbeddingFromMetadata(winner));

        for (Document doc : uniqueDocs) {
            RealVector currentVec = VectorUtils.toRealVector(VectorUtils.getEmbeddingFromMetadata(doc));
            double score = VectorUtils.cosineSimilarity(winnerVec, currentVec);

            // Threshold de Rigor: se baixar de ALTERNATIVE_CLUSTER_THRESHOLD, consideramos uma interpretação "Alternativa"
            if (score >= ALTERNATIVE_CLUSTER_THRESHOLD) {
                cluster0.add(doc);
            } else {
                cluster1.add(doc);
            }
        }

        Map<Integer, List<Document>> clusters = new HashMap<>();
        clusters.put(0, cluster0);
        if (!cluster1.isEmpty()) {
            clusters.put(1, cluster1);
        }

        double interRepSimilarity = calculateAverageSimilarity(winner, uniqueDocs);
        String decision = (clusters.size() > 1) ? "AMBIGUIDADE" : "CONSENSO";

        return new ProcedureAnalysisResult(
                procedureName,
                totalFiles,
                uniqueDocs,
                decision.equals("CONSENSO"),
                clusters,
                interRepSimilarity,
                MAX_SIMILARITY_SCORE - interRepSimilarity,
                decision,
                winner,
                cluster1.isEmpty() ? null : cluster1.get(0) // O líder da divergência
        );
    }

    private double calculateAverageSimilarity(Document winner, List<Document> others) {
        if (others.size() <= 1) {
            return MAX_SIMILARITY_SCORE;
        }
        RealVector winnerVec = VectorUtils.toRealVector(VectorUtils.getEmbeddingFromMetadata(winner));
        return others.stream()
                .mapToDouble(d -> VectorUtils.cosineSimilarity(winnerVec, VectorUtils.toRealVector(VectorUtils.getEmbeddingFromMetadata(d))))
                .average().orElse(MAX_SIMILARITY_SCORE);
    }

    private Document createWeightedDocument(CachedDocumentDTO dto, int weight) {
        Map<String, Object> metadata = new HashMap<>(dto.getMetadata());
        metadata.put("cluster_weight", weight);
        metadata.put("custom_embedding", dto.getEmbedding());

        // Injetamos o nome correto da procedure para que o ReportService o encontre facilmente
        String fullPath = (String) metadata.get("path");
        if (fullPath == null) {
            fullPath = (String) metadata.get("filename");
        }

        metadata.put("procedure_name", extractProcedureFromPathOrFile(fullPath));

        return new Document(dto.getBody(), metadata);
    }



    private String extractProcedureFromPathOrFile(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }


        String[] parts = path.replace("\\", "/").split("/");

        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }

        return parts[0].replace(".md", "");
    }

}