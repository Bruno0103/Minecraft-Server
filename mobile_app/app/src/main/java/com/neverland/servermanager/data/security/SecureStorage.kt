package com.neverland.servermanager.data.security

import com.neverland.servermanager.domain.model.SshConfig

/**
 * ==============================================================================
 * Never Land Server Manager - Contrato de Armazenamento Seguro (SecureStorage.kt)
 * ==============================================================================
 *
 * Define o contrato para persistência, recuperação e exclusão segura das credenciais
 * de autenticação e configurações de conexão SSH com o servidor Never Land.
 *
 * Benefícios da Abstração:
 * - Em ambiente de produção Android: utiliza [EncryptedSecureStorage], respaldado
 *   pelo hardware de segurança do dispositivo através do Android KeyStore e
 *   EncryptedSharedPreferences.
 * - Em testes unitários locais JUnit na JVM: utiliza [InMemorySecureStorage],
 *   permitindo execução ultrarrápida sem depender de stubs de KeyStore do Android.
 * ==============================================================================
 */
interface SecureStorage {

    /**
     * Salva as configurações de conexão e credenciais de autenticação SSH.
     * Deve garantir a cifragem robusta de campos sensíveis (senha, chave PEM e passphrase).
     *
     * @param config Instância com os parâmetros a serem persistidos.
     */
    fun saveSshConfig(config: SshConfig)

    /**
     * Carrega as configurações de conexão e credenciais salvas.
     * Caso não existam dados previamente armazenados, retorna uma instância padrão
     * com os valores canônicos do Never Land (host: "157.151.2.91", port: 22, username: "opc").
     *
     * @return Configuração carregada ou padrão canônico seguro.
     */
    fun loadSshConfig(): SshConfig

    /**
     * Remove com segurança todas as credenciais sensíveis (chave privada, passphrase e senha),
     * preservando os parâmetros de conexão não sensíveis (host, porta, usuário).
     */
    fun clearCredentials()

    /**
     * Verifica se existem credenciais SSH ativas e persistidas no armazenamento.
     *
     * @return true se houver chave privada PEM ou senha gravada; false caso contrário.
     */
    fun hasSavedCredentials(): Boolean
}
