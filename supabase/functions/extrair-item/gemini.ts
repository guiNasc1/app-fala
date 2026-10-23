// Provedor Gemini (Google). Usado nos testes, no plano gratuito.
// Secrets: GEMINI_API_KEY (obrigatório) e GEMINI_MODELO (opcional).
import { ApiError, GoogleGenAI } from "npm:@google/genai@2.30.0";
import { ErroIa } from "./erros.ts";
import { ESQUEMA, PROMPT } from "./extracao.ts";

const MODELO_PADRAO = "gemini-3.5-flash-lite";

export async function perguntarAoGemini(pedido: string): Promise<unknown> {
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) throw new ErroIa("Falta a chave GEMINI_API_KEY nos secrets do Supabase.", 500);

  const ai = new GoogleGenAI({ apiKey });
  try {
    const resposta = await ai.models.generateContent({
      model: Deno.env.get("GEMINI_MODELO") ?? MODELO_PADRAO,
      contents: pedido,
      config: {
        systemInstruction: PROMPT,
        responseMimeType: "application/json",
        responseJsonSchema: ESQUEMA,
      },
    });
    return JSON.parse(resposta.text ?? "");
  } catch (e) {
    if (e instanceof ApiError && e.status === 429) {
      throw new ErroIa("A IA gratuita chegou ao limite de uso. Espere um pouco e tente de novo.", 429);
    }
    throw e;
  }
}
