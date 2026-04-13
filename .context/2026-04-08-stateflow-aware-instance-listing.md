# StateFlow-Aware Instance Listing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar a listagem de instâncias do Barbatos ciente de padrões reativos (StateFlow, LiveData), mostrando apenas instâncias que o app considera "atuais" e indicando na UI como cada instância foi detectada.

**Architecture:** A detecção ocorre em `bridge/agent.js` (Frida, rodando dentro do processo Android). A estratégia primária lê `StateFlowImpl._state$volatile` via reflection para obter o valor atual de cada StateFlow. Para LiveData, lê `LiveData.mData`. Fallback para heap scan convencional quando a classe não usa nenhum dos dois. O campo `detectionMethod` na resposta informa o Kotlin client e a UI como a instância foi encontrada.

**Tech Stack:** Frida (JavaScript), Kotlin/Native (Barbatos client), Kotlin Multiplatform, ART/JVM reflection

**Background:** Ver `docs/discoveries/2026-04-08-instance-detection-in-reactive-kotlin-apps.md` para o root cause completo do problema.

---

## File Map

| Arquivo | O que muda |
|---|---|
| `bridge/agent.js` | Refatorar `listinstances` e `countinstances`; adicionar `resolveActiveInstances()`; remover código WeakReference+GC |
| `src/nativeMain/kotlin/RpcClient.kt` | Adicionar campo `detectionMethod` em `InstanceInfo` |
| `src/nativeMain/kotlin/Renderer.kt` | Mostrar badge de método de detecção na lista de instâncias |

---

### Task 1: Extrair `resolveActiveInstances` como função reutilizável em agent.js

Atualmente `listinstances` e `countinstances` duplicam a lógica de detecção. Extrair para uma função compartilhada e limpar o código WeakReference+GC que foi invalidado pela investigação.

**Files:**
- Modify: `bridge/agent.js`

- [ ] **Step 1: Ler o agent.js atual e localizar as funções alvo**

  Identificar as funções `countinstances` (linha ~182) e `listinstances` (linha ~217) no arquivo atual. Confirmar que ambas têm a lógica StateFlow duplicada.

- [ ] **Step 2: Adicionar a função `resolveActiveInstances` antes do bloco `rpc.exports`**

  Localizar onde `rpc.exports = {` começa e inserir a função antes disso:

  ```javascript
  /**
   * Retorna instâncias "ativas" de className.
   *
   * Estratégia 1 — StateFlow: lê _state$volatile de cada StateFlowImpl no heap.
   *   Instâncias presas em continuations de coroutines (valores antigos de collectors
   *   suspensos) são ignoradas — não representam o estado atual do app.
   *
   * Estratégia 2 — LiveData: lê o campo mData de cada LiveData no heap.
   *
   * Estratégia 3 — Fallback: Java.choose convencional (retorna todas as instâncias,
   *   incluindo as presas em continuations). Usado quando className não é exposto
   *   via StateFlow nem LiveData.
   *
   * @param {string} className  Nome completo da classe Java a inspecionar.
   * @returns {{ instances: Array<{id:string, handle:string, instance:object}>, method: string }}
   */
  function resolveActiveInstances(className) {
      var found = {};

      // ── Estratégia 1: StateFlow ───────────────────────────────────────────────
      try {
          Java.choose('kotlinx.coroutines.flow.StateFlowImpl', {
              onMatch: function(sf) {
                  try {
                      // Tentar nomes de campo usados em diferentes versões do coroutines
                      var fieldNames = ['_state$volatile', '_state'];
                      for (var i = 0; i < fieldNames.length; i++) {
                          try {
                              var f = sf.getClass().getDeclaredField(fieldNames[i]);
                              f.setAccessible(true);
                              var val = f.get(sf);
                              if (val !== null && val.getClass().getName() === className) {
                                  var id = val.$handle ? val.$handle.toString() : val.hashCode().toString();
                                  if (!found[id]) {
                                      found[id] = {
                                          id: id,
                                          handle: val.$handle ? val.$handle.toString() : "",
                                          instance: val
                                      };
                                  }
                              }
                              break; // campo encontrado, não tentar o próximo
                          } catch(fieldErr) {}
                      }
                  } catch(e) {}
              },
              onComplete: function() {}
          });
      } catch(e) {}

      if (Object.keys(found).length > 0) {
          return { instances: Object.values(found), method: 'stateflow' };
      }

      // ── Estratégia 2: LiveData ────────────────────────────────────────────────
      try {
          Java.choose('androidx.lifecycle.LiveData', {
              onMatch: function(ld) {
                  try {
                      var f = ld.getClass().getDeclaredField('mData');
                      f.setAccessible(true);
                      var val = f.get(ld);
                      if (val !== null && val.getClass().getName() === className) {
                          var id = val.$handle ? val.$handle.toString() : val.hashCode().toString();
                          if (!found[id]) {
                              found[id] = {
                                  id: id,
                                  handle: val.$handle ? val.$handle.toString() : "",
                                  instance: val
                              };
                          }
                      }
                  } catch(e) {}
              },
              onComplete: function() {}
          });
      } catch(e) {}

      if (Object.keys(found).length > 0) {
          return { instances: Object.values(found), method: 'livedata' };
      }

      // ── Estratégia 3: Fallback heap scan ─────────────────────────────────────
      var seen = {};
      try {
          Java.choose(className, {
              onMatch: function(instance) {
                  var id = instance.$handle ? instance.$handle.toString() : instance.hashCode().toString();
                  if (!seen[id]) {
                      seen[id] = true;
                      found[id] = {
                          id: id,
                          handle: instance.$handle ? instance.$handle.toString() : "",
                          instance: instance
                      };
                  }
              },
              onComplete: function() {}
          });
      } catch(e) {}

      return { instances: Object.values(found), method: 'heap_scan' };
  }
  ```

