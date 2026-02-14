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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        // 1. Gerar e Salvar Markdown (Para humanos)
        String reportContent = buildReportString(results);
        saveToFile(reportContent, "report_" + timestamp + ".md");

        // 2. Gerar e Salvar CSV (Para análise de dados/dissertação)
        String csvContent = buildCsvString(results);
        saveToFile(csvContent, "summary_" + timestamp + ".csv");

        return reportContent;
    }

    private String buildReportString(List<ProcedureAnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RELATÓRIO DE ANÁLISE SEMÂNTICA (SPL INFORMIX - CODESTRAL)\n");
        sb.append("**Data:** ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("  \n");
        sb.append("**Fundamentação:** Weighted-AST & Semantic Captioning (SQL2Text)  \n");
        sb.append("---\n\n");

        for (ProcedureAnalysisResult res : results) {
            double instability = 1.0 - res.interRepSimilarity();

            sb.append("## 📂 PROCEDURE: ").append(res.procedureName()).append("\n");
            sb.append(String.format("- **Total de Execuções:** %d\n", res.totalFiles()));
            sb.append(String.format("- **Similaridade Inter-Representantes:** %.4f\n", res.interRepSimilarity()));
            sb.append(String.format("- **Score de Instabilidade:** %.4f %s\n",
                    instability, instability > 0.1 ? "⚠️" : "✅"));

            if (res.isTrivial()) {
                sb.append("> ✅ **CONSENSO TOTAL:** Apenas 1 variante semântica detectada.\n\n");
            } else {
                sb.append(String.format("- **Decisão:** %s\n", res.systemDecision()));
                sb.append("### 👑 Representantes Eleitos\n");
                appendWinnerInfo(sb, "A (Principal)", res.recommendedWinner());
                if (res.alternativeOption() != null) {
                    appendWinnerInfo(sb, "B (Alternativa)", res.alternativeOption());
                }
            }

            // Detalhes dos Clusters
            sb.append("\n### 📊 Distribuição dos Clusters\n");
            for (Map.Entry<Integer, List<Document>> entry : res.clusters().entrySet()) {
                sb.append(String.format("\n#### 🔷 CLUSTER %d (%d docs)\n", entry.getKey(), entry.getValue().size()));
                for (Document d : entry.getValue()) {
                    String filename = (String) d.getMetadata().get("filename");
                    boolean isRep = d == res.recommendedWinner() || d == res.alternativeOption();
                    sb.append(String.format("- %s `%s` → **Técnica:** %s\n",
                            isRep ? "👑" : "-", filename, translateTechnique(filename)));
                }
            }
            sb.append("\n---\n\n");
        }
        return sb.toString();
    }

    private String buildCsvString(List<ProcedureAnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        // Header do CSV
        sb.append("procedure;total_files;similarity;instability;decision;winner_file;winner_technique\n");

        for (ProcedureAnalysisResult res : results) {
            String winnerFile = (String) res.recommendedWinner().getMetadata().get("filename");
            sb.append(String.format("%s;%d;%.4f;%.4f;%s;%s;%s\n",
                    res.procedureName(),
                    res.totalFiles(),
                    res.interRepSimilarity(),
                    1.0 - res.interRepSimilarity(),
                    res.systemDecision(),
                    winnerFile,
                    translateTechnique(winnerFile)
            ));
        }
        return sb.toString();
    }

    /**
     * Traduz o nome do arquivo para a técnica baseada na metodologia dos artigos.
     * Ex: fs_dirty_2026... -> Few-Shot com Ruído
     */
    private String translateTechnique(String filename) {
        if (filename == null) return "Unknown";
        String fn = filename.toLowerCase();

        String technique = "Unknown";
        if (fn.startsWith("ss")) technique = "Single-Shot";
        else if (fn.startsWith("fs")) technique = "Few-Shot";
        else if (fn.startsWith("cot")) technique = "Chain-of-Thought";

        String context = "Unknown";
        if (fn.contains("clean")) context = "Clean Code";
        else if (fn.contains("dirty")) context = "Legacy/Dirty Code";
        else if (fn.contains("raw")) context = "Raw Prompt";

        return technique + " (" + context + ")";
    }

    private void appendWinnerInfo(StringBuilder sb, String label, Document doc) {
        String fname = (String) doc.getMetadata().get("filename");
        sb.append(String.format("- **Opção %s:** `%s`  \n", label, fname));
        sb.append(String.format("  - *Técnica:* %s  \n", translateTechnique(fname)));
        sb.append(String.format("  - *Peso no Consenso:* %d  \n", doc.getMetadata().get("cluster_weight")));
    }

    private void saveToFile(String content, String filename) {
        try {
            Path dir = Paths.get(REPORT_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            System.out.println("[INFO] Arquivo salvo: " + file);
        } catch (IOException e) {
            log.error("Erro ao salvar arquivo: {}", filename, e);
        }
    }

    private RealVector extractVector(Document d) {
        return VectorUtils.toRealVector((List<Double>) d.getMetadata().get("custom_embedding"));
    }

    private RealVector calculateCentroid(List<Document> docs) {
        return VectorUtils.calculateCentroid(docs.stream().map(this::extractVector).toList());
    }
}