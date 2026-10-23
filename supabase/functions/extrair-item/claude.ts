// Provedor Claude (Anthropic). Para usar: secrets IA_PROVEDOR=claude e ANTHROPIC_API_KEY.
// CLAUDE_MODELO é opcional (padrão: Claude Haiku 5.5, o mais barato).
import Anthropic from "npm:@anthropic-ai/sdk@1.1.0";
import { ErroIa } from "./erros.ts";
import { ESQUEMA, PROMPT } from "./extracao.ts";

const MODELO_PADRAO = "claude-haiku-5-5";

export async function perguntarAoClaude(pedido: string): Promise<unknown> {
  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  if (!apiKey) throw new ErroIa("Falta a chave ANTHROPIC_API_KEY nos secrets do Supabase.", 500);

  const client = new Anthropic({ apiKey });
  try {
    const resposta = await client.messages.create({
      model: Deno.env.get("CLAUDE_MODELO") ?? MODELO_PADRAO,
      max_tokens: 4096,
      system: PROMPT,
      messages: [{ role: "user", content: pedido }],
      // effort baixo: extrair um item de uma frase é tarefa simples; a saída segue o ESQUEMA.
      output_config: { effort: "low", format: { type: "json_schema", schema: ESQUEMA } },
    });
    if (resposta.stop_reason === "refusal") {
      throw new ErroIa("A IA não aceitou esta fala. Tente dizer de outro jeito.", 422);
    }
    if (resposta.stop_reason === "max_tokens") {
      throw new ErroIa("A resposta da IA veio cortada. Dite um item por vez.", 502);
    }
    const bloco = resposta.content.find((b) => b.type === "text");
    return JSON.parse(bloco?.type === "text" ? bloco.text : "");
  } catch (e) {
    if (e instanceof Anthropic.RateLimitError) {
      throw new ErroIa("A IA chegou ao limite de uso. Espere um pouco e tente de novo.", 429);
    }
    throw e;
  }
}
