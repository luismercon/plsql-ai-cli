package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.PromptStrategy;
import pt.isec.mei.plsql_ai_cli.model.ProcedureDocumentation;
import pt.isec.mei.plsql_ai_cli.model.TokensData;
import pt.isec.mei.plsql_ai_cli.utils.FakeComments;
import pt.isec.mei.plsql_ai_cli.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentService {

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    public String readRawProcedure(String fileName) {
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

    public String cleanCommentsService(String fileName) {
        String rawProcedure = readRawProcedure(fileName);

        // Remove block comments enclosed in { }
        String cleaned = rawProcedure.replaceAll("\\{[^}]*}", "");

        // Remove single-line comments starting with --
        cleaned = cleaned.replaceAll("--[^\n]*", "");

        // Remove empty lines and trim whitespace
        cleaned = cleaned.replaceAll("(?m)^\\s*$[\n\r]{1,}", "\n");

        // Trim leading and trailing whitespace
        cleaned = cleaned.trim();

        return cleaned;
    }

    public String readDirtyProcedure(String fileName) {
        return readDirtyProcedure(fileName, 0.3);
    }

    public String readDirtyProcedure(String fileName, double replacementRatio) {
        String rawProcedure = readRawProcedure(fileName);
        return makeDirtyVersion(rawProcedure, replacementRatio);
    }

    private String makeDirtyVersion(String sqlContent, double replacementRatio) {
        if (replacementRatio <= 0 || replacementRatio > 1.0) {
            replacementRatio = 0.3; // Default to 30%
        }

        StringBuilder result = new StringBuilder();
        String[] lines = sqlContent.split("\n", -1);
        Random random = new Random();
        int fakeCommentIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("--")) {
                String comment = trimmedLine.substring(2).trim();
                if (comment.length() > 5) {
                    if (random.nextDouble() < replacementRatio) {
                        String leadingWhitespace = line.substring(0, line.indexOf("--"));
                        String fakeComment = FakeComments.getCommentAt(
                                fakeCommentIndex % FakeComments.getPoolSize()
                        );
                        if (!fakeComment.startsWith("--")) {
                            fakeComment = "-- " + fakeComment;
                        }
                        result.append(leadingWhitespace).append(fakeComment).append("\n");
                        fakeCommentIndex++;
                        continue;
                    }
                }
            }

            if (trimmedLine.startsWith("{")) {
                if (!trimmedLine.endsWith("}")) {
                    result.append(line).append("\n");
                    i++;
                    while (i < lines.length) {
                        String blockLine = lines[i];
                        String trimmedBlockLine = blockLine.trim();
                        if (trimmedBlockLine.contains("}")) {
                            result.append(blockLine).append("\n");
                            break;
                        }
                        if (trimmedBlockLine.length() > 5 && !trimmedBlockLine.matches("^[-=]+$")) {
                            if (random.nextDouble() < replacementRatio) {
                                String leadingWhitespace = blockLine.substring(
                                        0,
                                        Math.min(blockLine.length(), blockLine.length() - blockLine.trim().length())
                                );
                                String fakeComment = FakeComments.getCommentAt(
                                        fakeCommentIndex % FakeComments.getPoolSize()
                                );
                                if (fakeComment.startsWith("--")) {
                                    fakeComment = fakeComment.substring(2).trim();
                                }
                                result.append(leadingWhitespace).append(fakeComment).append("\n");
                                fakeCommentIndex++;
                                i++;
                                continue;
                            }
                        }
                        result.append(blockLine).append("\n");
                        i++;
                    }
                    continue;
                }
            }
            result.append(line).append("\n");
        }
        return result.toString();
    }

    public void saveBatchResult(ProcedureDocumentation doc, File file, PromptStrategy strategy, NoiseLevel noise, long durationMs, TokensData tokens) {
        String procedureName = file.getName().replace(".sql", "");
        try {
            saveDocumentationToMarkdown(
                    doc,
                    durationMs,
                    tokens,
                    this.modelName,
                    procedureName,
                    noise.getType(),
                    strategy.getType()
            );
        } catch (IOException e) {
            log.error("Failed to save batch result for {}", procedureName, e);
        }
    }

    public String saveDocumentationToMarkdown(ProcedureDocumentation doc,
                                              long processingTimeMs,
                                              TokensData tokensData,
                                              String modelName,
                                              String procedureName,
                                              String type,
                                              String promptType) throws IOException {

        String resultsPath = pathRouter(procedureName);
        Path resultsDir = Paths.get(resultsPath);

        if (!Files.exists(resultsDir)) {
            Files.createDirectories(resultsDir);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String fileName = determineFileName(type, promptType, timestamp);
        Path filePath = resultsDir.resolve(fileName);

        StringBuilder markdown = new StringBuilder();

        markdown.append("---\n");
        markdown.append("timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        markdown.append("processing_time_s: ").append(processingTimeMs / 1000.0).append("\n");
        markdown.append("prompt_tokens: ").append(tokensData.promptTokens()).append("\n");
        markdown.append("completion_tokens: ").append(tokensData.completionTokens()).append("\n");
        markdown.append("total_tokens: ").append(tokensData.totalTokens()).append("\n");
        markdown.append("model: ").append(modelName).append("\n");
        markdown.append("---\n\n");

        markdown.append("Procedure: ").append(doc.getProcedureName()).append("\n\n");

        markdown.append("## General Description: ")
                .append("\n")
                .append(doc.getGeneralDescription())
                .append("\n\n");

        markdown.append("## Business Rules (SBVR format):\n\n");
        int ruleNumber = 1;
        if (doc.getBusinessRules() != null) {
            for (String rule : doc.getBusinessRules()) {
                markdown.append(ruleNumber++).append(". ").append(rule).append("\n");
            }
        }

        markdown.append("\n## Logical Flow (procedural narrative):\n\n");
        if (doc.getLogicalFlowSteps() != null) {
            for (String step : doc.getLogicalFlowSteps()) {
                markdown.append("- ").append(step).append("\n");
            }
        }

        markdown.append("\n## Identified Dependencies:\n\n");
        markdown.append("### Tables: \n\n");

        markdown.append("| Table/View | Interaction Type | Business Rule Enforced |\n");
        markdown.append("|------------|------------------|------------------------|\n");

        if (doc.getTables() != null && !doc.getTables().isEmpty()) {
            for (ProcedureDocumentation.TableDependency table : doc.getTables()) {
                markdown.append("| ")
                        .append(table.getTableView() != null ? table.getTableView() : "N/A")
                        .append(" | ")
                        .append(table.getInteractionType() != null ? table.getInteractionType() : "N/A")
                        .append(" | ")
                        .append(table.getBusinessRuleEnforced() != null ? table.getBusinessRuleEnforced() : "N/A")
                        .append(" |\n");
            }
        } else {
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

        Files.writeString(filePath, markdown.toString());

        log.info("Documentation saved to: {}", filePath.toAbsolutePath());
        return fileName;
    }

    public List<File> getAllSqlFiles() {
        File proceduresDir = new File("procedures");
        if (!proceduresDir.exists() || !proceduresDir.isDirectory()) {
            return new ArrayList<>();
        }
        File[] files = proceduresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(files);
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

    private String pathRouter(String procedureName) {
        String normalizedProcedureName = StringUtils.normalizeToLower(procedureName);
        return String.format("results/%s", normalizedProcedureName);
    }

    private String determineFileName(String type, String promptType, String timestamp) {
        String normalizedType = StringUtils.normalizeToLower(type);
        String normalizedPromptType = StringUtils.normalizeToLower(promptType);
        return normalizedPromptType + "_" + normalizedType + "_" + timestamp + ".md";
    }
}