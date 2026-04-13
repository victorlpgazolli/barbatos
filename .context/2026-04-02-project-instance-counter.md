# Melhorias de UX na TUI do Barbatos

Três melhorias na experiência do usuário ao usar o debugger interativo.

## Proposed Changes

### 1. Lista de classes dinâmica baseada no tamanho do terminal

Atualmente, `renderClassList` usa `maxItems = 15` fixo. A mudança fará com que o número de classes visíveis se adapte à altura do terminal.

#### [MODIFY] [Renderer.kt](./barbatos/src/nativeMain/kotlin/Renderer.kt)

- Na função `render()`, já obtém `termWidth` via `Terminal.getSize()` — vamos passar a obter também `termHeight`
- Em `renderClassList()`: calcular `maxItems` dinamicamente baseado na altura do terminal, subtraindo as linhas fixas (logo ~8 linhas, welcome ~5 linhas, status ~1 linha, input box ~3 linhas, padding ~2 linhas ≈ 19 linhas fixas). Reservar 1 linha para o indicador "... and N more"
- Fórmula: `maxItems = max(3, termHeight - fixedLines - 1)` — o `-1` garante que sempre há espaço para o indicador "... and N more"
- O indicador "... and N more" sempre aparece se houver mais classes do que `maxItems`

---

### 2. Tmux split-window com 70% do espaço horizontal

Atualmente `appendInspectWindow` usa `tmux split-window -h` sem especificar percentual (default 50%).

#### [MODIFY] [TmuxManager.kt](./barbatos/src/nativeMain/kotlin/TmuxManager.kt)

- Alterar o comando em `appendInspectWindow` para incluir `-p 70` que define 70% do espaço para o novo painel:
  ```
  tmux split-window -h -p 70 ./build/bin/...
  ```

---

### 3. Contagem de instâncias com seta para direita (→)

Nova funcionalidade: ao pressionar `→` na listagem de classes (`DEBUG_CLASS_FILTER`), busca a quantidade de instâncias da classe selecionada e exibe como superscript.

#### [MODIFY] [agent.js](./barbatos/bridge/agent.js)

- Adicionar novo export `countinstances(className)` que usa `Java.choose()` para enumerar instâncias vivas da classe e retorna a contagem

#### [MODIFY] [bridge.py](./barbatos/bridge/bridge.py)

- Adicionar método `count_instances(className)` no `FridaBridge`
- Adicionar handler `"countInstances"` no `handle_rpc()`

#### [MODIFY] [RpcClient.kt](./barbatos/src/nativeMain/kotlin/RpcClient.kt)

- Adicionar tipos serializáveis: `CountInstancesParams`, `JsonRpcRequestCountInstances`, `JsonRpcCountInstancesResponse`
- Adicionar método `suspend fun countInstances(className: String): Pair<Int?, String?>`

#### [MODIFY] [AppState.kt](./barbatos/src/nativeMain/kotlin/AppState.kt)

- Adicionar campo `var instanceCounts: MutableMap<String, Int> = mutableMapOf()` — armazena contagem por classe (cache)
- Adicionar campo `var isFetchingInstances: Boolean = false` — flag de loading
- Adicionar campo `val sharedInstanceCountResult: AtomicReference<Pair<String, Int>?> = AtomicReference(null)` — resultado assíncrono
- Adicionar campo `val sharedInstanceCountError: AtomicReference<String?> = AtomicReference(null)` — erro assíncrono

#### [MODIFY] [Main.kt](./barbatos/src/nativeMain/kotlin/Main.kt)

- No handler de `KeyEvent.ArrowRight` no modo `DEBUG_CLASS_FILTER`:
  - Se há uma classe selecionada e não está já buscando, disparar busca assíncrona via `scope.launch { RpcClient.countInstances(...) }`
  - Setar `isFetchingInstances = true`
- No handler de `KeyEvent.Timeout`:
  - Incrementar spinner se `isFetchingInstances`
  - Pollar `sharedInstanceCountResult` e `sharedInstanceCountError`
  - Ao receber resultado, salvar em `instanceCounts[className] = count` e setar `isFetchingInstances = false`

#### [MODIFY] [Renderer.kt](./barbatos/src/nativeMain/kotlin/Renderer.kt)

- Em `renderClassList()`: após formatar o nome da classe, verificar se existe uma contagem em `state.instanceCounts[className]` e, se sim, adicionar o superscript `⁽N⁾` (usando caracteres Unicode superscript)
- Em `renderClassFetchStatus()` ou novo helper: quando `state.isFetchingInstances`, mostrar "Searching instances..." com spinner

## Detalhes de implementação do superscript

Mapeamento de dígitos para superscript Unicode:
- `0` → `⁰`, `1` → `¹`, `2` → `²`, `3` → `³`, `4` → `⁴`, `5` → `⁵`, `6` → `⁶`, `7` → `⁷`, `8` → `⁸`, `9` → `⁹`
- Formato: `⁽` + dígitos superscript + `⁾`

## Verification Plan

### Automated Tests
- `./gradlew build` — garantir que compila sem erros

### Manual Verification
- Testar com terminal de diferentes alturas para verificar que a lista de classes se adapta
- Verificar que o indicador "... and N more" sempre aparece quando há mais classes
- Verificar que o painel de inspeção ocupa ~70% do espaço horizontal
- Verificar que `→` no modo class filter dispara busca de instâncias e exibe o superscript
