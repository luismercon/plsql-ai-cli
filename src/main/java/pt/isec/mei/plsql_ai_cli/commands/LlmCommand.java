package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.service.DocumentService;
import pt.isec.mei.plsql_ai_cli.service.OllamaService;
import pt.isec.mei.plsql_ai_cli.service.UserInteractionService;

@ShellComponent
@AllArgsConstructor
public class LlmCommand {

    private final OllamaService ollamaService;
    private final DocumentService documentService;
    private final UserInteractionService userInteractionService;

    @ShellMethod(key = "analyze", value = "Analyze the SQL code from the stored procedure")
    public String analyzeSqlCode() {
        // Step 1: Ask for approach
        String approach = userInteractionService.promptForApproach();

        // Step 2: Ask for type (only if approach is noise)
        String type = userInteractionService.promptForType(approach);

        // Step 3: Ask for promptType (only if approach is technique)
        String promptType = userInteractionService.promptForPromptType(approach);

        // Step 4: Ask for modelId (using letters A/B)
        String modelId = userInteractionService.promptForModel();

        // Step 5: Get list of procedures and let user choose by number
        String procedureName = userInteractionService.promptForProcedure();
        /*if (procedureName.startsWith("Invalid") || procedureName.startsWith("No SQL")) {
            return procedureName; // Return error message @todo handle more gracefully
        }*/

        return ollamaService.analyze(type, modelId, procedureName, approach, promptType);
    }


    @ShellMethod(key = "list", value = "List all SQL files in the procedures directory")
    public String listSqlFiles() {
        return documentService.listSqlFiles();
    }
}

