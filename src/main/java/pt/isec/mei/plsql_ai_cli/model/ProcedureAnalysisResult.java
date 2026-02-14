package pt.isec.mei.plsql_ai_cli.model;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

// DTO para transportar a análise completa de uma procedure
public record ProcedureAnalysisResult(
        String procedureName,
        int totalFiles,
        List<Document> uniqueDocs,
        boolean isTrivial,              // Se tem apenas 1 versão (não precisa de cluster)
        Map<Integer, List<Document>> clusters,
        double interRepSimilarity,      // Similaridade entre os líderes (0.0 se trivial)
        double instabilityScore, // Medida de divergência (1.0 - Similarity)
        String systemDecision,          // "UNANIMIDADE" ou "AMBIGUIDADE"
        Document recommendedWinner,     // O documento sugerido
        Document alternativeOption      // A 2ª opção (se houver ambiguidade)
) {
}