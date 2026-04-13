# Discovery: Instance Detection in Reactive Kotlin Apps

**Date:** 2026-04-08  
**Context:** Implementando filtro de "instâncias ativas" na tela de inspeção de classes do Barbatos.

---

## O Problema

Ao inspecionar uma classe como `UiState` num app Android com arquitetura MVI/reactive, o `Java.choose()` do Frida retornava 21 instâncias. Na prática, o app tinha apenas 3 estados "ativos" (um por tela). As outras 18 eram valores antigos correspondentes a cada keystroke digitado pelo usuário.

---

## Hipótese Inicial (Errada)

As instâncias extras eram "zumbis" — objetos sem referências fortes, que o GC ainda não havia coletado. A solução tentada: `WeakReference` + `System.gc()` agressivo (5x, 1 segundo de delay).

**Resultado:** 21 de 21 instâncias sobreviveram ao GC. A hipótese estava errada.

---

## Root Cause Real

As instâncias não são zumbis. São **variáveis locais em coroutines suspensas**.

Em arquitetura reativa Kotlin com `StateFlow`:

```kotlin
// Cada collector suspenso retém a última versão do estado que recebeu
viewModelScope.launch {
    stateFlow.collect { uiState ->  // ← uiState é variável local
        processState(uiState)        // se esta função suspende,
                                     // uiState fica vivo na continuation
    }
}
```

Quando uma coroutine suspende, o runtime do Kotlin serializa todas as variáveis locais no objeto `Continuation`. Essas são **referências fortes legítimas** — a JVM/ART nunca as coleta, independente de quantas vezes `System.gc()` for chamado.

Com múltiplos collectors (UI layer, business logic, middleware, analytics, etc.), cada um suspende com um valor diferente do `UiState` em sua continuation. Isso explica as 18 instâncias "extras":

```
StateFlow._state$volatile  → UiState("tggffff")  ← valor atual (1 instância)
Continuation collector 1   → UiState("tggffff")  ← mesma versão, collector diferente
Continuation collector 2   → UiState("tggfff")   ← versão anterior
Continuation collector 3   → UiState("tggff")    ← ...
...
```

### Prova via Frida

```javascript
// ANTES do GC: 21 instâncias
// APÓS 5x System.gc() com 200ms de intervalo (1s total): ainda 21 instâncias
// → confirma que nenhuma é coletável
```

### Onde fica o "estado atual" de verdade

O `StateFlowImpl` interno usa um campo `_state$volatile` que sempre contém o valor mais recente emitido. Esse é o único valor que o app considera "atual":

```javascript
// Via Frida:
var f = stateFlowImpl.getClass().getDeclaredField('_state$volatile');
f.setAccessible(true);
var currentValue = f.get(stateFlowImpl); // ← o estado real do app
```

---

## Solução

Em vez de tentar filtrar via GC, ler diretamente `StateFlowImpl._state$volatile` de todas as instâncias de `StateFlowImpl` no heap, filtrando apenas aquelas cujo valor atual é do tipo inspecionado.

```javascript
Java.choose('kotlinx.coroutines.flow.StateFlowImpl', {
    onMatch: function(sf) {
        var f = sf.getClass().getDeclaredField('_state$volatile');
        f.setAccessible(true);
        var val = f.get(sf);
        if (val !== null && val.getClass().getName() === targetClassName) {
            // val é uma instância "ativa atual"
        }
    }
});
```

**Resultado:** 21 → 3 instâncias. Exatamente os 3 estados ativos do app (LoginState, AccessRecoveryState, PreLoginHomeStepState).

---

## Limitações Conhecidas

| Cenário | Comportamento atual |
|---|---|
| Classe não exposta via StateFlow | Fallback para `Java.choose` convencional (retorna todas) |
| LiveData (`MutableLiveData.mData`) | Não detectado ainda — precisa de suporte explícito |
| `SharedFlow` com `replay > 0` | Não detectado — `SharedFlow` não tem `_state$volatile` |
| Campo renomeado em versão futura do Coroutines | Vai silenciosamente fazer fallback para heap scan |

---

## Referências

- `kotlinx.coroutines.flow.StateFlowImpl` — implementação interna do StateFlow
- `kotlinx.coroutines.flow.internal.AbstractSharedFlow` — superclasse com `slots` (collectors)
- `kotlinx.coroutines.internal.BaseContinuationImpl` — onde variáveis locais ficam durante suspend
- Frida `Java.choose()` — itera o heap do ART, encontra objetos com ou sem referências fortes
