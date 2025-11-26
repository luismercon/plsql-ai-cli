package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.AnalysisType;
import pt.isec.mei.plsql_ai_cli.enums.Approach;
import pt.isec.mei.plsql_ai_cli.enums.Model;
import pt.isec.mei.plsql_ai_cli.model.ProcedureDocumentation;
import pt.isec.mei.plsql_ai_cli.model.TokensData;
import pt.isec.mei.plsql_ai_cli.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentService {

    public String readRawProcedure(String fileName, String approach, String type) {
        //String subfolder = determineSubfolder(approach, type);

        try {
            String filePath = String.format("procedures/%s.sql", fileName);
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new RuntimeException("Procedure file not found: " + filePath);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read procedure SQL file", e);
        }
    }

    private String determineSubfolder(String approach, String type) {

        if (Approach.TECHNIQUE.getValue().equals(StringUtils.normalizeToLower(approach))) {
            return Model.A.name();
        }

        if (Approach.NOISE.getValue().equals(StringUtils.normalizeToLower(approach))) {
            List<String> bTypes = List.of(AnalysisType.DIRTY.getType());
            List<String> aTypes = List.of(AnalysisType.CLEAN.getType(), AnalysisType.RAW.getType());
            if (bTypes.contains(StringUtils.normalizeToLower(type))) {
                return Model.B.name();
            }
            if (aTypes.contains(StringUtils.normalizeToLower(type))) {
                return Model.A.name();
            }
        }

        return Model.A.name();
    }

    public String cleanCommentsService(String fileName, String approach, String type) {
        String rawProcedure = readRawProcedure(fileName, approach, type);

        // Remove block comments enclosed in { }
        String cleaned = rawProcedure.replaceAll("\\{[^}]*}", "");

        // Remove single-line comments starting with --
        // This pattern handles both standalone comments and inline comments
        cleaned = cleaned.replaceAll("--[^\n]*", "");

        // Remove empty lines and trim whitespace
        cleaned = cleaned.replaceAll("(?m)^\\s*$[\n\r]{1,}", "\n");

        // Trim leading and trailing whitespace
        cleaned = cleaned.trim();

        return cleaned;
    }

    public String readDirtyProcedure(String fileName, String approach, String type) {
        // This reads the dirty procedure based on approach and type rules
        return readRawProcedure(fileName, approach, type);
    }

    public String saveDocumentationToMarkdown(ProcedureDocumentation doc,
                                              long processingTimeMs,
                                              TokensData tokensData,
                                              String modelId,
                                              String approach,
                                              String procedureName,
                                              String type,
                                              String promptType) throws IOException {

        String resultsPath = pathRouter(approach, modelId, procedureName);
        Path resultsDir = Paths.get(resultsPath);

        // Create results directory if it doesn't exist
        if (!Files.exists(resultsDir)) {
            Files.createDirectories(resultsDir);
        }

        // Generate filename based on approach
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String fileName = determineFileName(approach, type, promptType, timestamp);
        Path filePath = resultsDir.resolve(fileName);

        // Build markdown content
        StringBuilder markdown = new StringBuilder();

        // Add metadata header
        markdown.append("---\n");
        markdown.append("timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        markdown.append("processing_time_s: ").append(processingTimeMs / 1000.0).append("\n");
        markdown.append("prompt_tokens: ").append(tokensData.promptTokens()).append("\n");
        markdown.append("completion_tokens: ").append(tokensData.completionTokens()).append("\n");
        markdown.append("total_tokens: ").append(tokensData.totalTokens()).append("\n");
        markdown.append("Model_ID: ").append(modelId == null ? "default" : modelId).append("\n");
        markdown.append("---\n\n");

        // Add procedure documentation content
        markdown.append("Procedure: ").append(doc.getProcedureName()).append("\n\n");

        markdown.append("## General Description: ")
                .append("\n")
                .append(doc.getGeneralDescription())
                .append("\n\n");

        markdown.append("## Business Rules (SBVR format):\n\n");
        int ruleNumber = 1;
        for (String rule : doc.getBusinessRules()) {
            markdown.append(ruleNumber++).append(". ").append(rule).append("\n");
        }

        markdown.append("## Logical Flow (procedural narrative):\n\n");
        for (String step : doc.getLogicalFlowSteps()) {
            markdown.append("- ").append(step).append("\n");
        }

        markdown.append("## Identified Dependencies:\n\n");

        markdown.append("### Tables: \n\n");
        if (doc.getTables() != null && !doc.getTables().isEmpty()) {
            // Create markdown table header
            markdown.append("| Table/View | Interaction Type | Business Rule Enforced |\n");
            markdown.append("|------------|------------------|------------------------|\n");

            // Add each table dependency as a table row
            for (ProcedureDocumentation.TableDependency table : doc.getTables()) {
                markdown.append("| ")
                        .append(table.getTableView() != null ? table.getTableView() : "[TABLE_NAME]")
                        .append(" | ")
                        .append(table.getInteractionType() != null ? table.getInteractionType() : "[SELECT/INSERT/UPDATE/DELETE]")
                        .append(" | ")
                        .append(table.getBusinessRuleEnforced() != null ? table.getBusinessRuleEnforced() : "[Description of business rule enforced]")
                        .append(" |\n");
            }
        } else {
            markdown.append("| Table/View | Interaction Type | Business Rule Enforced |\n");
            markdown.append("|------------|------------------|------------------------|\n");
            markdown.append("| None | N/A | N/A |\n");
        }
        markdown.append("\n");

        markdown.append("### Calls: \n");
        if (doc.getCalls() != null && !doc.getCalls().isEmpty()) {
            for (String call : doc.getCalls()) {
                markdown.append("- ").append(call).append("\n");
            }
        } else {
            markdown.append("- None\n");
        }
        markdown.append("\n");

        // Write to file
        Files.writeString(filePath, markdown.toString());

        log.info("Documentation saved to: {}", filePath.toAbsolutePath());
        return fileName;
    }


    public String listSqlFiles() {

        log.info("Listing available SPL/SQL files ...");

        File proceduresDir = new File("procedures");

        if (!proceduresDir.exists() || !proceduresDir.isDirectory()) {
            return "Procedures directory not found";
        }

        File[] files = proceduresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (files == null || files.length == 0) {
            return "No SQL files found";
        }

        List<String> fileNames = Arrays.stream(files)
                .map(file -> "- " + file.getName().replaceAll("\\.sql$", ""))
                .sorted()
                .collect(Collectors.toList());

        return "Available PL/SQL files:\n" + String.join("\n", fileNames);
    }

    private String pathRouter(String approach, String modelId, String procedureName) {
        String normalizedApproach = StringUtils.normalizeToLower(approach);
        String normalizedModelId = StringUtils.normalizeToUpper(modelId);
        String normalizedProcedureName = StringUtils.normalizeToLower(procedureName);

        // Rule: if approach is "noise" and modelId is "A", save in results/noise/A/{procedureName}
        if (Approach.NOISE.getValue().equals(normalizedApproach) && Model.A.name().equals(normalizedModelId)) {
            return String.format("results/noise/A/%s", normalizedProcedureName);
        }

        // Rule: if approach is "noise" and modelId is "B", save in results/noise/B/{procedureName}
        if (Approach.NOISE.getValue().equals(normalizedApproach) && Model.B.name().equals(normalizedModelId)) {
            return String.format("results/noise/B/%s", normalizedProcedureName);
        }

        // Rule: if approach is "technique" and modelId is "A", save in results/technique/A/{procedureName}
        if (Approach.TECHNIQUE.getValue().equals(normalizedApproach) && Model.A.name().equals(normalizedModelId)) {
            return String.format("results/technique/A/%s", normalizedProcedureName);
        }

        // Rule: if approach is "technique" and modelId is "B", save in results/technique/B/{procedureName}
        if (Approach.TECHNIQUE.getValue().equals(normalizedApproach) && Model.B.name().equals(normalizedModelId)) {
            return String.format("results/technique/B/%s", normalizedProcedureName);
        }

        // Default fallback
        return "results";
    }

    private String determineFileName(String approach, String type, String promptType, String timestamp) {
        String normalizedApproach = StringUtils.normalizeToLower(approach);

        // Rule: if approach is "noise", use type in filename
        if (Approach.NOISE.getValue().equals(normalizedApproach)) {
            return type + "_" + timestamp + ".md";
        }

        // Rule: if approach is "technique", use promptType in filename
        if (Approach.TECHNIQUE.getValue().equals(normalizedApproach)) {
            return promptType + "_" + timestamp + ".md";
        }

        // Default fallback
        return type + "_" + timestamp + ".md";
    }
}

