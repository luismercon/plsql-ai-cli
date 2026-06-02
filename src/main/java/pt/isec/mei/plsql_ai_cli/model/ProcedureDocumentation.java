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
    private String generalDescription;
    private List<String> businessRules;
    private List<String> logicalFlowSteps;
    private List<TableDependency> tables;
    private List<String> calls;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableDependency {
        private String tableView;
        private String interactionType;
        private String businessRuleEnforced;
    }
}