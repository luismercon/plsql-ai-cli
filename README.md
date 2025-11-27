# PL/SQL AI CLI

## Overview
This project is a command-line interface (CLI) tool tailored for Informix PL/SQL stored procedures. It enables analysis, documentation, and modernization using local AI models via Ollama. The app supports code cleaning, business rule extraction, and procedural flow documentation, with results saved in Markdown format.

The application includes a **"dirty" option** that introduces fake/noisy comments into procedures for testing how well AI models handle inaccurate documentation. For more details, see [DIRTY_PROCEDURE_IMPLEMENTATION.md](DIRTY_PROCEDURE_IMPLEMENTATION.md).

## Prerequisites
- **Ollama installed** on your machine
- **Ollama service running** at `http://localhost:11434`
- **Default Ollama models installed**:
  - `qwen3-coder:30b` (Model A)
  - `deepseek-coder-v2:16b` (Model B)
  
  Install them with:
  ```cmd
  ollama pull qwen3-coder:30b
  ollama pull deepseek-coder-v2:16b
  ```
- **Java 17+** and **Maven** installed
- **Procedures folder** with at least one `.sql` file in the root directory

## Setup

### Procedures Folder
Before running the `analyze` or `list` commands, you **must** create a `procedures` folder in the project root directory and add your PL/SQL files:

1. Create a `procedures` folder in the root directory (same level as `pom.xml`)
2. Add one or more `.sql` files containing your PL/SQL stored procedures to this folder

**Example structure:**
```
plsql-ai-cli/
├── procedures/
│   ├── procedure1.sql
│   ├── procedure2.sql
│   └── procedure3.sql
├── pom.xml
├── README.md
└── ...
```

The application will automatically validate that:
- The `procedures` folder exists in the root directory
- At least one `.sql` file is present in the folder

If validation fails, you'll receive a clear error message with instructions on how to fix it.

## Configuration

The application is configured in two places:

### Ollama Base URL
Configure the Ollama connection in `src/main/resources/application.properties`:
```properties
spring.ai.ollama.base-url=http://localhost:11434
```

### Models Configuration
The application uses two models simultaneously, configured in `src/main/java/pt/isec/mei/plsql_ai_cli/config/OllamaConfig.java`:

**Model A (qwen3-coder:30b):**
- Temperature: 0.7
- Top-P: 0.9

**Model B (deepseek-coder-v2:16b):**
- Temperature: 0.7
- Top-P: 0.9

You can modify the models, temperature, and top-p values in `OllamaConfig.java` according to your needs.

## Starting Ollama
Before using the CLI, you **must ensure**:

1. **Ollama is running:**
   ```cmd
   ollama serve
   ```

2. **Required models are installed** (verify with `ollama list`):
   ```cmd
   ollama list
   ```
   
   If models are missing, install them:
   ```cmd
   ollama pull qwen3-coder:30b
   ollama pull deepseek-coder-v2:16b
   ```

## Usage

### Start the CLI
```cmd
mvnw.cmd spring-boot:run
```
Or, if built:
```cmd
java -jar target/plsql-ai-cli.jar
```

### Main Commands
- **list**: List available PL/SQL procedures (validates procedures folder first)
- **analyze**: Analyze a procedure (validates procedures folder, then provides guided prompts for approach, type, model, and file)

Both commands automatically validate that the `procedures` folder exists and contains at least one `.sql` file before execution.

Results are saved in the `results/` directory as Markdown files.


## Using Different Models
To change the Ollama models, edit the `OllamaConfig.java` file and modify the model names in the `@Bean` methods for `MODEL_A` and `MODEL_B`. Download models from [Ollama Model Search](https://ollama.com/search):
```cmd
ollama pull <model-name>
```
For example:
```cmd
ollama pull deepseek-coder-v2:16b
```

## Temperature Settings
- **Low (0.1-0.3):** Deterministic
- **Medium (0.5-0.7):** Balanced
- **High (0.8-1.0):** Creative

## Top-P Settings
Top-P (nucleus sampling) controls the diversity of the model's output by limiting the cumulative probability of tokens considered:
- **Low (0.1-0.3):** Very focused, only most likely tokens
- **Medium (0.5-0.7):** Balanced selection
- **High (0.8-1.0):** More diverse, considers a wider range of tokens

## Troubleshooting

### Ollama Not Responding
- Check Ollama: `ollama list`
- Verify URL: `http://localhost:11434`
- Test: `curl http://localhost:11434/api/tags`

### Model Not Found
Download the required model from [Ollama Model Search](https://ollama.com/search):
```cmd
ollama pull <model-name>
ollama list
```

### Port Issues (Windows)
Find and kill the process using the port:
```cmd
netstat -ano | findstr :11434
```
Note the PID, then:
```cmd
taskkill /PID <PID> /F
```
Change port in `application.properties` or use `OLLAMA_PORT` env variable.

## Service Architecture
- **DocumentService**: Reads, cleans, and lists procedures; saves results; supports "dirty" procedure generation with fake comments
- **OllamaService**: Handles prompt construction and LLM interaction
- **UserInteractionService**: Manages CLI prompts and user input
- **OllamaConfig**: Configures two Ollama models (Model A and Model B) with their respective temperature and top-p settings
- **ProceduresFolderValidator**: Validates that the procedures folder exists and contains SQL files before command execution

## Security
- Ollama runs locally; no external API calls
- Your code stays on your machine

---
For details, see the source code and comments in each service class.
