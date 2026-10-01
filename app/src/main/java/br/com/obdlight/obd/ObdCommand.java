package br.com.obdlight.obd;

/**
 * Centraliza os comandos utilizados pelo aplicativo OBD Light.
 *
 * <p>
 * A utilização desta classe evita que comandos AT e OBD-II sejam
 * escritos diretamente em diferentes partes do código-fonte.
 * Isso facilita a manutenção, documentação e evolução da aplicação.
 * </p>
 *
 * <p>
 * Os comandos foram divididos em duas categorias:
 * </p>
 *
 * <pre>
 * 1. Comandos AT:
 *    utilizados para configurar o interpretador ELM327.
 *
 * 2. Comandos OBD-II:
 *    utilizados para solicitar informações às ECUs do veículo.
 * </pre>
 */
public final class ObdCommand {

    /**
     * Construtor privado.
     *
     * <p>
     * Esta classe contém somente constantes e não deve
     * ser instanciada.
     * </p>
     */
    private ObdCommand() {
    }

    // ============================================================
    // COMANDOS DE CONFIGURAÇÃO DO ELM327
    // ============================================================

    /**
     * Reinicializa o interpretador ELM327.
     */
    public static final String RESET = "ATZ";

    /**
     * Desabilita o eco dos comandos enviados.
     *
     * <p>
     * Após sua execução, o ELM327 deixa de repetir na resposta
     * o comando que acabou de receber.
     * </p>
     */
    public static final String ECHO_OFF = "ATE0";

    /**
     * Desabilita caracteres Line Feed nas respostas.
     */
    public static final String LINEFEED_OFF = "ATL0";

    /**
     * Remove os espaços utilizados entre bytes nas respostas.
     */
    public static final String SPACES_OFF = "ATS0";

    /**
     * Desabilita a apresentação dos cabeçalhos das mensagens.
     */
    public static final String HEADERS_OFF = "ATH0";

    /**
     * Configura o ELM327 para seleção automática
     * do protocolo de comunicação com o veículo.
     */
    public static final String AUTO_PROTOCOL = "ATSP0";

    /**
     * Solicita ao ELM327 a descrição do protocolo
     * atualmente selecionado.
     */
    public static final String CURRENT_PROTOCOL = "ATDP";


    // ============================================================
    // COMANDOS OBD-II
    // ============================================================

    /**
     * Mode 03.
     *
     * Solicita os códigos de falha armazenados (Stored DTCs).
     */
    public static final String READ_STORED_DTC = "03";

    /**
     * Mode 04.
     *
     * Solicita a limpeza dos códigos de falha e informações
     * relacionadas armazenadas pelas ECUs.
     *
     * ATENÇÃO:
     * Este comando altera informações do sistema de diagnóstico
     * do veículo e deverá exigir confirmação do usuário.
     */
    public static final String CLEAR_DTC = "04";
}
