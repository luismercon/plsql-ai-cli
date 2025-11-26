package pt.isec.mei.plsql_ai_cli.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pt.isec.mei.plsql_ai_cli.exception.ValidationException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class ProceduresFolderValidator {

    private static final String PROCEDURES_FOLDER = "procedures";

    /**
     * Validates that the procedures folder exists and contains at least one .sql file
     * @throws ValidationException if validation fails
     */
    public void validateProceduresFolder() {
        log.debug("Validating procedures folder...");

        // Check if procedures folder exists in the root
        Path proceduresPath = Paths.get(PROCEDURES_FOLDER);
        File proceduresFolder = proceduresPath.toFile();

        if (!proceduresFolder.exists()) {
            String errorMsg = String.format("Procedures folder not found at: %s.%n%nTo run the analysis, please:%n" +
                    "  1. Create a 'procedures' folder in the root directory%n" +
                    "  2. Add one or more .sql files containing PL/SQL code to this folder",
                    proceduresPath.toAbsolutePath());
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }

        if (!proceduresFolder.isDirectory()) {
            String errorMsg = String.format("'%s' exists but is not a directory", PROCEDURES_FOLDER);
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }

        // Check if there is at least one .sql file in the procedures folder
        File[] sqlFiles = proceduresFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (sqlFiles == null || sqlFiles.length == 0) {
            String errorMsg = String.format("No .sql files found in the procedures folder: %s%n%n" +
                    "Please add one or more .sql files containing PL/SQL code to this folder.",
                    proceduresPath.toAbsolutePath());
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }

        log.debug("Validation successful: Found {} SQL file(s) in procedures folder", sqlFiles.length);
    }
}
