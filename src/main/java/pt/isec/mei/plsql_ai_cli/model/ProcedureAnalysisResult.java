package pt.isec.mei.plsql_ai_cli.model;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

public record ProcedureAnalysisResult(
        String procedureName,
        int totalFiles,
        List<Document> uniqueDocs,
        boolean isTrivial,              // Verdadeiro se existir apenas 1 versão única
        Map<Integer, List<Document>> clusters,
        double interRepSimilarity,      // Similaridade entre os representantes dos clusters
        double instabilityScore,        // Score de divergência (1.0 - similaridade)
        String systemDecision,          // UNANIMIDADE ou AMBIGUIDADE
        Document recommendedWinner,     // Documento principal eleito
        Document alternativeOption      // Segunda opção em caso de ambiguidade
) {
}