Analyze the PL/SQL code provided below.

Before generating the final JSON document, you MUST execute the internal **Chain-of-Thought (CoT)** reasoning steps defined in this prompt.
These CoT steps are **strictly internal** and must **NOT** appear, directly or indirectly, in the final JSON output.
The final answer must be based only on the PL/SQL code and must not introduce assumptions or business rules not supported by that code.

### PL/SQL Code

```plsql
{code}
````

# INTERNAL CHAIN-OF-THOUGHT STEPS (NOT TO BE SHOWN IN OUTPUT)

Perform the following reasoning steps internally.
Do **not** reveal, mention, summarize, or hint at these steps in the final output.

### **Step 1 — Code Segmentation and Functional Block Identification**

Identify all meaningful code structures, including:

* procedures or functions
* cursors
* loops
* conditional branches (IF / CASE)
* transactional boundaries (COMMIT / ROLLBACK)

For each block, determine its **technical role** (e.g., “This cursor retrieves all overdue invoices”).
This step is internal and used only to support later reasoning.

### **Step 2 — Business Rule Extraction**

For every conditional predicate, comparison, data manipulation, or WHERE clause:

* derive explicit **business rules** that can be *directly traced* to the code
* avoid interpretation beyond what the code supports
* ensure every rule is grounded in an observable condition or assignment

Examples (not to be included):

* “The procedure updates customers whose LAST_ACTIVITY_DATE is older than 90 days.”
* “A surcharge is applied only when ORDER_TYPE = 'EXPRESS'.”

### **Step 3 — Flow Synthesis**

Integrate all extracted rules and identified blocks into a coherent, sequential business process description.

The synthesized flow must:

* respect the **actual execution order** in the code
* include branching logic and looping behavior
* reflect all conditions, outcomes, and transactional actions
* avoid adding business meaning that is not explicit in the code

The content derived here will become the foundation of the final JSON output.

## Error and Exception Handling

Describe:

* which exceptions are handled
* the conditions that may lead to each exception (based on the code)
* the business impact or protection provided by the handler (e.g., rollback to avoid inconsistent updates)

# FINAL OUTPUT REQUIREMENTS (WHAT THE MODEL MUST OUTPUT)

Now generate a **single JSON document** that incorporates the insights derived from your internal CoT reasoning.

```json
{format}
```
