package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Modelo de Telemetria de Status (ServerStatus.kt)
 * ==============================================================================
 *
 * Estrutura imutável de dados que encapsula a telemetria operacional completa do
 * servidor Never Land Bedrock Dedicated Server (BDS) na Oracle Cloud Infrastructure.
 *
 * Princípios de Engenharia de Software e Clean Architecture:
 * 1. Independência de Frameworks: Não referencia nenhuma biblioteca de UI (Android/Compose),
 *    nem bibliotecas de rede externas, garantindo isolamento total de domínio e
 *    testabilidade 100% pura na JVM.
 * 2. Propriedades Computadas Inteligentes: Expõe campos calculados ([health],
 *    [memoryUsageRatio], [memoryUsagePercent], [isMemoryAlert], [isMemoryCritical],
 *    [playersRatioText]) para evitar duplicação de regras de negócio em ViewModels
 *    ou componentes da camada de apresentação.
 * 3. Tolerância a Falhas e Nulos: Estados em que o processo está inativo ou finalizado
 *    não expõem PID, consumo de RAM ou Uptime, sendo tratados com total segurança via nullables.
 *
 * @property isActive Indica se a unidade systemd (bedrock.service) está com ActiveState == "active".
 * @property substate Subestado granular reportado pelo systemd (ex.: "running", "failed", "inactive", "auto-restart").
 * @property pid Identificador do processo no kernel Linux (PID do bedrock_server), ou null se inativo.
 * @property memoryUsedMib Consumo atual de memória RAM física do processo BDS em MiB (via cgroups v2).
 * @property memoryAlertMib Limite de alerta de consumo de memória em MiB (padrão: 600.0 MiB, cgroup MemoryHigh).
 * @property memoryMaxMib Limite máximo absoluto de consumo de memória em MiB (padrão: 750.0 MiB, cgroup MemoryMax).
 * @property uptimeText Data/hora de inicialização ou tempo de atividade legível (ex: "Mon 2026-11-09 22:44:39 GMT").
 * @property isFifoAvailable Indica se o named pipe (/run/bedrock.console) está operacional para escrita.
 * @property onlinePlayersCount Quantidade de jogadores atualmente conectados ao servidor.
 * @property maxPlayersCount Capacidade máxima de conexões simultâneas suportadas (padrão Never Land: 10).
 * @property cpuUsagePercent Percentual de uso global do processador da VM (0.0 a 100.0%), medido via `/proc/stat`.
 * @property bdsCpuUsagePercent Percentual de uso de CPU exclusivo do processo `bedrock_server`.
 * @property cpuLoadAverage Carga média do sistema (Load Average de 1m / 5m / 15m via `/proc/loadavg`).
 * @property cpuCores Quantidade de núcleos/threads lógicos disponíveis na VM (`nproc`).
 * @property cpuModel Nome do modelo do processador da instância OCI (`/proc/cpuinfo`).
 * @property timestamp Timestamp Unix em milissegundos do momento da coleta da telemetria.
 * ==============================================================================
 */