- [ ] **Step 3: Reescrever `countinstances` usando `resolveActiveInstances`**

  Substituir a implementação atual (que ainda tem código WeakReference+GC) por:

  ```javascript
  countinstances: function(className) {
      try {
          var result;
          Java.perform(function() {
              result = resolveActiveInstances(className);
          });
          return result ? result.instances.length : 0;
      } catch(e) {
          return -1;
      }
  },
  ```

- [ ] **Step 4: Reescrever `listinstances` usando `resolveActiveInstances`**

  Substituir a implementação atual por:

  ```javascript
  listinstances: function(className) {
      try {
          var instances = [];
          var method = 'heap_scan';
          Java.perform(function() {
              var result = resolveActiveInstances(className);
              method = result.method;
              result.instances.forEach(function(item) {
                  if (instances.length < 1000) {
                      instanceCache[item.id] = item.instance;
                      instances.push({
                          id: item.id,
                          handle: item.handle,
                          summary: item.instance.toString() + " (" + getInstanceStatus(item.instance) + ")",
                          detectionMethod: method
                      });
                  }
              });
          });
          return { instances: instances, totalCount: instances.length, detectionMethod: method };
      } catch(e) {
          return { error: e.toString(), instances: [], totalCount: 0, detectionMethod: 'error' };
      }
  },
  ```

- [ ] **Step 5: Verificar que o bridge ainda sobe sem erros**

  ```bash
  python3 ./bridge/bridge.py --serial <seu-serial> > /tmp/bridge_test.log 2>&1 &
  sleep 4 && curl -s http://127.0.0.1:8080/ping
  # Esperado: {"status": "pong"}
  ```

- [ ] **Step 6: Testar `listInstances` e confirmar campo `detectionMethod` na resposta**

  ```bash
  curl -s -X POST http://127.0.0.1:8080/rpc \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"listInstances","params":{"className":"com.example.UiState"},"id":1}' \
    | python3 -c "
  import json,sys
  r = json.load(sys.stdin)
  result = r['result']
  print('detectionMethod:', result.get('detectionMethod'))
  print('totalCount:', result.get('totalCount'))
  for i in result['instances']:
      print(' id=%s method=%s' % (i['id'], i.get('detectionMethod')))
  "
  # Esperado:
  # detectionMethod: stateflow
  # totalCount: 3
  #   id=55173260 method=stateflow
  #   id=419386273 method=stateflow
  #   id=-237061690 method=stateflow
  ```

- [ ] **Step 7: Testar `countInstances`**

  ```bash
  curl -s -X POST http://127.0.0.1:8080/rpc \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"countInstances","params":{"className":"com.example.UiState"},"id":1}'
  # Esperado: {"jsonrpc": "2.0", "result": 3, "id": 1}
  ```

- [ ] **Step 8: Matar o processo do bridge de teste**

  ```bash
  kill $(lsof -ti:8080) 2>/dev/null
  ```

---

### Task 2: Adicionar `detectionMethod` ao modelo Kotlin

O campo `detectionMethod` agora é retornado pelo bridge. O client Kotlin precisa deserializá-lo.

**Files:**
- Modify: `src/nativeMain/kotlin/RpcClient.kt`

- [ ] **Step 1: Adicionar campo `detectionMethod` em `InstanceInfo`**

  Localizar a data class `InstanceInfo` (linha ~94 em RpcClient.kt):

  ```kotlin
  @Serializable
  data class InstanceInfo(
      val id: String,
      val handle: String,
      val summary: String
  )
  ```

  Substituir por:

  ```kotlin
  @Serializable
  data class InstanceInfo(
      val id: String,
      val handle: String,
      val summary: String,
      val detectionMethod: String = "heap_scan"
  )
  ```

- [ ] **Step 2: Adicionar campo `detectionMethod` em `ListInstancesResult`**

  Localizar a data class `ListInstancesResult` (linha ~101):

  ```kotlin
  @Serializable
  data class ListInstancesResult(
      val instances: List<InstanceInfo>,
      val totalCount: Int
  )
  ```

  Substituir por:

  ```kotlin
  @Serializable
  data class ListInstancesResult(
      val instances: List<InstanceInfo>,
      val totalCount: Int,
      val detectionMethod: String = "heap_scan"
  )
  ```

