package br.com.obdlight.elm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import br.com.obdlight.bluetooth.BluetoothManager;
import br.com.obdlight.obd.ObdCommand;

/**
 * Gerencia a comunicação lógica com o interpretador ELM327.
 *
 * <p>
 * Esta classe funciona como uma camada intermediária entre
 * a interface da aplicação e a comunicação Bluetooth.
 * </p>
 *
 * <pre>
 * MainActivity
 *      |
 *      v
 * Elm327Manager
 *      |
 *      v
 * BluetoothManager
 *      |
 *      v
 * ELM327
 *      |
 *      v
 * Veículo
 * </pre>
 *
 * <p>
 * BluetoothManager transporta bytes, enquanto Elm327Manager
 * conhece a estrutura textual utilizada pelo ELM327.
 * </p>
 */
public class Elm327Manager {

    /**
     * Prompt utilizado pelo ELM327 para informar que terminou
     * o processamento do comando e está pronto para receber outro.
     */
    private static final char PROMPT = '>';

    /**
     * Caractere CR utilizado para finalizar comandos enviados
     * ao ELM327.
     */
    private static final String COMMAND_TERMINATOR = "\r";

    /**
     * Tamanho do buffer utilizado durante as leituras.
     */
    private static final int READ_BUFFER_SIZE = 256;

    /**
     * Camada responsável pelo transporte Bluetooth.
     */
    private final BluetoothManager bluetoothManager;

    /**
     * Construtor do gerenciador ELM327.
     *
     * @param bluetoothManager camada Bluetooth previamente criada.
     */
    public Elm327Manager(BluetoothManager bluetoothManager) {

        if (bluetoothManager == null) {
            throw new IllegalArgumentException(
                    "BluetoothManager não pode ser nulo."
            );
        }

        this.bluetoothManager = bluetoothManager;
    }

    /**
     * Verifica se existe uma conexão Bluetooth ativa.
     *
     * @return true quando o socket Bluetooth estiver conectado.
     */
    public boolean isConnected() {
        return bluetoothManager.isConnected();
    }

