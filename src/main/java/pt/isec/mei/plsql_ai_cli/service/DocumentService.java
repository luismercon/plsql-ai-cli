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
                throw new RuntimeException("SQL file not found: " + filePath);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error reading Informix SQL file", e);
        }
    }

    public String cleanCommentsService(String fileName) {
        String rawProcedure = readRawProcedure(fileName);

        // Remove comentários Informix { } e --
        String cleaned = rawProcedure.replaceAll("\\{[^}]*}", "");
        cleaned = cleaned.replaceAll("--[^\n]*", "");

        // Limpa linhas vazias e espaços em excesso
        cleaned = cleaned.replaceAll("(?m)^\\s*$[\n\r]{1,}", "\n");
        return cleaned.trim();
    }

    public String readDirtyProcedure(String fileName) {
        return makeDirtyVersion(readRawProcedure(fileName), 0.3);
    }

    private String makeDirtyVersion(String sqlContent, double replacementRatio) {
        StringBuilder result = new StringBuilder();
        String[] lines = sqlContent.split("\n", -1);
        Random random = new Random();
        int fakeCommentIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            // 1. Injeção de ruído em comentários de linha única (--)
            if (trimmedLine.startsWith("--") && trimmedLine.length() > 5) {
                if (random.nextDouble() < replacementRatio) {
                    String leadingWhitespace = line.substring(0, line.indexOf("--"));
                    String fakeComment = FakeComments.getCommentAt(fakeCommentIndex++ % FakeComments.getPoolSize());

                    // Verifica se o comentário da pool já começa com "--" para evitar "-- --"
                    String prefix = fakeComment.startsWith("--") ? "" : "-- ";

                    result.append(leadingWhitespace)
                            .append(prefix)
                            .append(fakeComment)
                            .append("\n");
                    continue;
                }
            }

            // 2. Tratamento de blocos de comentários Informix { }
            if (trimmedLine.startsWith("{") && !trimmedLine.endsWith("}")) {
                result.append(line).append("\n");
                i++;
                while (i < lines.length) {
                    String blockLine = lines[i];
                    String trimmedBlock = blockLine.trim();

                    if (trimmedBlock.contains("}")) {
                        result.append(blockLine).append("\n");
                        break;
                    }

                    // Injeta ruído dentro do bloco se a linha tiver conteúdo relevante
                    if (trimmedBlock.length() > 5 && random.nextDouble() < replacementRatio) {
                        String fake = FakeComments.getCommentAt(fakeCommentIndex++ % FakeComments.getPoolSize());

                        // Dentro de {}, removemos o prefixo "--" se ele existir para manter a estética do bloco
                        String cleanFake = fake.startsWith("--") ? fake.substring(2).trim() : fake;

                        // Mantém a indentação original da linha
                        String leadingSpace = blockLine.substring(0, blockLine.indexOf(trimmedBlock));
                        result.append(leadingSpace).append(cleanFake).append("\n");
                    } else {
                        result.append(blockLine).append("\n");
                    }
                    i++;
                }
                continue;
            }

            // 3. Mantém linhas de código sem alteração
            result.append(line).append("\n");
        }
        return result.toString();
    }

    public void saveBatchResult(ProcedureDocumentation doc, File file, PromptStrategy strategy, NoiseLevel noise, long durationMs, TokensData tokens) {
        String procedureName = file.getName().replace(".sql", "");
        try {
            saveDocumentationToMarkdown(doc, durationMs, tokens, this.modelName, procedureName, noise.getLevel(), strategy.getStrategy());
        } catch (IOException e) {
            log.error("Error saving batch result for {}", procedureName, e);
        }
    }

    public String saveDocumentationToMarkdown(ProcedureDocumentation doc, long processingTimeMs, TokensData tokensData, String modelName, String procedureName, String type, String promptType) throws IOException {
        Path resultsDir = Paths.get(pathRouter(procedureName));
        if (!Files.exists(resultsDir)) Files.createDirectories(resultsDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = determineFileName(type, promptType, procedureName, timestamp);
        Path filePath = resultsDir.resolve(fileName);

        StringBuilder md = new StringBuilder();
        md.append("---\n")
                .append("timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n")
                .append("processing_time_s: ").append(processingTimeMs / 1000.0).append("\n")
                .append("prompt_tokens: ").append(tokensData.promptTokens()).append("\n")
                .append("completion_tokens: ").append(tokensData.completionTokens()).append("\n")
                .append("total_tokens: ").append(tokensData.totalTokens()).append("\n")
                .append("model: ").append(modelName).append("\n")
                .append("---\n\n")
                .append("# Procedure: ").append(doc.getProcedureName()).append("\n\n")
                .append("## General Description\n").append(doc.getGeneralDescription()).append("\n\n")
                .append("## Business Rules (SBVR)\n");

        int ruleIdx = 1;
        if (doc.getBusinessRules() != null) {
            for (String rule : doc.getBusinessRules()) md.append(ruleIdx++).append(". ").append(rule).append("\n");
        }

        md.append("\n## Logical Flow\n");
        if (doc.getLogicalFlowSteps() != null) {
            for (String step : doc.getLogicalFlowSteps()) md.append("- ").append(step).append("\n");
        }

        md.append("\n## Dependencies\n### Tables\n| Table/View | Interaction | Business Rule |\n|---|---|---|\n");
        if (doc.getTables() != null && !doc.getTables().isEmpty()) {
            for (var t : doc.getTables()) {
                md.append("| ").append(t.getTableView()).append(" | ").append(t.getInteractionType()).append(" | ").append(t.getBusinessRuleEnforced()).append(" |\n");
            }
        } else {
            md.append("| None | N/A | N/A |\n");
        }

        Files.writeString(filePath, md.toString());
        return fileName;
    }

    public List<File> getAllSqlFiles() {
        File dir = new File("procedures");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".sql"));
        return (files == null) ? new ArrayList<>() : Arrays.asList(files);
    }

    public String listSqlFiles() {
        List<File> files = getAllSqlFiles();
        if (files.isEmpty()) return "No procedures found.";
        return "Available procedures:\n" + files.stream()
                .map(f -> "- " + f.getName().replace(".sql", ""))
                .sorted().collect(Collectors.joining("\n"));
    }

    private String pathRouter(String procedureName) {
        return "results/" + StringUtils.normalizeToLower(procedureName);
    }

    private String determineFileName(String type, String promptType, String procedureName, String timestamp) {
        return StringUtils.normalizeToLower(promptType) + "_" +
                StringUtils.normalizeToLower(type) + "_" +
                StringUtils.normalizeToLower(procedureName) + "_" +
                timestamp + ".md";
    }
}