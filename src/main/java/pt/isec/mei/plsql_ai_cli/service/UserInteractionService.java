package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.PromptStrategy;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class UserInteractionService {

    private final Terminal terminal;

    public UserInteractionService(@Lazy Terminal terminal) {
        this.terminal = terminal;
    }

    public String promptForNoiseLevel() {
        LineReader lineReader = createLineReader();
        System.out.println("\nSelect Context (Noise Level):");
        System.out.println("1. Clean (No comments)");
        System.out.println("2. Raw (Original SPL comments)");
        System.out.println("3. Dirty (Injected fake comments)");

        String choice = lineReader.readLine("Choice (1-3): ").trim();
        String result = switch (choice) {
            case "2" -> NoiseLevel.RAW.getLevel();
            case "3" -> NoiseLevel.DIRTY.getLevel();
            default -> NoiseLevel.CLEAN.getLevel();
        };

        log.debug("Selected noise level: {}", result);
        return result;
    }

    public String promptForStrategy() {
        LineReader lineReader = createLineReader();
        System.out.println("\nSelect Prompt Strategy:");
        System.out.println("1. Single Shot");
        System.out.println("2. Few Shot");
        System.out.println("3. Chain of Thought");

        String choice = lineReader.readLine("Choice (1-3): ").trim();
        String result = switch (choice) {
            case "2" -> PromptStrategy.FEW_SHOT.getStrategy();
            case "3" -> PromptStrategy.CHAIN_OF_THOUGHT.getStrategy();
            default -> PromptStrategy.SINGLE_SHOT.getStrategy();
        };

        log.debug("Selected strategy: {}", result);
        return result;
    }

    public String promptForProcedure() {
        LineReader lineReader = createLineReader();
        File proceduresDir = new File("procedures");
        File[] files = proceduresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (files == null || files.length == 0) {
            log.error("No SQL procedures found in: {}", proceduresDir.getAbsolutePath());
            throw new IllegalStateException("Procedures folder is empty or missing.");
        }

        List<String> procedureNames = Arrays.stream(files)
                .map(file -> file.getName().replaceAll("\\.sql$", ""))
                .sorted()
                .toList();

        System.out.println("\nAvailable procedures:");
        for (int i = 0; i < procedureNames.size(); i++) {
            System.out.println((i + 1) + ". " + procedureNames.get(i));
        }

        String choiceStr = lineReader.readLine("\nSelect procedure number: ").trim();

        try {
            int choice = Integer.parseInt(choiceStr);
            if (choice > 0 && choice <= procedureNames.size()) {
                String selected = procedureNames.get(choice - 1);
                log.debug("Selected procedure: {}", selected);
                return selected;
            } else {
                throw new IllegalArgumentException("Invalid range (1-" + procedureNames.size() + ").");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid input. Please enter a number.");
        }
    }

    private LineReader createLineReader() {
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }
}