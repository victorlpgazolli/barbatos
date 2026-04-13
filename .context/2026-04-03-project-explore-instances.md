# Refactoring Class Inspection Mode

This implementation plan refactors the `DEBUG_INSPECT_CLASS` mode to introduce interactive, collapsible sections for both static properties/methods and live object instances. It leverages Frida to identify memory handles for instances and dynamically extract their attribute values via reflection.

## User Review Required

Nenhuma revisão adicional é necessária. As respostas para as perguntas anteriores foram incorporadas ao plano:
1. **Abordagem do Objection**: A abordagem de usar Frida + Reflection é **exatamente a mesma** que o Objection usa por debaixo dos panos. Para tornar ainda mais parecido com o Objection e evitar problemas com o Garbage Collector, nós vamos armazenar cada instância encontrada dentro de um dicionário (cache) no JavaScript (ex: `instances[hash] = instance`). Dessa forma mantemos a referência viva enquanto estamos inspecionando e acessamos os dados de forma segura!
2. **Limite de instâncias**: A listagem irá retornar apenas as 50 primeiras propriedades encontradas para não explodir a memória do TUI, mas o Frida continuará contando para podermos mostrar exatamente o total no rodapé ("... and 1500 more").

## Proposed Changes

---

### Frida Agent JS
Adding new functions to handle instance enumeration and attribute extraction.

#### [MODIFY] [agent.js](./barbatos/bridge/agent.js)
- Add a global map `global.instanceCache = {}` to safely hold references to retrieved instances. *Disclaimer: O cache mantém apenas a referência (wrapper) do objeto e não os seus dados. Toda vez que um objeto é inspecionado, validamos seus valores direto na heap em tempo real.*
- Add `listinstances(className)`: Uses `Java.choose` to iterate through the heap. It will count all instances to get a `totalCount`, but will only push the first 50 into a result array. Each item will have an `id` (e.g. `instance.hashCode()`), `handle` (from `instance.$handle.toString()`) and a summary (from `instance.toString()`). It safely adds them to `global.instanceCache[id] = instance`. Returns `{ instances: [...], total: count }`.
- Add `inspectinstance(className, id)`: Fetches the instance from `global.instanceCache[id]`. Iterates its `class.getDeclaredFields()`, sets `.setAccessible(true)`, and pulls each field's `getName()`, `getType().getSimpleName()`, and `.get(instance)`. Returns an array of `{ name, type, value }`. Ao pressionar 'R' (refresh) na TUI, este método é disparado novamente, o JS pegará a referência no cache e rodará reflection novamente lendo os valores mais recentes na memória.

#### [MODIFY] [bridge.py](./barbatos/bridge/bridge.py)
- Map `listInstances` JSON-RPC method to `script.exports_sync.listinstances`.
- Map `inspectInstance` JSON-RPC method to `script.exports_sync.inspectinstance`.

---

### Kotlin RPC Client & Data Classes
Update the internal models to support instances.

#### [MODIFY] [RpcClient.kt](./barbatos/src/nativeMain/kotlin/RpcClient.kt)
- Create `ListInstancesParams`, `InspectInstanceParams` and their respective `@Serializable` data classes.
- Create `InstanceInfo` (id, handle, summary) and `InstanceAttribute` (name, type, value) models.
- Create `ListInstancesResult` containing `instances: List<InstanceInfo>` and `totalCount: Int`.
- Expose `listInstances` and `inspectInstance` suspension functions that issue Ktor calls and return the parsed data.

#### [MODIFY] [AppState.kt](./barbatos/src/nativeMain/kotlin/AppState.kt)
- Add state variables for the collapsible UI:
  - `var inspectStaticExpanded: Boolean = false`
  - `var inspectInstancesExpanded: Boolean = false`
  - `var inspectInstancesList: List<InstanceInfo>? = null` (null = not fetched yet)
  - `var inspectInstancesTotalCount: Int = 0`
  - `var inspectExpandedInstances: MutableMap<String, List<InstanceAttribute>?> = mutableMapOf()` (maps string ID -> attributes, null means fetching)
- Add atomic references properties for async results of fetching instances and fetching specific instance attributes.

---

### Kotlin TUI Handling
Wiring up navigation and specific inputs.