- [ ] **Step 3: Compilar e confirmar que não há erros**

  ```bash
  ./gradlew macosArm64MainKlibrary 2>&1 | tail -5
  # Esperado: BUILD SUCCESSFUL
  ```

---

### Task 3: Mostrar método de detecção na UI — tela de instâncias

A tela de listagem de instâncias deve mostrar um indicador sutil de como as instâncias foram detectadas, para que o usuário saiba se está vendo "estado atual" (StateFlow) ou "heap scan" convencional.

**Files:**
- Modify: `src/nativeMain/kotlin/AppState.kt` — adicionar campo para guardar o `detectionMethod` da última listagem
- Modify: `src/nativeMain/kotlin/Renderer.kt` — exibir badge na lista de instâncias

- [ ] **Step 1: Adicionar `instancesDetectionMethod` ao AppState**

  Localizar em `AppState.kt` onde `inspectInstancesList` é declarado (linha ~107) e adicionar logo abaixo:

  ```kotlin
  var inspectInstancesList: List<InstanceInfo>? = null,
  var instancesDetectionMethod: String = "heap_scan",  // ← adicionar esta linha
  var inspectInstancesTotalCount: Int = 0,
  ```

- [ ] **Step 2: Popular `instancesDetectionMethod` quando instâncias chegam**

  Buscar em `Main.kt` onde `sharedInstancesListResult` é consumido e o estado é atualizado. Deve haver algo como:

  ```kotlin
  val result = state.sharedInstancesListResult.value
  if (result != null) {
      state.sharedInstancesListResult.value = null
      state.inspectInstancesList = result.instances
      state.inspectInstancesTotalCount = result.totalCount
  ```

  Adicionar logo após `state.inspectInstancesTotalCount = result.totalCount`:

  ```kotlin
      state.instancesDetectionMethod = result.detectionMethod
  ```

- [ ] **Step 3: Localizar onde a lista de instâncias é renderizada em Renderer.kt**

  Buscar por `inspectInstancesList` ou `instances` no Renderer.kt para encontrar a função que renderiza a lista de instâncias (provavelmente dentro de `renderInspectClassList`).

- [ ] **Step 4: Adicionar badge de detecção acima da lista de instâncias**

  Dentro da função que renderiza instâncias, antes de listar os itens, adicionar:

  ```kotlin
  // Badge do método de detecção
  val (badgeColor, badgeLabel) = when (state.instancesDetectionMethod) {
      "stateflow" -> Pair(C_GREEN,    "● StateFlow  — apenas valores atuais")
      "livedata"  -> Pair(C_BLUE,     "● LiveData   — apenas valores atuais")
      else        -> Pair(C_MID_GRAY, "○ Heap scan  — inclui continuations suspensas")
  }
  buf.append("  $badgeColor$badgeLabel$RESET\n")
  ```

  Usar as constantes de cor já existentes no objeto `Renderer` (`C_GREEN`, `C_BLUE`, `C_MID_GRAY`, `RESET`).

- [ ] **Step 5: Compilar e confirmar que não há erros**

  ```bash
  ./gradlew macosArm64MainKlibrary 2>&1 | tail -5
  # Esperado: BUILD SUCCESSFUL
  ```

- [ ] **Step 6: Confirmar visualmente no app**

  - Abrir o Barbatos, navegar até uma classe que usa StateFlow (ex: `UiState`)
  - Verificar se aparece `● StateFlow — apenas valores atuais` acima da lista
  - Navegar até uma classe que não usa StateFlow (ex: `android.widget.TextView`)
  - Verificar se aparece `○ Heap scan — inclui continuations suspensas`

---

## Notas de Implementação

### Por que não usar `WeakReference` + GC

A abordagem WeakReference+GC foi investigada e descartada. Ver `docs/discoveries/2026-04-08-instance-detection-in-reactive-kotlin-apps.md` para o root cause completo. Em resumo: instâncias presas em continuations de coroutines têm referências fortes legítimas e nunca são coletadas pelo GC, independente de tempo ou número de ciclos.

### Robustez do nome do campo `_state$volatile`

A Task 1 tenta `['_state$volatile', '_state']` em sequência. Se versões futuras do `kotlinx-coroutines` renomearem o campo, o código silenciosamente faz fallback para heap scan em vez de quebrar. Isso é intencional — preferir degradação graciosa a crash.

### `SharedFlow` com `replay > 0`

`SharedFlow` não tem `_state$volatile`. Se o app usar `shareIn()` com `replay > 0`, as instâncias mantidas no buffer de replay não serão detectadas por esta implementação. O fallback para heap scan cobrirá esses casos, mas retornará todas as instâncias (incluindo as em continuations).
