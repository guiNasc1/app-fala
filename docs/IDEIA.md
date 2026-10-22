# Ideia do app-fala

Especificação enviada pelo usuário em 2026-10-22. Fonte da verdade para o escopo do produto.

## Contexto

O pai do usuário tem uma **oficina de motos** e hoje faz orçamentos e notas de serviço à mão.
O app deve deixar isso rápido: ele **dita** peças e serviços num gravador de voz, uma **IA** entende
nome, quantidade e valor de cada item, e no fim o app gera o **PDF** completo.

O usuário final é **leigo em tecnologia**: poucas telas, botões grandes e óbvios, nada de interface complexa.

## Visual

- Cores padrão **branco e vermelho escuro**, com opção de **dark mode**.
- Layout moderno e simples. O usuário não é de UI/UX: proponha boas escolhas e explique.
- **Header:** nome da oficina do usuário logado + botão para alternar dark mode.

## Login e cadastro

- Tela de login com opção de **cadastro**.
- Cada conta tem a **sua oficina com CNPJ**: nome, CNPJ, nome fantasia, endereço etc.
- **"Lembrar de mim"**: se ativo, ao abrir o app vai direto para a tela inicial.

## Navegação (footer com 4 abas)

| Aba | Conteúdo |
|---|---|
| **Início** | Home com gráficos do desempenho: valores somados por mês, com base nos orçamentos e notas emitidos |
| **Emitir** | Botão para emitir, escolhendo **Orçamento** (valores antes do serviço) ou **Nota de serviço** (valores depois do serviço feito) |
| **Histórico** | Lista de tudo que foi emitido, diferenciando orçamento e nota de serviço, com data e valor total |
| **Perfil** | Foto de perfil, nome de usuário, e-mail, CNPJ; opções **trocar de conta** e **sair** |

## Emissão por voz (o diferencial)

- Um **botão principal** na tela: aperta, grava, o usuário fala **um item por vez**.
- A cada gravação, a IA extrai e guarda: **nome do produto/serviço, quantidade, valor** (unitário ou total, conforme dito).
- A primeira fala normalmente identifica o **veículo e o cliente** (modelo, ano, placa).
- Na mesma tela aparece a **lista dos itens ditados**, com opção de **apagar** um item adicionado por engano.
- Botão final **Realizar Orçamento** (ou Nota): monta o documento com dados da **oficina, cliente e veículo**,
  todos os itens e o **total somado**, e exibe o **PDF**.
- O PDF pode ser **baixado e compartilhado** e fica registrado no **histórico** com data e valor total.

### Nomes formais

A IA deve reescrever o que foi dito num nome técnico/formal de orçamento. Exemplo:

- Dito: "um jogo de rolamento principal do virabrequim"
- No orçamento: "Rolamentos principais do virabrequim - sprocket shaft e pinion shaft, com pista interna (jogo)"

### Exemplo de sessão de ditado

1. "Quero fazer um orçamento de uma moto Suzuki GSX R1000 2020, com a placa ABC1D23."
2. "um jogo de rolamento principal do virabrequim, mil e trinta reais"
3. "duas bombas de oleo completa, mil e cento e cinco cada"
4. "dois cilindros dianteiros 88,90 milímetros, novecentos e noventa reais cada"
5. "serviço de mão de obra, desmontagem do motor, lavagem, controle de torque e folga, tres mil reais"

Resultado esperado: 1 × 1.030,00 + 2 × 1.105,00 + 2 × 990,00 + 1 × 3.000,00 = **R$ 8.220,00**.
