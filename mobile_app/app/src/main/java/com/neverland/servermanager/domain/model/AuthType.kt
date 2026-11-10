package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Tipo de Autenticação SSH (AuthType.kt)
 * ==============================================================================
 *
 * Define os métodos de autenticação aceitos para acesso ao host Oracle Linux remoto
 * onde o Bedrock Dedicated Server (BDS) Never Land está hospedado.
 *
 * Métodos Suportados:
 * - [PASSWORD]: Autenticação tradicional baseada em senha do sistema operacional.
 *   Utilizada para conexões simples, embora requeira cuidado no tráfego de senhas.
 * - [PRIVATE_KEY]: Autenticação criptográfica de alta segurança baseada em par de
 *   chaves assimétricas (RSA / Ed25519) em formato PEM ou OpenSSH v1.
 *   Permite também o uso de passphrase de proteção opcional.
 *
 * Este enum é desacoplado de qualquer biblioteca de terceiro, residindo na camada
 * de domínio pura para facilitar testes e clareza arquitetural.
 * ==============================================================================
 */
enum class AuthType(val displayName: String) {
    /**
     * Autenticação convencional baseada em senha de texto claro.
     */
    PASSWORD("Senha Textual"),

    /**
     * Autenticação avançada baseada em chave privada assimétrica (PEM / OpenSSH).
     * Recomendada pela Oracle Cloud Infrastructure (OCI) por máxima segurança.
     */
    PRIVATE_KEY("Chave Privada (RSA / Ed25519)");

    /**
     * Indica se o método exige chave criptográfica privada para autenticar.
     */
    val requiresPrivateKey: Boolean
        get() = this == PRIVATE_KEY

    /**
     * Indica se o método utiliza credencial de senha direta.
     */
    val requiresPassword: Boolean
        get() = this == PASSWORD
}
