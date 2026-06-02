package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NoiseLevel {
    CLEAN("clean"), // Código sem comentários
    RAW("raw"),     // Código original com comentários SPL
    DIRTY("dirty"); // Código com comentários ruidosos (FakeComments)

    private final String level;
}
