/** Erro com mensagem pronta para o mecânico e o status HTTP que a função deve devolver. */
export class ErroIa extends Error {
  constructor(mensagem: string, readonly status: number) {
    super(mensagem);
  }
}
