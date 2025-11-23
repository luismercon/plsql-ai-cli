package pt.isec.mei.plsql_ai_cli.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureDocumentation {

    private String procedureName;

    /**
     * Represents the "General Description" section.
     * Example: "This procedure executes a business operation on system data,
     * performing calculations, validations, or updates according to predefined rules."
     */
    private String generalDescription;

    /**
     * Represents the "Business Rules (SBVR format)" section.
     * Each entry is one rule.
     */
    private List<String> businessRules;

    /**
     * Represents the "Logical Flow (procedural narrative)" section.
     * Each entry is one step.
     */
    private List<String> logicalFlowSteps;

    /**
     * Represents the list of table dependencies in "Identified Dependencies".
     * Each entry contains table name, interaction type, and business rule enforced.
     */
    private List<TableDependency> tables;

    /**
     * Represents the list of procedure/function calls in "Identified Dependencies".
     */
    private List<String> calls;

    /**
     * Nested class to represent a table dependency with detailed information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableDependency {
        /**
         * Table or View name
         */
        private String tableView;

        /**
         * Type of interaction: SELECT, INSERT, UPDATE, DELETE
         */
        private String interactionType;

        /**
         * Description of the business rule enforced by this table interaction
         */
        private String businessRuleEnforced;
    }
}
