package br.com.obdlight;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import br.com.obdlight.bluetooth.BluetoothManager;
import br.com.obdlight.elm.Elm327Manager;
import br.com.obdlight.model.Dtc;
import br.com.obdlight.obd.DtcParser;

/**
 * Activity principal do aplicativo OBD Light.
 *
 * <p>
 * Esta classe representa a camada de interface com o usuário.
 * Ela coordena o processo de conexão Bluetooth e a inicialização
 * do adaptador ELM327.
 * </p>
 *
 * <p>
 * A comunicação foi dividida em camadas para manter separadas
 * as responsabilidades da aplicação:
 * </p>
 *
 * <pre>
 * MainActivity
 *      |
 *      | controla interface
 *      v
 * Elm327Manager
 *      |
 *      | comandos AT
 *      v
 * BluetoothManager
 *      |
 *      | Bluetooth Classic / RFCOMM
 *      v
 * ELM327
 * </pre>
 *
 * <p>
 * Nesta etapa, a aplicação:
 * </p>
 *
 * <pre>
 * 1. verifica as permissões Bluetooth;
 * 2. apresenta os dispositivos previamente pareados;
 * 3. permite selecionar o adaptador;
 * 4. estabelece a conexão RFCOMM;
 * 5. envia o comando ATZ;
 * 6. apresenta a resposta recebida do ELM327.
 * </pre>
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Gerenciador responsável pela camada de transporte Bluetooth.
     */
    private BluetoothManager bluetoothManager;

    /**
     * Gerenciador responsável pela comunicação lógica
     * com o interpretador ELM327.
     */
    private Elm327Manager elm327Manager;

    /**
     * Campo responsável por apresentar o estado atual
     * da conexão com o adaptador.
     */
    private TextView txtStatus;

    /**
     * Campo utilizado para apresentar as respostas
     * recebidas do ELM327.
     */
    private TextView txtResposta;

    /**
     * Botão utilizado para iniciar a conexão Bluetooth.
     */
    private Button btnConectar;

    /**
     * Botão reservado para a futura leitura dos DTCs.
     */
    private Button btnLerFalhas;

    /**
     * Botão reservado para a futura limpeza dos DTCs.
     */
    private Button btnApagarFalhas;

    /**
     * Gerenciador moderno utilizado para solicitar a permissão
     * BLUETOOTH_CONNECT em tempo de execução.
     *
     * <p>
     * Essa permissão passou a ser necessária a partir
     * do Android 12 (API 31).
     * </p>
     */
    private final ActivityResultLauncher<String> bluetoothPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {

                        /*
                         * Caso o usuário permita o acesso ao Bluetooth,
                         * continuamos o fluxo de conexão.
                         */
                        if (isGranted) {

                            mostrarDispositivosPareados();

                        } else {

                            /*
                             * Sem a permissão não é possível acessar
                             * os dispositivos Bluetooth pareados.
                             */
                            atualizarStatus(
                                    "ELM327: Permissão Bluetooth negada"
                            );

                            Toast.makeText(
                                    this,
                                    "A permissão Bluetooth é necessária para conectar ao ELM327.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    /**
     * Método executado quando a Activity é criada.
     *
     * @param savedInstanceState estado anteriormente salvo.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Associa esta Activity ao layout XML principal.
         */
        setContentView(R.layout.activity_main);

        /*
         * Cria a camada responsável pela comunicação Bluetooth.
         */
        bluetoothManager = new BluetoothManager(this);

        /*
         * Cria a camada responsável pelos comandos do ELM327.
         *
         * O Elm327Manager utiliza internamente o BluetoothManager
         * para enviar e receber os dados.
         */
        elm327Manager = new Elm327Manager(bluetoothManager);

        /*
         * Obtém as referências dos componentes da interface.
         */
        txtStatus = findViewById(R.id.txtStatus);
        txtResposta = findViewById(R.id.txtResposta);

        btnConectar = findViewById(R.id.btnConectar);
        btnLerFalhas = findViewById(R.id.btnLerFalhas);
        btnApagarFalhas = findViewById(R.id.btnApagarFalhas);


        btnLerFalhas.setEnabled(false);
        btnApagarFalhas.setEnabled(false);

        /*
         * Define a ação do botão responsável pela conexão.
         */
        btnConectar.setOnClickListener(
                view -> iniciarConexaoBluetooth()
        );
        btnLerFalhas.setOnClickListener(
                view -> lerFalhas()
        );
        btnApagarFalhas.setOnClickListener(
                view -> confirmarLimpezaFalhas()
        );
    }

    /**
     * Inicia o fluxo necessário para acessar o adaptador Bluetooth.
     *
     * <p>
     * Antes de acessar dispositivos pareados, são verificadas
     * a disponibilidade do hardware e as permissões necessárias.
     * </p>
     */
    private void iniciarConexaoBluetooth() {

        /*
         * Verifica se o aparelho possui suporte físico
         * a Bluetooth.
         */
        if (!bluetoothManager.isBluetoothSupported()) {

            atualizarStatus("Bluetooth não suportado");

            Toast.makeText(
                    this,
                    "Este dispositivo não possui suporte a Bluetooth.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        /*
         * A partir do Android 12 é obrigatório verificar
         * BLUETOOTH_CONNECT em tempo de execução.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {

                /*
                 * Solicita a permissão ao usuário.
                 */
                bluetoothPermissionLauncher.launch(
                        Manifest.permission.BLUETOOTH_CONNECT
                );

                return;
            }
        }

        /*
         * Com as permissões disponíveis, podemos consultar
         * os dispositivos previamente pareados.
         */
        mostrarDispositivosPareados();
    }

    /**
     * Obtém os dispositivos Bluetooth previamente pareados
     * e apresenta uma janela para seleção.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void mostrarDispositivosPareados() {

        /*
         * Antes de consultar os dispositivos, verificamos
         * se o Bluetooth está ativado.
         */
        if (!bluetoothManager.isBluetoothEnabled()) {

            atualizarStatus("Bluetooth desativado");

            Toast.makeText(
                    this,
                    "Ative o Bluetooth antes de continuar.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {

            /*
             * Recupera todos os dispositivos previamente
             * pareados com o Android.
             */
            Set<BluetoothDevice> pairedDevices =
                    bluetoothManager.getPairedDevices();

            /*
             * Verifica se existe algum dispositivo disponível.
             */
            if (pairedDevices == null || pairedDevices.isEmpty()) {

                Toast.makeText(
                        this,
                        "Nenhum dispositivo Bluetooth pareado foi encontrado.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            /*
             * Converte o conjunto em uma lista para permitir
             * acesso através da posição selecionada.
             */
            List<BluetoothDevice> devices =
                    new ArrayList<>(pairedDevices);

            /*
             * Lista textual utilizada exclusivamente para
             * apresentar os dispositivos na interface.
             */
            List<String> deviceNames = new ArrayList<>();

            /*
             * Percorre os dispositivos encontrados.
             */
            for (BluetoothDevice device : devices) {

                String name = device.getName();

                /*
                 * Alguns dispositivos podem não fornecer
                 * um nome Bluetooth.
                 */
                if (name == null || name.trim().isEmpty()) {
                    name = "Dispositivo sem nome";
                }

                /*
                 * Adiciona nome e endereço MAC à representação
                 * apresentada ao usuário.
                 */
                deviceNames.add(
                        name + "\n" + device.getAddress()
                );
            }

            /*
             * Apresenta a janela contendo os dispositivos.
             */
            mostrarDialogoDispositivos(
                    devices,
                    deviceNames
            );

        } catch (SecurityException e) {

            atualizarStatus(
                    "Sem permissão Bluetooth"
            );

            Toast.makeText(
                    this,
                    "Não foi possível acessar os dispositivos Bluetooth.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Apresenta uma caixa de diálogo para seleção do
     * adaptador Bluetooth.
     *
     * @param devices     dispositivos Bluetooth encontrados.
     * @param deviceNames representação textual dos dispositivos.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void mostrarDialogoDispositivos(
            List<BluetoothDevice> devices,
            List<String> deviceNames
    ) {

        /*
         * Adapter utilizado para apresentar os dispositivos
         * na caixa de diálogo.
         */
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        deviceNames
                );

        /*
         * Constrói a janela de seleção.
         */
        new AlertDialog.Builder(this)
                .setTitle("Selecione o ELM327")
                .setAdapter(adapter, (dialog, position) -> {

                    /*
                     * Obtém o dispositivo correspondente
                     * à posição escolhida.
                     */
                    BluetoothDevice selectedDevice =
                            devices.get(position);

                    /*
                     * Inicia a conexão com o dispositivo.
                     */
                    conectarDispositivo(selectedDevice);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }


/**
 * Estabelece a conexão Bluetooth, valida o ELM327 através
 * do comando ATZ e executa sua sequência de inicialização.
 *
 * <p>
 * Todo o processo de comunicação é executado em uma thread
 * secundária, pois as operações de conexão, escrita e leitura
 * Bluetooth são bloqueantes e não devem ser executadas na
 * thread principal da interface gráfica.
 * </p>
 *
 * <p>
 * O fluxo executado por este método é:
 * </p>
 *
 * <pre>
 * Bluetooth RFCOMM
 *       ↓
 *      ATZ
 *       ↓
 *     ATE0
 *       ↓
 *     ATL0
 *       ↓
 *     ATS0
 *       ↓
 *     ATH0
 *       ↓
 *     ATSP0
 *       ↓
 *     ATDP
 * </pre>
 *
 * @param device dispositivo Bluetooth selecionado pelo usuário.
 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void conectarDispositivo(BluetoothDevice device) {

        /*
         * Desabilita temporariamente o botão de conexão.
         *
         * Isso impede que o usuário inicie duas tentativas
         * de conexão simultaneamente.
         */
        btnConectar.setEnabled(false);

        /*
         * As funções OBD-II permanecem desabilitadas enquanto
         * a conexão e a inicialização não forem concluídas.
         */
        btnLerFalhas.setEnabled(false);
        btnApagarFalhas.setEnabled(false);

        /*
         * Informa ao usuário que o processo de conexão começou.
         */
        atualizarStatus(
                "ELM327: Conectando..."
        );

        txtResposta.setText(
                "Resposta:\n\nAbrindo conexão Bluetooth..."
        );

        /*
         * Cria uma thread secundária.
         *
         * BluetoothSocket.connect() e as operações de leitura
         * são bloqueantes. Por esse motivo não podem ser
         * executadas diretamente na thread da interface.
         */
        new Thread(() -> {

            try {

                /*
                 * =====================================================
                 * ETAPA 1 - CONEXÃO BLUETOOTH
                 * =====================================================
                 *
                 * Solicita ao BluetoothManager que estabeleça
                 * uma conexão RFCOMM com o dispositivo escolhido.
                 */
                bluetoothManager.connect(device);

                /*
                 * Como componentes da interface somente podem ser
                 * modificados pela thread principal, utilizamos
                 * runOnUiThread().
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Bluetooth conectado"
                    );

                    txtResposta.setText(
                            "Resposta:\n\n" +
                                    "Bluetooth conectado.\n" +
                                    "Enviando ATZ..."
                    );
                });


                /*
                 * =====================================================
                 * ETAPA 2 - VALIDAÇÃO DO ELM327
                 * =====================================================
                 *
                 * Uma conexão Bluetooth bem-sucedida não garante
                 * que o dispositivo conectado seja um ELM327.
                 *
                 * Por isso enviamos ATZ e aguardamos uma resposta
                 * terminada pelo prompt ">".
                 */
                String respostaReset =
                        elm327Manager.reset();


                /*
                 * =====================================================
                 * ETAPA 3 - INICIALIZAÇÃO DO ELM327
                 * =====================================================
                 *
                 * Depois que ATZ confirma que existe comunicação
                 * com o interpretador, executamos a configuração
                 * utilizada pelo OBD Light.
                 *
                 * A sequência executada pelo método initialize() é:
                 *
                 * ATE0  -> desabilita echo
                 * ATL0  -> desabilita Line Feed
                 * ATS0  -> remove espaços
                 * ATH0  -> oculta headers
                 * ATSP0 -> seleção automática do protocolo
                 * ATDP  -> consulta o protocolo informado pelo ELM327
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Inicializando..."
                    );

                    txtResposta.setText(
                            "RESET:\n" +
                                    respostaReset +
                                    "\n\n" +
                                    "Inicializando ELM327..."
                    );
                });

                /*
                 * Executa efetivamente a sequência de inicialização.
                 *
                 * O objeto retornado guarda as respostas de cada
                 * comando enviado ao interpretador.
                 */
                Elm327Manager.InitializationResult initializationResult =
                        elm327Manager.initialize();


                /*
                 * =====================================================
                 * ETAPA 4 - INICIALIZAÇÃO CONCLUÍDA
                 * =====================================================
                 *
                 * Neste ponto:
                 *
                 * 1. Bluetooth está conectado;
                 * 2. ELM327 respondeu ao ATZ;
                 * 3. comandos de configuração foram enviados;
                 * 4. protocolo foi consultado através de ATDP.
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Inicializado"
                    );

                    /*
                     * Apresenta tanto a resposta do reset quanto
                     * o resultado dos comandos de inicialização.
                     */
                    txtResposta.setText(
                            "RESET:\n" +
                                    respostaReset +
                                    "\n\n" +
                                    "INICIALIZAÇÃO:\n" +
                                    initializationResult.toLogString()
                    );

                    /*
                     * Permite que o usuário realize uma nova
                     * conexão caso seja necessário.
                     */
                    btnConectar.setEnabled(true);

                    /*
                     * Nesta etapa ainda não habilitamos as funções
                     * de leitura e limpeza de DTC.
                     *
                     * Primeiro será realizada uma comunicação
                     * OBD-II real com a ECU do veículo.
                     */
                    btnLerFalhas.setEnabled(true);
                    btnApagarFalhas.setEnabled(true);
                });

            } catch (IOException e) {

                /*
                 * IOException poderá ocorrer durante:
                 *
                 * - abertura do BluetoothSocket;
                 * - envio de comandos;
                 * - recebimento das respostas.
                 *
                 * Em caso de falha, encerramos a conexão para
                 * liberar os recursos utilizados.
                 */
                bluetoothManager.disconnect();

                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Falha de comunicação"
                    );

                    btnConectar.setEnabled(true);

                    btnLerFalhas.setEnabled(false);
                    btnApagarFalhas.setEnabled(false);

                    txtResposta.setText(
                            "Resposta:\n\n" +
                                    "Falha durante a comunicação.\n\n" +
                                    "Detalhes:\n" +
                                    e.getMessage()
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Não foi possível comunicar com o ELM327.",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (SecurityException e) {

                /*
                 * SecurityException poderá ocorrer caso uma operação
                 * Bluetooth seja executada sem a permissão necessária.
                 */
                bluetoothManager.disconnect();

                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Permissão Bluetooth necessária"
                    );

                    btnConectar.setEnabled(true);

                    btnLerFalhas.setEnabled(false);
                    btnApagarFalhas.setEnabled(false);

                    txtResposta.setText(
                            "Resposta:\n\n" +
                                    "A permissão Bluetooth não está disponível."
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Permissão Bluetooth necessária.",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception e) {

                /*
                 * Captura adicional para erros inesperados.
                 *
                 * Isso evita que uma exceção não prevista encerre
                 * silenciosamente a thread responsável pela comunicação.
                 */
                bluetoothManager.disconnect();

                runOnUiThread(() -> {

                    atualizarStatus(
                            "ELM327: Erro inesperado"
                    );

                    btnConectar.setEnabled(true);

                    btnLerFalhas.setEnabled(false);
                    btnApagarFalhas.setEnabled(false);

                    txtResposta.setText(
                            "Resposta:\n\n" +
                                    "Erro inesperado:\n" +
                                    e.getMessage()
                    );
                });
            }

        }).start();
    }

    /**
     * Solicita à ECU os códigos de falha armazenados
     * através do serviço OBD-II Mode 03.
     *
     * <p>
     * A comunicação é executada em uma thread secundária,
     * pois a leitura Bluetooth é uma operação bloqueante.
     * </p>
     */
    private void lerFalhas() {

        /*
         * Verifica se existe comunicação com o ELM327.
         */
        if (!elm327Manager.isConnected()) {

            Toast.makeText(
                    this,
                    "ELM327 não está conectado.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        /*
         * Evita múltiplas solicitações simultâneas.
         */
        btnLerFalhas.setEnabled(false);

        atualizarStatus(
                "OBD-II: Lendo falhas..."
        );

        txtResposta.setText(
                "Enviando Mode 03..."
        );

        new Thread(() -> {

            try {

                /*
                 * =================================================
                 * REQUISIÇÃO OBD-II MODE 03
                 * =================================================
                 */
                String resposta =
                        elm327Manager.readStoredDtc();

                /*
                 * Analisa toda a resposta antes de decidir se existem
                 * falhas, ausência de falhas ou erro na consulta.
                 */
                DtcParser.ParseResult parseResult =
                        DtcParser.parse(resposta);

                List<Dtc> dtcs =
                        parseResult.getDtcs();

                /*
                 * Monta o texto que será apresentado.
                 */
                StringBuilder resultado =
                        new StringBuilder();

                resultado.append(
                        "RESPOSTA BRUTA:\n"
                );

                resultado.append(resposta);

                resultado.append(
                        "\n\nDTCs ENCONTRADOS:\n"
                );

                if (dtcs.isEmpty()) {

                    resultado.append(
                            "Nenhum DTC identificado."
                    );

                } else {

                    for (Dtc dtc : dtcs) {

                        resultado
                                .append("\n")
                                .append(dtc.getCode())
                                .append(" - ")
                                .append(dtc.getDescription());
                    }
                }

                /*
                 * Atualiza a interface na thread principal.
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "OBD-II: Leitura concluída"
                    );

                    txtResposta.setText(
                            resultado.toString()
                    );

                    btnLerFalhas.setEnabled(true);
                });

            } catch (IOException e) {

                runOnUiThread(() -> {

                    atualizarStatus(
                            "OBD-II: Falha na leitura"
                    );

                    txtResposta.setText(
                            "Falha ao solicitar DTCs.\n\n" +
                                    e.getMessage()
                    );

                    btnLerFalhas.setEnabled(true);
                });
            }

        }).start();
    }


    /**
     * Atualiza a informação de estado apresentada na interface.
     *
     * @param status texto que representa o novo estado.
     */
    private void atualizarStatus(String status) {
        txtStatus.setText(status);
    }

    /**
     * Solicita confirmação do usuário antes de executar
     * a limpeza dos códigos de falha.
     *
     * <p>
     * O Mode 04 é uma operação que modifica informações
     * armazenadas pelo sistema de diagnóstico do veículo.
     * Por esse motivo, a aplicação não deve executá-lo
     * diretamente após o pressionamento do botão.
     * </p>
     */
    private void confirmarLimpezaFalhas() {

        /*
         * Verifica inicialmente se existe conexão ativa
         * com o interpretador ELM327.
         */
        if (!elm327Manager.isConnected()) {

            Toast.makeText(
                    this,
                    "ELM327 não está conectado.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        /*
         * Apresenta uma caixa de diálogo para evitar que
         * a operação seja realizada acidentalmente.
         */
        new AlertDialog.Builder(this)
                .setTitle("Apagar códigos de falha")
                .setMessage(
                        "Deseja realmente apagar os códigos de falha " +
                                "armazenados no veículo?\n\n" +
                                "Esta operação enviará o comando OBD-II Mode 04."
                )

                /*
                 * Somente após confirmação explícita do usuário
                 * a operação é executada.
                 */
                .setPositiveButton(
                        "Apagar",
                        (dialog, which) -> apagarFalhas()
                )

                /*
                 * O botão cancelar fecha a janela sem executar
                 * qualquer comunicação com a ECU.
                 */
                .setNegativeButton(
                        "Cancelar",
                        null
                )

                .show();
    }

    /**
     * Executa a solicitação OBD-II Mode 04 para limpeza
     * dos códigos de falha armazenados.
     *
     * <p>
     * Este método somente deve ser chamado depois da
     * confirmação explícita do usuário.
     * </p>
     *
     * <p>
     * A comunicação é executada em uma thread secundária
     * porque as operações Bluetooth utilizadas pelo
     * ELM327 são bloqueantes.
     * </p>
     */
    private void apagarFalhas() {

        /*
         * Proteção adicional.
         *
         * Mesmo existindo a verificação antes da janela de
         * confirmação, verificamos novamente a conexão antes
         * de iniciar a operação.
         */
        if (!elm327Manager.isConnected()) {

            Toast.makeText(
                    this,
                    "ELM327 não está conectado.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        /*
         * Desabilita temporariamente os botões relacionados
         * ao diagnóstico.
         *
         * Isso evita que outro comando seja enviado enquanto
         * aguardamos a resposta do Mode 04.
         */
        btnLerFalhas.setEnabled(false);
        btnApagarFalhas.setEnabled(false);

        atualizarStatus(
                "OBD-II: Apagando falhas..."
        );

        txtResposta.setText(
                "Enviando Mode 04..."
        );

        /*
         * Cria uma thread dedicada à operação de comunicação.
         */
        new Thread(() -> {

            try {

                /*
                 * =====================================================
                 * OBD-II MODE 04
                 * =====================================================
                 *
                 * Solicita ao ELM327 que envie ao veículo
                 * a requisição para limpeza das informações
                 * de diagnóstico.
                 */
                String resposta =
                        elm327Manager.clearDtc();

                /*
                 * Preserva a resposta original para apresentação
                 * e cria uma segunda representação em maiúsculas
                 * para facilitar algumas verificações.
                 */
                String respostaUpper =
                        resposta.toUpperCase();

                /*
                 * Se o ELM327 não conseguir estabelecer comunicação
                 * com o veículo, isso não significa que os DTCs
                 * foram apagados.
                 */
                if (respostaUpper.contains("UNABLE TO CONNECT")) {

                    runOnUiThread(() -> {

                        atualizarStatus(
                                "OBD-II: Veículo não conectado"
                        );

                        txtResposta.setText(
                                "RESPOSTA BRUTA:\n" +
                                        resposta +
                                        "\n\n" +
                                        "Não foi possível executar a limpeza, " +
                                        "pois o ELM327 não conseguiu estabelecer " +
                                        "comunicação com o veículo."
                        );

                        btnLerFalhas.setEnabled(true);
                        btnApagarFalhas.setEnabled(true);
                    });

                    return;
                }

                /*
                 * NO DATA também não deve ser interpretado
                 * automaticamente como sucesso.
                 */
                if (respostaUpper.contains("NO DATA")) {

                    runOnUiThread(() -> {

                        atualizarStatus(
                                "OBD-II: Sem resposta da ECU"
                        );

                        txtResposta.setText(
                                "RESPOSTA BRUTA:\n" +
                                        resposta +
                                        "\n\n" +
                                        "A ECU não retornou dados para a solicitação."
                        );

                        btnLerFalhas.setEnabled(true);
                        btnApagarFalhas.setEnabled(true);
                    });

                    return;
                }

                /*
                 * Caso uma resposta tenha sido recebida,
                 * ela é apresentada integralmente.
                 *
                 * Nesta etapa não assumimos sucesso apenas
                 * pelo fato de existir uma resposta.
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "OBD-II: Comando de limpeza enviado"
                    );

                    txtResposta.setText(
                            "RESPOSTA MODE 04:\n" +
                                    resposta
                    );

                    btnLerFalhas.setEnabled(true);
                    btnApagarFalhas.setEnabled(true);
                });

            } catch (IOException e) {

                /*
                 * Trata falhas relacionadas à comunicação.
                 */
                runOnUiThread(() -> {

                    atualizarStatus(
                            "OBD-II: Falha ao apagar DTCs"
                    );

                    txtResposta.setText(
                            "Falha durante a operação.\n\n" +
                                    "Detalhes:\n" +
                                    e.getMessage()
                    );

                    btnLerFalhas.setEnabled(true);
                    btnApagarFalhas.setEnabled(true);
                });
            }

        }).start();
    }

    /**
     * Método chamado quando a Activity é destruída.
     *
     * <p>
     * A conexão Bluetooth é encerrada para liberar o socket,
     * InputStream e OutputStream utilizados pela aplicação.
     * </p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bluetoothManager != null) {
            bluetoothManager.disconnect();
        }
    }
}
