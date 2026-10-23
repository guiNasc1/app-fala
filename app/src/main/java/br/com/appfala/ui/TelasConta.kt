package br.com.appfala.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.appfala.R
import br.com.appfala.data.Oficina
import br.com.appfala.ui.tema.CoresCabecalho
import br.com.appfala.ui.tema.VermelhoEscuro
import br.com.appfala.util.Cnpj
import br.com.appfala.util.MascaraTransformation
import br.com.appfala.util.Mascaras
import br.com.appfala.util.soDigitos
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val mascaraCnpj = MascaraTransformation(Cnpj.MASCARA)
private val mascaraTelefone = MascaraTransformation(Mascaras::telefone)
private val mascaraCep = MascaraTransformation(Mascaras.CEP)

/**
 * `remember` guarda um valor enquanto o componente está na tela; `rememberSaveable` também
 * sobrevive a girar o celular. Para guardar um objeto nosso, ele precisa de um Saver que
 * o transforme em algo salvável; aqui, JSON.
 */
private val SaverOficina = Saver<Oficina, String>(
    save = { Json.encodeToString(Oficina.serializer(), it) },
    restore = { Json.decodeFromString(Oficina.serializer(), it) },
)

@Composable
fun TelaLogin(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf(vm.emailInicial) }
    var senha by rememberSaveable { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    // Escopo para disparar coroutines a partir de um clique (o onClick não é suspend).
    val scope = rememberCoroutineScope()

    Scaffold { espacos ->
        Column(
            Modifier
                .padding(espacos)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(VermelhoEscuro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_emitir), contentDescription = null, Modifier.size(52.dp), tint = Color.White)
            }
            Text("Fala Oficina", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Orçamentos e notas de serviço por voz", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))

            vm.aviso?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }

            Campo(
                email, { email = it.trim(); erro = null }, "E-mail",
                teclado = KeyboardType.Email, maiusculas = KeyboardCapitalization.None,
            )
            Campo(
                senha, { senha = it; erro = null }, "Senha",
                teclado = KeyboardType.Password, maiusculas = KeyboardCapitalization.None,
                mascara = PasswordVisualTransformation(),
            )
            CaixaMarcar("Lembrar de mim", vm.lembrarDeMim) { vm.lembrarDeMim = it }
            erro?.let { TextoErro(it) }

            BotaoGrande("Entrar", carregando = carregando, aoClicar = {
                carregando = true
                scope.launch {
                    erro = vm.entrar(email, senha)
                    carregando = false
                }
            })
            OutlinedButton(onClick = vm::irParaCadastro, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Criar uma conta", fontSize = 17.sp)
            }
        }
    }
}

