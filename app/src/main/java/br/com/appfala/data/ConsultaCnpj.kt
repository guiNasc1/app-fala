package br.com.appfala.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Consulta pública de CNPJ (BrasilAPI) para preencher o cadastro. Veio do projeto NotaServico.
 * Faz I/O bloqueante: chame fora da thread principal (o ViewModel usa Dispatchers.IO).
 * A BrasilAPI só conhece CNPJ numérico; o alfanumérico novo é preenchido à mão.
 */
object ConsultaCnpj {
    fun buscar(cnpj: String): Oficina? {
        val con = URL("https://brasilapi.com.br/api/cnpj/v1/$cnpj").openConnection() as HttpURLConnection
        return try {
            con.connectTimeout = 8000
            con.readTimeout = 8000
            con.setRequestProperty("Accept", "application/json")
            if (con.responseCode != 200) return null
            val j = JSONObject(con.inputStream.bufferedReader().readText())
            fun s(k: String) = j.optString(k).takeUnless { it == "null" }.orEmpty().trim()
            val rua = listOf(s("descricao_tipo_de_logradouro"), s("logradouro")).filter { it.isNotEmpty() }.joinToString(" ")
            // Razão social de MEI vem como "19.806.803 FULANO DE TAL": tira o prefixo do CNPJ.
            val razao = s("razao_social").replace(Regex("^\\d{2}\\.\\d{3}\\.\\d{3}\\s+"), "")
            Oficina(
                cnpj = cnpj,
                razaoSocial = razao,
                nomeFantasia = s("nome_fantasia"),
                telefone = s("ddd_telefone_1").filter { it.isDigit() },
                email = s("email").lowercase(),
                endereco = listOf(rua, s("numero"), s("complemento")).filter { it.isNotEmpty() }.joinToString(", "),
                bairro = s("bairro"),
                cidade = s("municipio"),
                uf = s("uf"),
                cep = s("cep").filter { it.isDigit() },
            )
        } finally {
            con.disconnect()
        }
    }
}
