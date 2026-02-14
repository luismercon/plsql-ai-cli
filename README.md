# PL/SQL AI CLI

## Visão Geral

Esta ferramenta de linha de comando (CLI) foi projetada especificamente para procedimentos armazenados (Stored
Procedures) **Informix PL/SQL**. Ela permite análise, documentação e modernização utilizando modelos de IA locais via *
*Ollama**. A aplicação suporta limpeza de código, extração de regras de negócio e documentação de fluxo procedimental,
com resultados salvos em formato Markdown.

O diferencial desta ferramenta é o seu **Pipeline de Estabilidade Semântica**, que utiliza técnicas de *clustering* para
verificar o consenso entre diferentes estratégias de *prompting* e níveis de ruído no código fonte.

## Pré-requisitos

* **Ollama** instalado no sistema.
* **Serviço Ollama** em execução em `http://localhost:11434`.
* **Modelos Recomendados** (essenciais para análise de código e semântica):

```bash
ollama pull codestral:22b        # Especializado em código
ollama pull nomic-embed-text    # Especializado em vetorização (embeddings)

```

* **Java 17+** e **Maven** instalados.
* Uma pasta chamada `procedures` com pelo menos um arquivo `.sql` no diretório raiz.

## Instalação e Configuração

### 1. Preparação da Pasta de Procedimentos

Antes de executar os comandos, você **deve** criar uma pasta `procedures` na raiz do projeto e adicionar seus arquivos
SQL:

1. Crie a pasta `procedures` no mesmo nível do arquivo `pom.xml`.
2. Adicione os arquivos `.sql` que deseja analisar.

### 2. Configuração de Modelos e Parâmetros

A configuração é feita no arquivo `src/main/resources/application.properties`:

```properties
# URL Base do Ollama
spring.ai.ollama.base-url=http://localhost:11434
# Configuração de Modelos
spring.ai.ollama.chat.options.model=codestral:22b
spring.ai.ollama.embedding.options.model=nomic-embed-text
# Parâmetros de Geração
spring.ai.ollama.chat.options.temperature=0.4
spring.ai.ollama.chat.options.top-p=0.9

```

## Como Usar os Comandos

Inicie a aplicação com:

```cmd
mvnw.cmd spring-boot:run

```

### Comandos Principais

* **`list`**: Lista todos os procedimentos disponíveis na pasta `procedures`. Valida automaticamente se a pasta e os
  arquivos existem.
* **`analyze`**: Realiza uma análise individual e guiada. Você escolherá o arquivo, a estratégia de *prompt* e o nível
  de ruído para gerar uma única documentação.
* **`cluster`**: Executa o pipeline completo de análise de estabilidade.
* Gera 9 variantes de documentação (Combinação de: *Single-Shot*, *Few-Shot*, *Chain-of-Thought* vs *Raw*, *Clean*,
  *Dirty*).
* Realiza a vetorização (sem o *frontmatter* para evitar ruídos).
* Executa a deduplicação e o agrupamento (*clustering*).
* Elege o **Medoide** (o arquivo real mais representativo) para o relatório final.

---

## Metodologia de Análise (Pipeline Semântico)

A aplicação segue um rigoroso processo científico para garantir que a documentação gerada seja confiável:

1. **Geração Multi-Estratégia**: O sistema gera documentações variando o Prompt e o estado do código. O estado **"Clean"
   ** remove todos os comentários originais, enquanto o **"Dirty"** introduz ruídos para testar a resiliência da IA.
2. **Vetorização (Embeddings)**: Utiliza o modelo `nomic-embed-text` para transformar texto em coordenadas matemáticas.
3. **Deduplicação e Clustering**: Documentos com similaridade > 0.96 são fundidos. O sistema então agrupa as
   interpretações. Se houver divergência lógica, múltiplos clusters são criados.
4. **Eleição de Medoide**: Em vez de uma média aritmética (centroide), o sistema escolhe o documento **real** que está
   no centro do cluster como representante.
5. **Relatório**: O sistema gera um relatório final que pode serve como base para a escolha das versões de documentação
   a ser submetida a um perito humano, acompanhado de um questionário de escala Likert (1-5) para validar a utilidade
   das regras de negócio extraídas.

---

## Configurações Detalhadas de Geração

### Temperatura (Temperature)

* **Baixa (0.1-0.3):** Respostas determinísticas e focadas. Ideal para extração de regras de negócio.
* **Média (0.4-0.6):** Equilíbrio entre precisão e fluidez textual.
* **Alta (0.7-1.0):** Criativa, mas com maior risco de alucinações em lógica de código.

### Top-P (Nucleus Sampling)

Controla a diversidade da saída:

* **Baixo (0.1-0.3):** Considera apenas os tokens mais prováveis.
* **Alto (0.8-1.0):** Considera uma gama maior de palavras, resultando em textos menos repetitivos.

## Arquitetura de Serviços

* **DocumentService**: Gerencia leitura e a lógica de "limpeza" ou "sujeira" (remoção/adição de comentários).
* **EmbeddingService**: Lida com a geração e cache de vetores semânticos.
* **ClusteringService**: Implementa o algoritmo de agrupamento e eleição de medoides.
* **OllamaService**: Responsável pela comunicação direta com a API local do Ollama.
* **ReportService**: Consolida os dados estatísticos e qualitativos em relatórios Markdown em `results/reports/`.

## Segurança

* **100% Local**: O Ollama roda localmente; nenhum dado de código ou regra de negócio sai da sua infraestrutura.
* **Sem APIs Externas**: Não há dependência de OpenAI, Anthropic ou outros serviços de nuvem.
