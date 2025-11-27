# Dirty Procedure Implementation

## Overview

This implementation creates "dirty" versions of SQL procedure files by replacing some of the existing comments with fake
comments. This is useful for testing documentation generation with noisy/inaccurate comments.

## Components

### 1. FakeComments Class (`utils/FakeComments.java`)

A utility class containing a pool of 50 fake SQL comments that mimic the patterns found in real PL/SQL procedures:

- **Single-line revision comments**: Following the pattern `-- NN Author YYYY/MM/DD Description`
- **Inline explanatory comments**: Brief comments explaining code logic
- **Block comment content**: Lines that appear inside `{...}` block comments
- **Mixed formats**: Various comment styles matching the real codebase

**Key Features:**

- Static array of 50 pre-defined fake comments
- `getRandomComment()`: Returns a random fake comment
- `getCommentAt(index)`: Returns a specific fake comment by index
- `getPoolSize()`: Returns the total number of available fake comments

### 2. DocumentService Enhancements

#### `readDirtyProcedure(String fileName, String approach, String type)`

Default method that creates a dirty version with 30% comment replacement.

#### `readDirtyProcedure(String fileName, String approach, String type, double replacementRatio)`

Extended method that allows custom replacement ratio (0.0 to 1.0).

**Parameters:**

- `fileName`: Name of the SQL procedure file (without extension)
- `approach`: Analysis approach (e.g., "noise", "technique")
- `type`: Analysis type (e.g., "dirty", "clean", "raw")
- `replacementRatio`: Percentage of comments to replace (default: 0.3 = 30%)

#### `makeDirtyVersion(String sqlContent, double replacementRatio)`

Private method that performs the actual comment replacement logic.

**Algorithm:**

1. Splits SQL content into lines
2. For each line:
    - If it's a single-line comment (`--`) with more than 5 characters:
        - Randomly decides whether to replace based on ratio
        - Replaces with a fake comment while preserving indentation
    - If it's inside a block comment (`{...}`):
        - Processes each line in the block
        - Replaces substantial lines (>5 chars) based on ratio
        - Maintains structure and formatting
3. Returns the modified SQL content

**Key Features:**

- Preserves indentation and code structure
- Only replaces comments with more than 5 characters
- Maintains SQL syntax validity
- Uses random selection for natural distribution
- Cycles through fake comments to ensure variety

## Usage Examples

### Example 1: Default Usage (30% replacement)

```java
DocumentService service = new DocumentService();
String dirtySQL = service.readDirtyProcedure("sp_calcula_total_fatura", "noise", "dirty");
```

### Example 2: Custom Replacement Ratio (50% replacement)

```java
DocumentService service = new DocumentService();
String dirtySQL = service.readDirtyProcedure(
        "sp_calcula_total_fatura",
        "noise",
        "dirty",
        0.5  // Replace 50% of comments
);
```

### Example 3: Testing Different Ratios

```java
// Light noise: 10% replacement
String lightDirty = service.readDirtyProcedure("procedure_name", "noise", "dirty", 0.1);

// Medium noise: 30% replacement (default)
String mediumDirty = service.readDirtyProcedure("procedure_name", "noise", "dirty");

// Heavy noise: 70% replacement
String heavyDirty = service.readDirtyProcedure("procedure_name", "noise", "dirty", 0.7);
```

## Comment Patterns Analyzed

Based on the analysis of existing SQL files, the following comment patterns were identified:

### 1. Block Comments (Header Documentation)

```sql
{------------------------------------------------------------------------------
  NAME      : sp_calcula_total_fatura_mock.sql
  DATE      : 20250126
  Usage     : call sp_calcula_total_fatura_mock(p_id_fatura, p_aplicar_desc) returning decimal
  Propose   : Calculate invoice total with optional discount
  Tables    : fatura + itens_fatura + cliente
  Programmer: TestDev
  Revisions :
  01 TestDev 20250126 Initial implementation - Calculate total and apply discounts
 ------------------------------------------------------------------------------}
```

### 2. Revision Comments

```sql
-- 01 ITlca 2009/06/09 Error description and fix
-- 02 ITaoa 2009/06/17 Review order of generation
```

### 3. Inline Comments

```sql
define
lv_stat      smallint;  -- Return status
let
av_regera="N";  -- Default value
```

### 4. Section Comments

```sql
-- Variaveis para manusear os erros de SQL
-- Tratamentos de excepcoes...
-- Inicia a transacao...
```

## Configuration

The default replacement ratio can be modified by changing the default value in the `readDirtyProcedure` method:

```java
public String readDirtyProcedure(String fileName, String approach, String type) {
    return readDirtyProcedure(fileName, approach, type, 0.3);  // Change 0.3 to desired ratio
}
```

## Implementation Details

### Comment Selection Strategy

- Comments are selected randomly based on the replacement ratio
- Uses `java.util.Random` for pseudo-random selection
- Fake comments are cycled through using modulo operation
- Ensures even distribution across the fake comment pool

### Preservation Logic

The implementation preserves:

- Code indentation and structure
- Non-comment code (unchanged)
- Empty lines and formatting
- SQL syntax validity
- Block comment delimiters `{` and `}`

### Filtering Rules

Only replaces comments that:

- Have more than 5 characters (excluding the `--` or delimiter)
- Are not just separator lines (e.g., `------` or `======`)
- Contain meaningful text content

## Future Enhancements

Possible improvements:

1. **Configurable comment pool**: Load fake comments from external file
2. **Pattern-specific replacement**: Replace revision comments with revision-style fakes
3. **Semantic preservation**: Ensure fake comments match the context (e.g., keep DATE fields as dates)
4. **Statistics tracking**: Report how many comments were replaced
5. **Reversibility**: Store mapping to allow reverting dirty versions
6. **Multiple strategies**: Different replacement algorithms (sequential, random, weighted)

## Notes

- The implementation is idempotent within the same random seed
- Replacement ratio of 0.0 returns the original content
- Replacement ratio > 1.0 is clamped to 1.0 (100%)
- All SQL syntax remains valid after comment replacement
- Original files are never modified (read-only operation)

