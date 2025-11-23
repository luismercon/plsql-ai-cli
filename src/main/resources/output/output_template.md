---
timestamp: 
processing_time_s: 
prompt_tokens: 
completion_tokens: 
total_tokens: 
Model_ID: 
---

Procedure: [PROCEDURE_NAME]

## General Description: 
This procedure executes a business operation on system data, performing calculations, validations, or updates according to predefined rules.

## Business Rules (SBVR format):

1. It is mandatory that [condition or mandatory result].

2. If [condition], then [expected action].

3. It is prohibited that [invalid situation].

4. In case of [exception or null value], the system must [default action].

## Logical Flow (procedural narrative):

- Step 1: [Query or read necessary data].

- Step 2: [Execute calculations or validations].

- Step 3: [Update tables or return values].

- Step 4: [Call other procedures, if applicable].

## Identified Dependencies:

### Tables: 

| Table/View | Interaction Type | Business Rule Enforced |
|------------|------------------|------------------------|
| [TABLE_NAME] | [SELECT/INSERT/UPDATE/DELETE] | [Description of business rule enforced] |
| [TABLE_NAME] | [SELECT/INSERT/UPDATE/DELETE] | [Description of business rule enforced] |

### Calls: 
- [list of procedures or functions]
