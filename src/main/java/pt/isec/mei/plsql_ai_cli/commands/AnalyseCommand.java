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

    @ShellMethod(key = "analyze-results", value = "Analisa a consistência semântica das explicações via clustering (Weighted-AST) e gera métricas de instabilidade em MD e CSV.")
    public String analyzeResults() {
        List<CachedDocumentDTO> docs = cacheLoaderService.loadAllDocuments();

        if (docs.isEmpty()) {
            return "Erro: Cache vazio. Execute 'vectorize-all' primeiro.";
        }

        List<ProcedureAnalysisResult> results = clusteringService.analyzeAllProcedures(docs);

        return reportService.generateAndSaveReport(results);
    }
}