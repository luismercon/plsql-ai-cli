package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.service.CacheLoadService;
import pt.isec.mei.plsql_ai_cli.service.ClusteringService;
import pt.isec.mei.plsql_ai_cli.utils.VectorUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ShellComponent
@AllArgsConstructor
public class ClusterTestCommand {

    private final CacheLoadService cacheLoaderService;
    private final ClusteringService clusteringService;

    // CONFIGURAÇÕES GLOBAIS
    private static final double DUPLICATE_THRESHOLD = 0.96;
    private static final double SIMILARITY_THRESHOLD = 0.95;
    private static final int K_MEANS_CLUSTERS = 2;

    @ShellMethod(key = "test-cluster", value = "Relatório Completo: Decisão + Dados Estatísticos")
    public String testClustering() {
        List<CachedDocumentDTO> rawDocs = cacheLoaderService.loadAllDocuments();
        if (rawDocs.isEmpty()) return "Cache vazio.";

        Map<String, List<CachedDocumentDTO>> docsByProcedure = groupDocumentsByProcedure(rawDocs);

        StringBuilder sb = new StringBuilder();

        // --- CABEÇALHO GERAL ---
        sb.append("\n======================================================\n");
        sb.append("   RELATÓRIO DE ANÁLISE SEMÂNTICA (PL/SQL AI)\n");
        sb.append("======================================================\n");
        sb.append("🔍 Configurações do Algoritmo:\n");
        sb.append(String.format("   • Deduplicação (Threshold):   %.2f (96%%)\n", DUPLICATE_THRESHOLD));
        sb.append(String.format("   • Fusão de Clusters (Simil):  %.2f (95%%)\n", SIMILARITY_THRESHOLD));
        sb.append(String.format("   • K-Means Target:             %d Clusters\n", K_MEANS_CLUSTERS));
        sb.append("------------------------------------------------------\n");
        sb.append(String.format("📂 Total Procedures Encontradas: %d\n", docsByProcedure.size()));
        sb.append(String.format("📄 Total Documentos Processados: %d\n", rawDocs.size()));
        sb.append("======================================================\n");


        for (Map.Entry<String, List<CachedDocumentDTO>> entry : docsByProcedure.entrySet()) {
            String procName = entry.getKey();
            List<CachedDocumentDTO> procDocs = entry.getValue();

            sb.append("\n\n**************************************************\n");
            sb.append(String.format("📂 PROCEDURE: %s (%d arquivos)\n", procName, procDocs.size()));
            sb.append("**************************************************\n");

            // 1. Deduplicar
            List<Document> uniqueDocs = clusteringService.deduplicate(procDocs);

            if (uniqueDocs.size() < 2) {
                sb.append("   ✅ CASO TRIVIAL (1 versão única). Entregar direto.\n");
                continue;
            }

            // 2. K-Means (K=2)
            Map<Integer, List<Document>> clusters = clusteringService.clusterKMeans(uniqueDocs, K_MEANS_CLUSTERS);
            Document medoid0 = clusteringService.findMedoid(clusters.get(0));
            Document medoid1 = clusteringService.findMedoid(clusters.get(1));

            // --- BLOCO 1: A DECISÃO DO SISTEMA (SIMULAÇÃO) ---
            if (medoid0 != null && medoid1 != null) {
                double similarity = calculateSimilarity(medoid0, medoid1);

                sb.append(String.format("   📊 Similaridade Inter-Representantes: %.4f (%.2f%%)\n", similarity, similarity * 100));
                sb.append("   ----------------------------------------------\n");
                sb.append("   🤖 DECISÃO AUTOMÁTICA:\n");

                if (similarity >= SIMILARITY_THRESHOLD) {
                    Document winner = (clusters.get(0).size() >= clusters.get(1).size()) ? medoid0 : medoid1;
                    sb.append("   ✅ UNANIMIDADE (Grupos fundidos).\n");
                    sb.append("   ➡️  Ação: Enviar APENAS 1 arquivo (Gold Standard).\n");
                    sb.append("   👑  Eleito: " + winner.getMetadata().get("filename") + "\n");
                } else {
                    sb.append("   ⚠️  AMBIGUIDADE (Diferenças relevantes).\n");
                    sb.append("   ➡️  Ação: Enviar 2 arquivos para revisão humana.\n");
                    sb.append("   1️⃣  Opção A: " + medoid0.getMetadata().get("filename") + "\n");
                    sb.append("   2️⃣  Opção B: " + medoid1.getMetadata().get("filename") + "\n");
                }
                sb.append("   ----------------------------------------------\n");
            }

            // --- BLOCO 2: DADOS TÉCNICOS DETALHADOS ---
            sb.append("\n   [ANÁLISE ESTATÍSTICA DETALHADA]\n");

            for (Map.Entry<Integer, List<Document>> clusterEntry : clusters.entrySet()) {
                Integer clusterId = clusterEntry.getKey();
                List<Document> docs = clusterEntry.getValue();

                // Cálculos para o detalhe
                RealVector clusterCentroid = calculateClusterCentroid(docs);
                Document representative = clusteringService.findMedoid(docs);

                sb.append(String.format("\n   🔷 CLUSTER %d (%d documentos)\n", clusterId, docs.size()));

                // Ordenação por coesão (proximidade ao centro do cluster)
                docs.sort((d1, d2) -> {
                    double sim1 = VectorUtils.cosineSimilarity(extractVector(d1), clusterCentroid);
                    double sim2 = VectorUtils.cosineSimilarity(extractVector(d2), clusterCentroid);
                    return Double.compare(sim2, sim1);
                });

                for (Document d : docs) {
                    boolean isRep = (d == representative);
                    double score = VectorUtils.cosineSimilarity(extractVector(d), clusterCentroid);
                    int weight = (Integer) d.getMetadata().get("cluster_weight");

                    sb.append(String.format("      %s [%.4f] %s (Peso: %d) %s\n",
                            isRep ? "👑" : "-",
                            score,
                            d.getMetadata().get("filename"),
                            weight,
                            isRep ? "<- REPRESENTANTE" : ""
                    ));
                }
            }
        }
        return sb.toString();
    }

    // --- MÉTODOS AUXILIARES ---

    private Map<String, List<CachedDocumentDTO>> groupDocumentsByProcedure(List<CachedDocumentDTO> allDocs) {
        return allDocs.stream()
                .collect(Collectors.groupingBy(dto -> {
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

    private RealVector extractVector(Document d) {
        Object meta = d.getMetadata().get("custom_embedding");
        if (meta instanceof List) {
            return VectorUtils.toRealVector((List<Double>) meta);
        }
        return new org.apache.commons.math3.linear.ArrayRealVector(1);
    }

    private double calculateSimilarity(Document d1, Document d2) {
        return VectorUtils.cosineSimilarity(extractVector(d1), extractVector(d2));
    }

    private RealVector calculateClusterCentroid(List<Document> docs) {
        List<RealVector> vectors = new ArrayList<>();
        for (Document d : docs) {
            vectors.add(extractVector(d));
        }
        return VectorUtils.calculateCentroid(vectors);
    }
}