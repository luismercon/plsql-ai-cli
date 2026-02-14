package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.utils.VectorUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportService {

    private static final String REPORT_DIR = "reports";

    public String generateAndSaveReport(List<ProcedureAnalysisResult> results) {
        // 1. Gerar o Conteúdo do Relatório
        String reportContent = buildReportString(results);

        // 2. Salvar no Disco
        saveReportToFile(reportContent);

        return reportContent;
    }

    private String buildReportString(List<ProcedureAnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RELATÓRIO DE ANÁLISE SEMÂNTICA (PL/SQL AI)\n"); // Usei # para Título H1
        sb.append("**Data:** ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("  \n");
        sb.append("---\n\n");

        for (ProcedureAnalysisResult res : results) {
            sb.append("## 📂 PROCEDURE: ").append(res.procedureName()).append(" (").append(res.totalFiles()).append(" arquivos)\n");

            if (res.isTrivial()) {
                sb.append("> ✅ **CASO TRIVIAL** (1 versão única). Entregar direto.\n\n");
                continue;
            }

            // Decisão
            sb.append(String.format("- **Similaridade Inter-Representantes:** %.2f%%\n", res.interRepSimilarity() * 100));

            if ("UNANIMIDADE".equals(res.systemDecision())) {
                sb.append("- 🤖 **DECISÃO DO SISTEMA:** UNANIMIDADE (Grupos fundidos).\n");
                sb.append("  - ➡️ **Ação:** Enviar APENAS 1 arquivo.\n");
                sb.append("  - 👑 **Eleito:** `").append(res.recommendedWinner().getMetadata().get("filename")).append("`\n");
            } else {
                sb.append("- 🤖 **DECISÃO DO SISTEMA:** AMBIGUIDADE DETETADA.\n");
                sb.append("  - ➡️ **Ação:** Enviar 2 arquivos para revisão.\n");
                sb.append("  - 1️⃣ **Opção A:** `").append(res.recommendedWinner().getMetadata().get("filename")).append("`\n");
                sb.append("  - 2️⃣ **Opção B:** `").append(res.alternativeOption().getMetadata().get("filename")).append("`\n");
            }
            sb.append("\n"); // Linha em branco para separar blocos

            // Detalhes Técnicos
            sb.append("### 📊 Detalhes Estatísticos\n");
            for (Map.Entry<Integer, List<Document>> entry : res.clusters().entrySet()) {
                List<Document> docs = entry.getValue();
                RealVector centroid = calculateCentroid(docs);

                // Cabeçalho do Cluster (H4)
                sb.append("\n#### 🔷 CLUSTER ").append(entry.getKey()).append(" (").append(docs.size()).append(" documentos)\n");

                // Ordenação visual
                docs.sort((d1, d2) -> Double.compare(
                        VectorUtils.cosineSimilarity(extractVector(d2), centroid),
                        VectorUtils.cosineSimilarity(extractVector(d1), centroid)
                ));

                for (Document d : docs) {
                    double score = VectorUtils.cosineSimilarity(extractVector(d), centroid);
                    boolean isWinner = (d == res.recommendedWinner()) || (d == res.alternativeOption());

                    // CORREÇÃO AQUI: Usando sintaxe de lista Markdown (- )
                    sb.append(String.format("- %s `[%.4f]` %s **%s** (Peso: %s)\n",
                            isWinner ? "👑" : "", // Ícone
                            score,                // Score
                            isWinner ? "**REPRESENTANTE**" : "", // Negrito se for líder
                            d.getMetadata().get("filename"),     // Nome do arquivo
                            d.getMetadata().get("cluster_weight") // Peso
                    ));
                }
            }
            sb.append("\n---\n\n"); // Separador horizontal entre procedures
        }
        return sb.toString();
    }

    private void saveReportToFile(String content) {
        try {
            Path dir = Paths.get(REPORT_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);

            String filename = "report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".md";
            Path file = dir.resolve(filename);

            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("Report saved to: {}", file.toAbsolutePath());

            // Adiciona uma linha ao final do output de tela para avisar o usuário
            System.out.println("\n[INFO] Relatório salvo em: " + file.toString());

        } catch (IOException e) {
            log.error("Failed to save report", e);
        }
    }

    // Helpers repetidos (Idealmente estariam no VectorUtils, mas para rapidez ficam aqui ou lá)
    private RealVector extractVector(Document d) {
        return VectorUtils.toRealVector((List<Double>) d.getMetadata().get("custom_embedding"));
    }

    private RealVector calculateCentroid(List<Document> docs) {
        return VectorUtils.calculateCentroid(docs.stream().map(this::extractVector).toList());
    }
}