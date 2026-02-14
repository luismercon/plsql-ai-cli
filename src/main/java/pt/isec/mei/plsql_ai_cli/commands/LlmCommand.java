package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.service.DocumentService;
import pt.isec.mei.plsql_ai_cli.service.GenerationOrchestratorService;
import pt.isec.mei.plsql_ai_cli.service.OllamaService;
import pt.isec.mei.plsql_ai_cli.service.UserInteractionService;
import pt.isec.mei.plsql_ai_cli.validator.ProceduresFolderValidator;

@ShellComponent
@AllArgsConstructor
public class LlmCommand {

    private final OllamaService ollamaService;
    private final DocumentService documentService;
    private final UserInteractionService userInteractionService;
    private final ProceduresFolderValidator proceduresFolderValidator;
    private final GenerationOrchestratorService orchestratorService;

    @ShellMethod(key = "generate", value = "Gera a análise de uma procedure específica (individual).")
    public String generate() {
        proceduresFolderValidator.validateProceduresFolder();

        String strategy = userInteractionService.promptForStrategy();
        String noiseLevel = userInteractionService.promptForNoiseLevel();
        String procedureName = userInteractionService.promptForProcedure();

        return ollamaService.analyze(noiseLevel, procedureName, strategy);
    }

    @ShellMethod(key = "generate-batch", value = "Gera as 9 variantes para todas as procedures (processamento em massa).")
    public String generateBatch() {
        proceduresFolderValidator.validateProceduresFolder();

        System.out.println("\n==================================================");
        System.out.println("   MODO DE GERAÇÃO EM BATCH (Benchmark Informix)");
        System.out.println("==================================================");
        System.out.println("Este processo analisará TODAS as procedures na pasta de entrada.");
        System.out.println("Cada procedure será submetida a 9 variantes:");
        System.out.println("  • Estratégias: Single Shot, Few Shot, Chain of Thought");
        System.out.println("  • Contextos: Clean, Raw, Dirty");
        System.out.println("\nNota: Este processo pode ser demorado dependendo do hardware local.");
        System.out.println("--------------------------------------------------\n");

        orchestratorService.runBatchGeneration();

        return "Geração em batch iniciada. Acompanhe o progresso nos logs e na barra de estado.";
    }

    @ShellMethod(key = "list", value = "Lista as procedures encontradas na pasta de entrada.")
    public String listSqlFiles() {
        proceduresFolderValidator.validateProceduresFolder();
        return documentService.listSqlFiles();
    }
}