/**
 * Cadastro (conta nova + oficina) ou edição dos dados da oficina (edicao = true).
 * A mesma tela serve aos dois casos, como um form JSF com rendered="#{!bean.edicao}" nos campos de senha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCadastro(vm: AppViewModel, edicao: Boolean) {
    var dados by rememberSaveable(stateSaver = SaverOficina) {
        mutableStateOf(if (edicao) vm.oficina ?: vm.oficinaDoCadastro ?: Oficina() else Oficina())
    }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var confirmacao by rememberSaveable { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var avisoCnpj by remember { mutableStateOf<String?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sem oficina ainda (cadastro incompleto) não há para onde voltar.
    val podeVoltar = !edicao || vm.oficina != null
    val voltar = { if (edicao) vm.fecharDadosOficina() else vm.voltarParaLogin() }
    // Botão "voltar" do Android (o gesto ou a seta do sistema).
    BackHandler(enabled = podeVoltar, onBack = voltar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (edicao) "Dados da oficina" else "Criar conta", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (podeVoltar) IconButton(onClick = voltar) {
                        Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Completando o cadastro não há Perfil nem voltar: esta é a saída (ex.: conta errada).
                    if (!podeVoltar) TextButton(onClick = vm::sair) { Text("Sair", color = CoresCabecalho.texto, fontSize = 17.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoresCabecalho.fundo,
                    titleContentColor = CoresCabecalho.texto,
                    navigationIconContentColor = CoresCabecalho.texto,
                ),
            )
        },
    ) { espacos ->
        Column(
            Modifier
                .padding(espacos)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.aviso?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }

            Secao("Você") {
                Campo(dados.nomeUsuario, { dados = dados.copy(nomeUsuario = it) }, "Seu nome", maiusculas = KeyboardCapitalization.Words)
                if (!edicao) {
                    Campo(
                        email, { email = it.trim() }, "E-mail (para entrar no app)",
                        teclado = KeyboardType.Email, maiusculas = KeyboardCapitalization.None,
                    )
                    Campo(
                        senha, { senha = it }, "Senha", dica = "Pelo menos 6 caracteres",
                        teclado = KeyboardType.Password, maiusculas = KeyboardCapitalization.None,
                        mascara = PasswordVisualTransformation(),
                    )
                    Campo(
                        confirmacao, { confirmacao = it }, "Repita a senha",
                        teclado = KeyboardType.Password, maiusculas = KeyboardCapitalization.None,
                        mascara = PasswordVisualTransformation(),
                    )
                }
            }

            Secao("Oficina") {
                Campo(
                    dados.cnpj, { dados = dados.copy(cnpj = Cnpj.limpar(it)); avisoCnpj = null }, "CNPJ",
                    teclado = KeyboardType.Ascii, maiusculas = KeyboardCapitalization.Characters, mascara = mascaraCnpj,
                )
                OutlinedButton(
                    onClick = {
                        buscando = true
                        avisoCnpj = null
                        scope.launch {
                            val r = vm.consultarCnpj(dados.cnpj)
                            buscando = false
                            if (r == null) {
                                avisoCnpj = "Não achei esse CNPJ na consulta pública. Preencha à mão."
                            } else {
                                // copy() troca só os campos da empresa e mantém o seu nome.
                                dados = r.copy(nomeUsuario = dados.nomeUsuario, userId = dados.userId)
                                avisoCnpj = "Dados preenchidos pela Receita. Confira antes de salvar."
                            }
                        }
                    },
                    // A consulta pública só conhece CNPJ numérico.
                    enabled = !buscando && Cnpj.valido(dados.cnpj) && Cnpj.somenteNumeros(dados.cnpj),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (buscando) "Buscando..." else "Buscar dados pelo CNPJ", fontSize = 16.sp) }
                avisoCnpj?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Campo(dados.razaoSocial, { dados = dados.copy(razaoSocial = it) }, "Razão social", maiusculas = KeyboardCapitalization.Characters)
                Campo(
                    dados.nomeFantasia, { dados = dados.copy(nomeFantasia = it) }, "Nome fantasia",
                    maiusculas = KeyboardCapitalization.Words, dica = "É o nome que aparece no topo do app e no PDF",
                )
            }

            Secao("Contato e endereço") {
                Campo(
                    dados.telefone, { dados = dados.copy(telefone = soDigitos(it, 11)) }, "Telefone / WhatsApp",
                    teclado = KeyboardType.Phone, mascara = mascaraTelefone,
                )
                Campo(
                    dados.email, { dados = dados.copy(email = it.trim()) }, "E-mail da oficina",
                    teclado = KeyboardType.Email, maiusculas = KeyboardCapitalization.None,
                )
                Campo(dados.endereco, { dados = dados.copy(endereco = it) }, "Endereço (rua e número)", maiusculas = KeyboardCapitalization.Words)
                Campo(dados.bairro, { dados = dados.copy(bairro = it) }, "Bairro", maiusculas = KeyboardCapitalization.Words)
                Linha {
                    Campo(
                        dados.cidade, { dados = dados.copy(cidade = it) }, "Cidade",
                        maiusculas = KeyboardCapitalization.Words, modifier = Modifier.weight(3f),
                    )
                    Campo(
                        dados.uf, { dados = dados.copy(uf = it.filter(Char::isLetter).take(2).uppercase()) }, "UF",
                        maiusculas = KeyboardCapitalization.Characters, modifier = Modifier.weight(1f),
                    )
                }
                Campo(
                    dados.cep, { dados = dados.copy(cep = soDigitos(it, 8)) }, "CEP",
                    teclado = KeyboardType.Number, mascara = mascaraCep,
                )
            }

            if (!edicao) CaixaMarcar("Lembrar de mim neste celular", vm.lembrarDeMim) { vm.lembrarDeMim = it }
            erro?.let { TextoErro(it) }
            BotaoGrande(if (edicao) "Salvar" else "Criar conta", carregando = salvando, aoClicar = {
                salvando = true
                erro = null
                scope.launch {
                    erro = if (edicao) vm.salvarOficina(dados) else vm.cadastrar(email, senha, confirmacao, dados)
                    salvando = false
                }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
