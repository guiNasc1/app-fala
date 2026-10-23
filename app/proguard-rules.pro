# Regras do R8 (o "minificador" da versão release).
# As bibliotecas (kotlinx.serialization, Ktor, OkHttp, supabase-kt) já trazem as regras delas;
# aqui fica só o que este app precisa a mais.

# Mantém nome de arquivo e número de linha nos erros, para dar para achar onde o app quebrou.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
