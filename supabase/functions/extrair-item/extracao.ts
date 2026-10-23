// Regras da extração: o prompt, o formato JSON que a IA deve devolver e a limpeza da resposta.
// É a parte que não depende do provedor (Gemini ou Claude).

export type TipoDocumento = "ORCAMENTO" | "NOTA";

export interface Veiculo {
  modelo: string;
  ano: string;
  placa: string;
  cor: string;
  quilometragem: string;
}

export interface Cliente {
  nome: string;
  telefone: string;
}

/** Item como a IA devolve: valor em reais, que pode ser unitário ou o total do item. */
interface ItemDaIa {
  descricao: string;
  tipo: "PECA" | "SERVICO";
  quantidade: number;
  valor: number;
  valorEhTotal: boolean;
}

interface RespostaDaIa {
  veiculo: Veiculo;
  cliente: Cliente;
  itens: ItemDaIa[];
  observacao: string;
}

/** Item como o app recebe: valores já em centavos, unitário e total. */
export interface Item {
  descricao: string;
  tipo: "PECA" | "SERVICO";
  quantidade: number;
  valorUnitarioCentavos: number;
  valorTotalCentavos: number;
}

export interface Resultado {
  veiculo: Veiculo;
  cliente: Cliente;
  itens: Item[];
  observacao: string;
}

export const PROMPT = `Você ajuda uma oficina de motos a montar orçamentos e notas de serviço.
O mecânico dita no celular e você recebe o texto de UMA fala, vindo do reconhecimento de voz:
pode ter erros de transcrição, faltar pontuação e trazer números por extenso.

Extraia da fala:
1. Dados do veículo (marca e modelo, ano, placa, cor, quilometragem) e do cliente (nome, telefone),
   quando forem ditos. Normalmente vêm na primeira fala. Campo não dito = texto vazio.
   Placa em maiúsculas, sem hífen nem espaço (ex.: ABC1D23).
2. Cada peça ou serviço citado, com:
   - descricao: o nome formal, como num orçamento profissional de oficina de motos. Use a
     terminologia técnica usual e a descrição técnica padrão da peça; pode completar com o que
     é próprio da peça, mas não invente marca, medida, código ou quantidade que não foram ditos.
     Mantenha medidas ditas (ex.: "88,90 mm"). Escreva "(jogo)" ou "(par)" só quando a pessoa disser
     "jogo" ou "par"; nesse caso a quantidade conta jogos/pares. Itens contados um a um ("dois cilindros")
     ficam no singular, sem "(par)": a quantidade já diz quantos são.
     Comece com letra maiúscula.
     Exemplo: "um jogo de rolamento principal do virabrequim" vira
     "Rolamentos principais do virabrequim - sprocket shaft e pinion shaft, com pista interna (jogo)".
   - tipo: SERVICO para mão de obra e serviços; PECA para peças e materiais.
   - quantidade: número de unidades ("duas" = 2, "um jogo" = 1, "dois pares" = 2). Se não disser, 1.
   - valor: em reais, como número ("mil e cento e cinco" = 1105; "novecentos e noventa" = 990;
     "mil e trinta" = 1030; "trinta e cinco e cinquenta" = 35.5). Cuidado: medidas como
     "88,90 milímetros" não são preço.
   - valorEhTotal: false quando o valor é de cada unidade ("cada", "a unidade", ou quantidade 1);
     true quando o valor dito é o total de várias unidades ("as duas por duzentos").
3. observacao: frase curta e simples para o mecânico quando algo faltou ou não deu para entender
   (ex.: "Faltou o valor da vela de ignição."). Se estiver tudo certo, texto vazio.
   Item sem valor dito: não coloque em itens, explique na observacao.

Se a fala trouxer só o veículo/cliente, itens fica vazio. Se trouxer vários itens, liste todos.`;

/** Esquema JSON da resposta. Serve para o Gemini (responseJsonSchema) e para o Claude (json_schema). */
export const ESQUEMA = {
  type: "object",
  properties: {
    veiculo: {
      type: "object",
      properties: {
        modelo: { type: "string", description: "Marca e modelo, ex.: Suzuki GSX-R1000" },
        ano: { type: "string" },
        placa: { type: "string" },
        cor: { type: "string" },
        quilometragem: { type: "string" },
      },
      required: ["modelo", "ano", "placa", "cor", "quilometragem"],
      additionalProperties: false,
    },
    cliente: {
      type: "object",
      properties: {
        nome: { type: "string" },
        telefone: { type: "string" },
      },
      required: ["nome", "telefone"],
      additionalProperties: false,
    },
    itens: {
      type: "array",
      items: {
        type: "object",
        properties: {
          descricao: { type: "string" },
          tipo: { type: "string", enum: ["PECA", "SERVICO"] },
          quantidade: { type: "number" },
          valor: { type: "number", description: "Em reais" },
          valorEhTotal: { type: "boolean" },
        },
        required: ["descricao", "tipo", "quantidade", "valor", "valorEhTotal"],
        additionalProperties: false,
      },
    },
    observacao: { type: "string" },
  },
  required: ["veiculo", "cliente", "itens", "observacao"],
  additionalProperties: false,
};

/** Monta a mensagem do usuário: a fala e o contexto que ajuda a IA (tipo e moto já identificada). */
export function montarPedido(texto: string, tipo: TipoDocumento, veiculo: Veiculo | null): string {
  const documento = tipo === "NOTA" ? "nota de serviço (serviço já feito)" : "orçamento (antes do serviço)";
  const moto = veiculo && veiculo.modelo
    ? `Moto já identificada neste documento: ${[veiculo.modelo, veiculo.ano].filter(Boolean).join(" ")}.`
    : "A moto ainda não foi identificada neste documento.";
  return `Documento: ${documento}.\n${moto}\nFala do mecânico: "${texto}"`;
}

const texto = (v: unknown): string => (typeof v === "string" ? v.trim() : "");

/**
 * Confere e limpa a resposta da IA: nunca confie cegamente no JSON de um modelo.
 * Converte reais em centavos e calcula unitário/total conforme o valor dito.
 */
export function normalizar(bruto: unknown): Resultado {
  const r = (bruto ?? {}) as Partial<RespostaDaIa>;
  const v = (r.veiculo ?? {}) as Partial<Veiculo>;
  const c = (r.cliente ?? {}) as Partial<Cliente>;

  const itens: Item[] = [];
  for (const i of Array.isArray(r.itens) ? r.itens : []) {
    const descricao = texto(i?.descricao);
    const valor = Number(i?.valor);
    if (!descricao || !Number.isFinite(valor) || valor <= 0) continue;
    const quantidade = Number.isFinite(Number(i.quantidade)) && Number(i.quantidade) > 0 ? Number(i.quantidade) : 1;
    const centavos = Math.round(valor * 100);
    const unitario = i.valorEhTotal ? Math.round(centavos / quantidade) : centavos;
    const total = i.valorEhTotal ? centavos : Math.round(centavos * quantidade);
    itens.push({
      descricao,
      tipo: i.tipo === "SERVICO" ? "SERVICO" : "PECA",
      quantidade,
      valorUnitarioCentavos: unitario,
      valorTotalCentavos: total,
    });
  }

  return {
    veiculo: {
      modelo: texto(v.modelo),
      ano: texto(v.ano),
      placa: texto(v.placa).toUpperCase().replace(/[^A-Z0-9]/g, ""),
      cor: texto(v.cor),
      quilometragem: texto(v.quilometragem),
    },
    cliente: { nome: texto(c.nome), telefone: texto(c.telefone) },
    itens,
    observacao: texto(r.observacao),
  };
}
