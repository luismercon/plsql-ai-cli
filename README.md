# PL/SQL AI CLI

## Overview
This project is a command-line interface (CLI) tool tailored for Informix PL/SQL stored procedures. It enables analysis, documentation, and modernization using local AI models via Ollama. The app supports code cleaning, business rule extraction, and procedural flow documentation, with results saved in Markdown format.

## Prerequisites
- **Ollama installed** on your machine
- **Required Ollama models downloaded** (see [Ollama Model Search](https://ollama.com/search) for available models)
- **Ollama service running** at `http://localhost:11434`
- **Java 17+** and **Maven** installed

## Configuration
Configure Ollama in `src/main/resources/application.properties`:
```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=qwen3-coder:30b
spring.ai.ollama.chat.options.temperature=0.7
spring.ai.ollama.chat.options.top-p=0.9
```
The values in `application.properties` are the default model and settings. The application supports using two models at the same time, which can be set in `OllamaConfig.java`.

## Starting Ollama
Before using the CLI, ensure Ollama is running:
```cmd
ollama list
ollama serve
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
- **list**: List available PL/SQL procedures
- **analyze**: Analyze a procedure (guided prompts for approach, type, model, and file)

Results are saved in the `results/` directory as Markdown files.

## Directory Structure
- `src/main/resources/procedures/A` — Place `.sql` files here
- `results/` — Output analysis and documentation

## Using Different Models
To change the Ollama model, update `application.properties` or set the models in `OllamaConfig.java` for runtime selection. Download models from [Ollama Model Search](https://ollama.com/search):
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
- **DocumentService**: Reads, cleans, and lists procedures; saves results
- **OllamaService**: Handles prompt construction and LLM interaction
- **UserInteractionService**: Manages CLI prompts and user input
- **OllamaConfig**: Allows runtime selection of two Ollama models; defaults are set in `application.properties`

## Security
- Ollama runs locally; no external API calls
- Your code stays on your machine

---
For details, see the source code and comments in each service class.
