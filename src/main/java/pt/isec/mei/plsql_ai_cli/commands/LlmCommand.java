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

    @ShellMethod(key = "analyze", value = "Analyze the SQL code from the stored procedure")
    public String analyzeSqlCode() {
        // Validate that procedures folder exists and contains SQL files
        proceduresFolderValidator.validateProceduresFolder();

        // Step 1: Ask for approach
        String approach = userInteractionService.promptForApproach();

        // Step 2: Ask for type (only if approach is noise)
        String type = userInteractionService.promptForType(approach);

        // Step 3: Ask for promptType (only if approach is technique)
        String promptType = userInteractionService.promptForPromptType(approach);

        // Step 4: Get list of procedures and let user choose by number
        String procedureName = userInteractionService.promptForProcedure();

        return ollamaService.analyze(type, procedureName, approach, promptType);
    }

    @ShellMethod(key = "generate-batch", value = "Generate analysis for all procedures with all PromptStrategy and NoiseLevel combinations")
    public String generateBatch() {
        // Validate that procedures folder exists and contains SQL files
        proceduresFolderValidator.validateProceduresFolder();

        System.out.println("\n=== Batch Generation Mode ===");
        System.out.println("This will analyze ALL procedures in the procedures/ folder");
        System.out.println("Each procedure will be analyzed with:");
        System.out.println("  - 3 Prompt Strategies: Single Shot, Few Shot, Chain of Thought");
        System.out.println("  - 3 Noise Levels: Clean, Raw, Dirty");
        System.out.println("  - Total: 9 analyses per procedure");
        System.out.println("\nThis may take a considerable amount of time...\n");

        orchestratorService.runBatchGeneration();

        return "Batch generation initiated. Check logs and progress bar.";
    }

    @ShellMethod(key = "list", value = "List all SQL files in the procedures directory")
    public String listSqlFiles() {
        // Validate that procedures folder exists and contains SQL files
        proceduresFolderValidator.validateProceduresFolder();
        return documentService.listSqlFiles();
    }
}
