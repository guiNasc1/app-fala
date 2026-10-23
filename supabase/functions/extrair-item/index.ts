// Edge Function "extrair-item": recebe o texto de UMA fala ditada na oficina e devolve, em JSON,
// o veículo/cliente e os itens (nome formal, quantidade, valores em centavos).
//
// Só aceita usuários logados: withSupabase({ auth: "user" }) valida o token (JWT) que o app manda.
// A chave da IA fica nos secrets do Supabase; o app nunca a vê.
// IA_PROVEDOR escolhe a IA: "gemini" (padrão, testes) ou "claude".
import { withSupabase } from "npm:@supabase/server@1.9.2";
import { perguntarAoClaude } from "./claude.ts";
import { ErroIa } from "./erros.ts";
import { montarPedido, normalizar, type TipoDocumento, type Veiculo } from "./extracao.ts";
import { perguntarAoGemini } from "./gemini.ts";

interface Pedido {
  texto?: unknown;
  tipo?: unknown;
  veiculo?: unknown;
}

const LIMITE_DA_FALA = 1000;

export default {
  fetch: withSupabase({ auth: "user" }, async (req, _ctx) => {
    if (req.method !== "POST") return erro("Use POST.", 405);

    let corpo: Pedido;
    try {
      corpo = await req.json();
    } catch {
      return erro("Corpo da requisição inválido.", 400);
    }

    const texto = typeof corpo.texto === "string" ? corpo.texto.trim() : "";
    if (!texto) return erro("Não veio nenhuma fala.", 400);
    if (texto.length > LIMITE_DA_FALA) return erro("Fala longa demais. Dite um item por vez.", 400);
    const tipo: TipoDocumento = corpo.tipo === "NOTA" ? "NOTA" : "ORCAMENTO";
    const veiculo = lerVeiculo(corpo.veiculo);

    const provedor = (Deno.env.get("IA_PROVEDOR") ?? "gemini").toLowerCase();
    try {
      const pedido = montarPedido(texto, tipo, veiculo);
      const bruto = provedor === "claude" ? await perguntarAoClaude(pedido) : await perguntarAoGemini(pedido);
      return Response.json(normalizar(bruto));
    } catch (e) {
      if (e instanceof ErroIa) return erro(e.message, e.status);
      console.error(`Falha ao consultar a IA (${provedor}):`, e);
      return erro("A IA não conseguiu entender agora. Tente de novo.", 502);
    }
  }),
};

function lerVeiculo(v: unknown): Veiculo | null {
  if (!v || typeof v !== "object") return null;
  const o = v as Record<string, unknown>;
  const s = (k: string) => (typeof o[k] === "string" ? (o[k] as string).slice(0, 100) : "");
  return { modelo: s("modelo"), ano: s("ano"), placa: s("placa"), cor: s("cor"), quilometragem: s("quilometragem") };
}

function erro(mensagem: string, status: number): Response {
  return Response.json({ erro: mensagem }, { status });
}
