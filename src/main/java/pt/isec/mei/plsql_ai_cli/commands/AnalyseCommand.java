package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.service.CacheLoadService;
import pt.isec.mei.plsql_ai_cli.service.ClusteringService;
import pt.isec.mei.plsql_ai_cli.service.ReportService;

import java.util.List;

@ShellComponent
@AllArgsConstructor
public class AnalyseCommand {

    private final CacheLoadService cacheLoaderService;
    private final ClusteringService clusteringService;
    private final ReportService reportService;

    @ShellMethod(key = "analyze-results", value = "Fase 4: Executa análise de instabilidade e gera relatórios MD e CSV.")
    public String analyzeResults() {
        // 1. Carregar do Cache (Vetorizados pelo Codestral)
        List<CachedDocumentDTO> rawDocs = cacheLoaderService.loadAllDocuments();
        if (rawDocs.isEmpty()) return "Erro: Cache vazio. Execute 'vectorize-all' primeiro.";

        // 2. Processar Lógica de Clustering e Instabilidade
        List<ProcedureAnalysisResult> results = clusteringService.analyzeAllProcedures(rawDocs);

        // 3. Gerar Outputs Finais (Markdown para humanos, CSV para estatísticas)
        return reportService.generateAndSaveReport(results);
    }
}