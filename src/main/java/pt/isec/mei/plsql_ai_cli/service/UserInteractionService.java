package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.Approach;
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

    /**
     * Prompt user to select an approach (noise or technique)
     */
    public String promptForApproach() {
        LineReader lineReader = createLineReader();
        System.out.println("\nSelect approach:");
        System.out.println("1. noise");
        System.out.println("2. technique");
        String approachChoice = lineReader.readLine("Enter choice (1-2): ").trim();
        String result = approachChoice.equals("1") ? Approach.NOISE.getValue() : Approach.TECHNIQUE.getValue();
        log.debug("User selected approach: {}", result);
        return result;
    }

    /**
     * Prompt user to select a type (clean, raw, dirty) - only applicable for noise approach
     */
    public String promptForType(String approach) {
        if (Approach.NOISE.getValue().equals(approach)) {
            LineReader lineReader = createLineReader();
            System.out.println("\nSelect type:");
            System.out.println("1. clean");
            System.out.println("2. raw");
            System.out.println("3. dirty");
            String typeChoice = lineReader.readLine("Enter choice (1-3): ").trim();
            String result = switch (typeChoice) {
                case "1" -> NoiseLevel.CLEAN.getType();
                case "2" -> NoiseLevel.RAW.getType();
                case "3" -> NoiseLevel.DIRTY.getType();
                default -> NoiseLevel.CLEAN.getType();
            };
            log.debug("User selected type: {}", result);
            return result;
        } else {
            log.debug("Approach is not noise, defaulting type to: clean");
            return NoiseLevel.CLEAN.getType(); // default for technique
        }
    }

    /**
     * Prompt user to select a prompt type (Single Shot, Few Shot, Chain of Thought) - only applicable for technique approach
     */
    public String promptForPromptType(String approach) {
        if (Approach.TECHNIQUE.getValue().equals(approach)) {
            LineReader lineReader = createLineReader();
            System.out.println("\nSelect a prompt type:");
            System.out.println("1. Single Shot");
            System.out.println("2. Few Shot");
            System.out.println("3. Chain of Thought");
            String typeChoice = lineReader.readLine("Enter choice (1-3): ").trim();
            String result = switch (typeChoice) {
                case "1" -> PromptStrategy.SINGLE_SHOT.getType();
                case "2" -> PromptStrategy.FEW_SHOT.getType();
                case "3" -> PromptStrategy.CHAIN_OF_THOUGHT.getType();
                default -> PromptStrategy.SINGLE_SHOT.getType();
            };
            log.debug("User selected prompt type: {}", result);
            return result;
        } else {
            log.debug("Approach is not technique, defaulting prompt type to: ss");
            return PromptStrategy.SINGLE_SHOT.getType(); // default for noise
        }
    }


    /**
     * Prompt user to select a procedure from available SQL files
     */
    public String promptForProcedure() {
        LineReader lineReader = createLineReader();
        File proceduresDir = new File("procedures");
        File[] files = proceduresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (files == null || files.length == 0) {
            log.error("No SQL procedures found in directory: {}", proceduresDir.getAbsolutePath());
            throw new IllegalStateException("No SQL procedures found in directory: " + proceduresDir.getAbsolutePath());
        }

        List<String> procedureNames = Arrays.stream(files)
                .map(file -> file.getName().replaceAll("\\.sql$", ""))
                .sorted()
                .toList();

        System.out.println("\nAvailable procedures:");
        procedureNames.forEach(name -> System.out.println((procedureNames.indexOf(name) + 1) + ". " + name));

        String procedureChoice = lineReader.readLine("\nEnter procedure number: ").trim();

        try {
            int choice = Integer.parseInt(procedureChoice);
            if (choice > 0 && choice <= procedureNames.size()) {
                String selectedProcedure = procedureNames.get(choice - 1);
                log.debug("User selected procedure: {}", selectedProcedure);
                return selectedProcedure;
            } else {
                log.error("Invalid procedure number selected: {}", choice);
                throw new IllegalArgumentException("Invalid procedure number: " + choice + ". Please select a number between 1 and " + procedureNames.size());
            }
        } catch (NumberFormatException e) {
            log.error("Invalid input for procedure selection: {}", procedureChoice);
            throw new IllegalArgumentException("Invalid input. Please enter a number.", e);
        }
    }

    /**
     * Create a LineReader instance for reading user input
     */
    private LineReader createLineReader() {
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }
}