#### [MODIFY] [Main.kt](./barbatos/src/nativeMain/kotlin/Main.kt) & [InputHandler.kt](./barbatos/src/nativeMain/kotlin/InputHandler.kt)
- Add handlers in `KeyEvent.Enter` context when in `AppMode.DEBUG_INSPECT_CLASS` to:
  - Toggle static properties if the selection is on the `Expand static attributes and methods` layout item.
  - Toggle instances section. If turning it on for the first time, dispatch the `RpcClient.listInstances` fetch.
  - Toggle a specific instance. If turned on, dispatch the `RpcClient.inspectInstance` fetch.
- Add handler for `KeyEvent.Char` when `key.c == 'r' || key.c == 'R'` to re-dispatch `RpcClient.inspectInstance` for the currently selected instance. 

#### [MODIFY] [Renderer.kt](./barbatos/src/nativeMain/kotlin/Renderer.kt)
- Build a flattened list of UI elements at render-time sequentially. Because of the collapsible nature, we dynamically calculate lines to properly determine the max `selectedClassIndex`.
- Print types and values with VS-Code-like typography. (e.g. `Ansi` constants: boolean = yellow, numbers = light blue/green, Strings = orange, member variables = grey/dark-white).
- Proper indentation support via string padding based on item depth.
- Show `(press R to refresh)` in `DIM_GRAY` next to expanded instances.

## Open Questions

Nenhuma pergunta em aberto. O plano está pronto para execução.

## Verification Plan

### Manual Verification
1. Launch the `bridge.py` service.
2. Build and launch Barbatos via gradle native executable run.
3. Open a TMUX instance and attach to a debug session.
4. Issue `debug_inspect_class` on a known Android widget like `android.view.View`, switch on the "Found instances" view.
5. Select an instance to extract its fields. Press 'R' and verify fields reset/refresh without duplicating.

# Nested Instance Attribute Inspection

O usuário deseja que ao selecionar um atributo de um objeto focado e apertar `Enter`, esse atributo seja expandido para mostrar as suas próprias propriedades (caso seja um objeto), permitindo uma exploração hierárquica (nested) infinitamente profunda no modelo de dados vivo gerenciado pelo Frida.

## User Review Required

Nenhuma decisão crítica no sentido de UX, os comportamentos seguirão a lógica exata já esperada para o toggle e "R" (refresh).

## Proposed Changes

### Frida Agent (`agent.js`)
Para que possamos inspecionar o valor de um atributo, precisamos que ele seja exposto da mesma forma que uma "Instância principal". 
- No `inspectinstance`, ao obtermos o valor do campo via `field.get()`, verificaremos se ele é um objeto (não-nulo e não-primitivo).
- Em caso positivo, registraremos esse `fieldVal` também no `global.instanceCache[childId]`.
- Vamos obter o nome real da classe desse atributo em runtime via `fieldVal.getClass().getName()` e seu `id` via `fieldVal.hashCode()`.
- O retorno JSON do atributo ganhará os campos opcionais `childId` e `childClassName`.

### Kotlin Data Models (`RpcClient.kt`)
- A classe de dados `InstanceAttribute` vai passar a mapear o `childId: String?` e `childClassName: String?`. 

### State Management & Rendering (`AppState.kt` & `Renderer.kt`)
- Já temos `inspectExpandedInstances: MutableMap<String, List<InstanceAttribute>?>` que mapeia um `id` pros seus atributos. Isso é perfeito pois funciona para qualquer ID independente se é Root ou um Child.
- Modificar a construção das linhas de renderização (`buildInspectRows` no `AppState.kt`) para virar uma **função recursiva** que gera o TUI. Para cada atributo, ver se o seu `childId` está na lista de expermida; se estiver, chama a recursão incrementando a profundidade (`depth`).
- Modificar o `InspectRow` para receber um novo parâmetro genérico `depth: Int` em `InstanceAttributeRow`.
- O `Renderer.kt` agora fará o _padding_ visual (recuo) baseado no valor de `depth` da linha do atributo.

### Input Management (`Main.kt`)
- Na hora que o `Enter` for pressinado e a _row_ selecionada for um `InstanceAttributeRow`:
  - Se ele tiver um `childId` (ou seja, for um Objeto inspecionável e não um tipo primitivo), nós damos _toggle_ da mesma forma que fizemos com a Instância Root, iniciando a requisição `RpcClient.inspectInstance(childClassName, childId)`.

## Open Questions
- Nenhum - plano pronto para seguir.