data class ServerStatus(
    val isActive: Boolean,
    val substate: String,
    val pid: Int?,
    val memoryUsedMib: Float?,
    val memoryAlertMib: Float = 600.0f,
    val memoryMaxMib: Float = 750.0f,
    val uptimeText: String?,
    val isFifoAvailable: Boolean,
    val onlinePlayersCount: Int = 0,
    val maxPlayersCount: Int = 10,
    val cpuUsagePercent: Float? = null,
    val bdsCpuUsagePercent: Float? = null,
    val cpuLoadAverage: String? = null,
    val cpuCores: Int? = null,
    val cpuModel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Avalia e determina o estado consolidado de integridade e saúde operacional do servidor.
     *
     * Regras de negócio de domínio:
     * - OFFLINE: Quando o serviço não está ativo (!isActive) ou em substate de término ("failed", "dead", "inactive").
     * - CRITICAL: Quando a RAM física ultrapassa o limite máximo do cgroup (> 750 MiB) ou reinício automático em loop.
     * - WARNING: Quando a RAM física ultrapassa a faixa de alerta (> 600 MiB) ou o FIFO está inoperante com o serviço ativo.
     * - HEALTHY: Operação nominal, serviço ativo em "running", RAM abaixo de 600 MiB e FIFO totalmente operacional.
     */
    val health: ServerHealth
        get() = when {
            !isActive || substate.lowercase() in listOf("failed", "dead", "inactive") -> ServerHealth.OFFLINE
            (memoryUsedMib != null && memoryUsedMib > memoryMaxMib) || substate.equals("auto-restart", ignoreCase = true) -> ServerHealth.CRITICAL
            (memoryUsedMib != null && memoryUsedMib > memoryAlertMib) || !isFifoAvailable -> ServerHealth.WARNING
            else -> ServerHealth.HEALTHY
        }

    /**
     * Razão matemática do consumo de memória RAM física em relação ao limite máximo (0.0f a 1.0f).
     * Retorna null caso a medição de memória não esteja disponível ou o limite seja inválido.
     */
    val memoryUsageRatio: Float?
        get() = if (memoryUsedMib != null && memoryMaxMib > 0.0f) {
            (memoryUsedMib / memoryMaxMib).coerceIn(0.0f, 1.0f)
        } else {
            null
        }

    /**
     * Percentual inteiro arredondado do consumo de memória RAM física (0 a 100%).
     * Retorna null caso a medição de memória não esteja disponível.
     */
    val memoryUsagePercent: Int?
        get() = memoryUsageRatio?.let { (it * 100).toInt() }

    /**
     * Indica se o consumo de memória RAM física ultrapassou o patamar de alerta seguro (600.0 MiB).
     */
    val isMemoryAlert: Boolean
        get() = memoryUsedMib != null && memoryUsedMib > memoryAlertMib

    /**
     * Indica se o consumo de memória RAM física ultrapassou o teto crítico máximo do cgroup (750.0 MiB).
     */
    val isMemoryCritical: Boolean
        get() = memoryUsedMib != null && memoryUsedMib > memoryMaxMib

    /**
     * Razão matemática do uso global do processador da máquina virtual (0.0f a 1.0f).
     * Retorna null caso a medição de CPU não esteja disponível.
     */
    val cpuUsageRatio: Float?
        get() = cpuUsagePercent?.let { (it / 100.0f).coerceIn(0.0f, 1.0f) }

    /**
     * Indica se o uso do processador atingiu a faixa de atenção/alerta (>= 70%).
     */
    val isCpuAlert: Boolean
        get() = cpuUsagePercent != null && cpuUsagePercent >= 70.0f

    /**
     * Indica se o uso do processador atingiu a faixa de saturação crítica (>= 90%).
     */
    val isCpuCritical: Boolean
        get() = cpuUsagePercent != null && cpuUsagePercent >= 90.0f

    /**
     * Formatação descritiva da lotação de jogadores para exibição amigável na UI (ex.: "0/10", "2/10").
     */
    val playersRatioText: String
        get() = "$onlinePlayersCount/$maxPlayersCount"

    /**
     * Indica se há ao menos um jogador conectado e autenticado no servidor.
     */
    val hasPlayersOnline: Boolean
        get() = onlinePlayersCount > 0

    companion object {
        /**
         * Cria uma instância determinística e segura representando o servidor em estado desligado / inativo.
         * Útil para inicialização de estados de UI e situações em que o serviço se encontra inativo na VM.
         */
        fun offline(): ServerStatus = ServerStatus(
            isActive = false,
            substate = "inactive",
            pid = null,
            memoryUsedMib = null,
            memoryAlertMib = 600.0f,
            memoryMaxMib = 750.0f,
            uptimeText = null,
            isFifoAvailable = false,
            onlinePlayersCount = 0,
            maxPlayersCount = 10,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * ==============================================================================
 * Never Land Server Manager - Classificação de Saúde do Servidor (ServerHealth)
 * ==============================================================================
 *
 * Enumeração semântica que categoriza a saúde geral do processo Bedrock Dedicated Server.
 *
 * @property label Rótulo legível em Português do Brasil para exibição em badges e cabeçalhos.
 * @property description Descrição detalhada do estado operacional para modais e tooltips.
 * @property colorHex Cor hexadecimal associada ao estado na paleta Never Land.
 */
enum class ServerHealth(
    val label: String,
    val description: String,
    val colorHex: Long
) {
    /**
     * Operação nominal: serviço ativo em "running", canal FIFO pronto e consumo de RAM controlado (< 600 MiB).
     */
    HEALTHY(
        label = "Saudável",
        description = "Serviço operacional, canal de console pronto e consumo de memória dentro do limite nominal.",
        colorHex = 0xFF4CAF50 // Verde Never Land (Grass Green)
    ),

    /**
     * Alerta operacional: pressão de memória acima de 600 MiB (início de swap) ou canal FIFO temporariamente sob contenção.
     */
    WARNING(
        label = "Atenção",
        description = "Consumo de memória RAM elevado (> 600 MiB) ou canal de console temporariamente sob contenção.",
        colorHex = 0xFFFFC107 // Âmbar Redstone Lamp
    ),

    /**
     * Estado crítico: memória acima do teto de 750 MiB (risco iminente de OOM killer) ou reinício em loop.
     */
    CRITICAL(
        label = "Crítico",
        description = "Risco iminente de colapso, estouro de memória física (> 750 MiB) ou reinicialização em loop.",
        colorHex = 0xFFF44336 // Vermelho Redstone Error
    ),

    /**
     * Servidor desligado: processo parado, inativo ou finalizado de forma limpa.
     */
    OFFLINE(
        label = "Desligado",
        description = "Serviço parado ou inativo. Nenhum processo BDS em execução na máquina virtual.",
        colorHex = 0xFF9E9E9E // Cinza Bedrock Neutro
    );

    /**
     * Informa se o servidor está considerado online sob a perspectiva de rede.
     */
    val isOnline: Boolean
        get() = this == HEALTHY || this == WARNING || this == CRITICAL
}

/**
 * ==============================================================================
 * Never Land Server Manager - Ações de Ciclo de Vida do Servidor (LifecycleAction)
 * ==============================================================================
 *
 * Modela tipadamente as instruções de controle operacional do serviço Bedrock
 * Dedicated Server executadas na máquina virtual Oracle Linux.
 *
 * @property displayName Título em Português do Brasil para exibição em botões e diálogos modais.
 * @property canonicalCommand Comando oficial executado através do utilitário administrativo mc.
 * @property fallbackCommand Comando alternativo direto no systemd com privilégios de sudo.
 * @property timeoutSeconds Tempo limite máximo de espera em segundos para o retorno do comando SSH.
 * @property requiresConfirmation Se verdadeiro, exige a apresentação de um AlertDialog de confirmação antes do despacho.
 * @property confirmationMessage Mensagem explicativa apresentada ao operador no diálogo de confirmação.
 */
enum class LifecycleAction(
    val displayName: String,
    val canonicalCommand: String,
    val fallbackCommand: String,
    val timeoutSeconds: Int,
    val requiresConfirmation: Boolean,
    val confirmationMessage: String?
) {
    /**
     * Inicializa o serviço bedrock.service e aguarda a subida do motor BDS.
     */
    START(
        displayName = "Iniciar Servidor",
        canonicalCommand = "mc start",
        fallbackCommand = "sudo systemctl start bedrock.service",
        timeoutSeconds = 30,
        requiresConfirmation = false,
        confirmationMessage = null
    ),

    /**
     * Desliga o servidor com salvamento ordenado do banco de dados LevelDB via script bedrock_stop.sh.
     * IMPORTANTE: Utiliza obrigatoriamente a flag '-f' para contornar solicitações interativas no terminal.
     */
    STOP(
        displayName = "Parar Servidor",
        canonicalCommand = "mc stop -f",
        fallbackCommand = "sudo systemctl stop bedrock.service",
        timeoutSeconds = 65,
        requiresConfirmation = true,
        confirmationMessage = "Deseja realmente parar o servidor? O mundo Never Land será salvo com segurança antes da interrupção do processo."
    ),

    /**
     * Reinicia a unidade bedrock.service. Utiliza a flag '--now' para execução imediata sem o contador
     * de 10 segundos no terminal interativo, prevenindo timeouts de canal de rede SSH.
     */
    RESTART(
        displayName = "Reiniciar Servidor",
        canonicalCommand = "mc restart --now",
        fallbackCommand = "sudo systemctl restart bedrock.service",
        timeoutSeconds = 75,
        requiresConfirmation = true,
        confirmationMessage = "Deseja realmente reiniciar o servidor? Jogadores conectados serão desconectados momentaneamente."
    );
}