    /**
     * Envia um comando ao ELM327 e aguarda sua resposta.
     *
     * <p>
     * O método adiciona automaticamente o caractere CR.
     * Portanto, os comandos armazenados em ObdCommand não
     * precisam possuir terminadores.
     * </p>
     *
     * @param command comando que será transmitido.
     * @return resposta normalizada do adaptador.
     * @throws IOException caso ocorra erro de comunicação.
     */
    public synchronized String sendCommand(String command)
            throws IOException {

        if (!bluetoothManager.isConnected()) {
            throw new IOException(
                    "Não existe conexão Bluetooth ativa."
            );
        }

        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O comando não pode estar vazio."
            );
        }

        /*
         * Prepara o comando adicionando o terminador CR.
         */
        String formattedCommand =
                command.trim() + COMMAND_TERMINATOR;

        /*
         * Converte o comando textual em bytes ASCII.
         */
        byte[] commandBytes =
                formattedCommand.getBytes(StandardCharsets.US_ASCII);

        /*
         * Transmite os bytes através da camada Bluetooth.
         */
        bluetoothManager.write(commandBytes);

        /*
         * Aguarda a resposta até o prompt ">".
         */
        String response = readUntilPrompt();

        /*
         * Retorna uma versão normalizada da resposta.
         */
        return normalizeResponse(response);
    }

    /**
     * Aguarda dados até encontrar o prompt do ELM327.
     *
     * @return resposta completa recebida.
     * @throws IOException caso ocorra erro de comunicação.
     */
    private String readUntilPrompt() throws IOException {

        StringBuilder response = new StringBuilder();

        byte[] buffer = new byte[READ_BUFFER_SIZE];

        while (true) {

            /*
             * Uma resposta poderá chegar fragmentada em vários
             * pacotes Bluetooth.
             */
            int bytesRead = bluetoothManager.read(buffer);

            /*
             * Valor negativo indica encerramento do stream.
             */
            if (bytesRead < 0) {
                throw new IOException(
                        "A conexão Bluetooth foi encerrada."
                );
            }

            /*
             * Converte somente os bytes efetivamente recebidos.
             */
            String chunk = new String(
                    buffer,
                    0,
                    bytesRead,
                    StandardCharsets.US_ASCII
            );

            response.append(chunk);

            /*
             * O prompt indica o final lógico da resposta.
             */
            if (response.indexOf(String.valueOf(PROMPT)) >= 0) {
                break;
            }
        }

        return response.toString();
    }

    /**
     * Normaliza a resposta do adaptador.
     *
     * @param response resposta original.
     * @return resposta sem prompt e com quebras de linha normalizadas.
     */
    private String normalizeResponse(String response) {

        if (response == null) {
            return "";
        }

        return response
                .replace(">", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    /**
     * Executa somente o reset do ELM327.
     *
     * @return identificação/resposta do adaptador.
     * @throws IOException caso ocorra erro.
     */
    public String reset() throws IOException {

        return sendCommand(
                ObdCommand.RESET
        );
    }

    /**
     * Executa toda a sequência de inicialização utilizada
     * pelo aplicativo OBD Light.
     *
     * <p>
     * A sequência executada é:
     * </p>
     *
     * <pre>
     * ATE0
     * ATL0
     * ATS0
     * ATH0
     * ATSP0
     * ATDP
     * </pre>
     *
     * <p>
     * O reset ATZ não é executado aqui porque é utilizado
     * separadamente para validar inicialmente o adaptador.
     * </p>
     *
     * @return objeto contendo as respostas da inicialização.
     * @throws IOException caso ocorra falha de comunicação.
     */
    public InitializationResult initialize()
            throws IOException {

        /*
         * Desabilita o eco.
         */
        String echoResponse =
                sendCommand(ObdCommand.ECHO_OFF);

        /*
         * Desabilita Line Feed.
         */
        String lineFeedResponse =
                sendCommand(ObdCommand.LINEFEED_OFF);

        /*
         * Remove espaços das respostas.
         */
        String spacesResponse =
                sendCommand(ObdCommand.SPACES_OFF);

        /*
         * Oculta cabeçalhos.
         */
        String headersResponse =
                sendCommand(ObdCommand.HEADERS_OFF);

        /*
         * Habilita a seleção automática do protocolo.
         */
        String protocolSelectionResponse =
                sendCommand(ObdCommand.AUTO_PROTOCOL);

        /*
         * Consulta o protocolo atualmente informado
         * pelo interpretador.
         */
        String protocol =
                sendCommand(ObdCommand.CURRENT_PROTOCOL);

        /*
         * Agrupa todas as respostas para que a interface
         * possa apresentá-las ou registrá-las.
         */
        return new InitializationResult(
                echoResponse,
                lineFeedResponse,
                spacesResponse,
                headersResponse,
                protocolSelectionResponse,
                protocol
        );
    }

    /**
     * Representa o resultado da sequência de inicialização
     * do adaptador ELM327.
     *
     * <p>
     * Manter essas informações em um objeto evita que a camada
     * de interface precise executar individualmente cada
     * comando de configuração.
     * </p>
     */
    public static class InitializationResult {

        private final String echoResponse;
        private final String lineFeedResponse;
        private final String spacesResponse;
        private final String headersResponse;
        private final String protocolSelectionResponse;
        private final String protocol;

        /**
         * Construtor do resultado da inicialização.
         */
        public InitializationResult(
                String echoResponse,
                String lineFeedResponse,
                String spacesResponse,
                String headersResponse,
                String protocolSelectionResponse,
                String protocol
        ) {

            this.echoResponse = echoResponse;
            this.lineFeedResponse = lineFeedResponse;
            this.spacesResponse = spacesResponse;
            this.headersResponse = headersResponse;
            this.protocolSelectionResponse =
                    protocolSelectionResponse;
            this.protocol = protocol;
        }

        public String getEchoResponse() {
            return echoResponse;
        }

        public String getLineFeedResponse() {
            return lineFeedResponse;
        }

        public String getSpacesResponse() {
            return spacesResponse;
        }

        public String getHeadersResponse() {
            return headersResponse;
        }

        public String getProtocolSelectionResponse() {
            return protocolSelectionResponse;
        }

        public String getProtocol() {
            return protocol;
        }

        /**
         * Gera uma representação textual completa do processo
         * de inicialização.
         *
         * @return log formatado dos comandos executados.
         */
        public String toLogString() {

            return "ATE0 → " + echoResponse + "\n" +
                    "ATL0 → " + lineFeedResponse + "\n" +
                    "ATS0 → " + spacesResponse + "\n" +
                    "ATH0 → " + headersResponse + "\n" +
                    "ATSP0 → " + protocolSelectionResponse + "\n" +
                    "ATDP → " + protocol;
        }
    }

    /**
     * Solicita à ECU os códigos de falha armazenados
     * através do serviço OBD-II Mode 03.
     *
     * <p>
     * Uma resposta positiva normalmente inicia com 0x43,
     * correspondente ao Mode 03 acrescido de 0x40.
     * </p>
     *
     * @return resposta bruta recebida através do ELM327.
     * @throws IOException caso ocorra falha de comunicação.
     */
    public String readStoredDtc() throws IOException {

        return sendCommand(
                ObdCommand.READ_STORED_DTC
        );
    }

    /**
     * Solicita a limpeza dos códigos de falha armazenados
     * através do serviço OBD-II Mode 04.
     *
     * <p>
     * Diferentemente do Mode 03, que realiza somente uma
     * consulta, o Mode 04 provoca uma alteração no estado
     * de diagnóstico do veículo.
     * </p>
     *
     * <p>
     * Por esse motivo, a confirmação do usuário deve ser
     * realizada pela camada de interface antes da chamada
     * deste método.
     * </p>
     *
     * @return resposta bruta recebida do ELM327.
     * @throws IOException caso ocorra falha de comunicação.
     */
    public String clearDtc() throws IOException {

        return sendCommand(
                ObdCommand.CLEAR_DTC
        );
    }
}
