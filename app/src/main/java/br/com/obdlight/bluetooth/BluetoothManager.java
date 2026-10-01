package br.com.obdlight.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Classe responsável pelo gerenciamento da comunicação Bluetooth Classic.
 *
 * <p>
 * No projeto OBD Light, esta classe representa a camada responsável
 * exclusivamente pela comunicação Bluetooth entre o dispositivo Android
 * e o adaptador ELM327.
 * </p>
 *
 * <p>
 * É importante observar que esta classe não possui conhecimento sobre
 * comandos AT, protocolo OBD-II, códigos DTC ou rede CAN. Sua função é
 * somente estabelecer e manter o canal de comunicação Bluetooth.
 * </p>
 *
 * <p>
 * Fluxo simplificado:
 * </p>
 *
 * <pre>
 * Android
 *    |
 *    | Bluetooth Classic
 *    v
 * ELM327
 * </pre>
 *
 * @author OBD Light
 */
public class BluetoothManager {

    /**
     * UUID padrão utilizado pelo perfil Serial Port Profile (SPP).
     *
     * Muitos adaptadores ELM327 Bluetooth Classic utilizam o protocolo
     * RFCOMM juntamente com esse UUID para simular uma comunicação serial.
     */
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * Contexto da aplicação.
     *
     * É utilizado principalmente para verificar permissões de Bluetooth.
     */
    private final Context context;

    /**
     * Adaptador Bluetooth do dispositivo Android.
     *
     * Através dele podemos verificar o estado do Bluetooth e acessar
     * dispositivos previamente pareados.
     */
    private final BluetoothAdapter bluetoothAdapter;

    /**
     * Socket RFCOMM utilizado para manter a conexão com o ELM327.
     */
    private BluetoothSocket bluetoothSocket;

    /**
     * Fluxo de entrada da conexão.
     *
     * Utilizado para receber dados enviados pelo ELM327.
     */
    private InputStream inputStream;

    /**
     * Fluxo de saída da conexão.
     *
     * Utilizado para enviar dados para o ELM327.
     */
    private OutputStream outputStream;

    /**
     * Construtor do gerenciador Bluetooth.
     *
     * @param context contexto da aplicação ou Activity.
     */
    public BluetoothManager(Context context) {

        /*
         * Utilizamos o contexto da aplicação para evitar manter
         * desnecessariamente uma referência direta à Activity.
         */
        this.context = context.getApplicationContext();

        /*
         * Obtém o adaptador Bluetooth padrão do dispositivo.
         *
         * Em aparelhos sem suporte a Bluetooth, o resultado poderá
         * ser null.
         */
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Verifica se o dispositivo Android possui suporte a Bluetooth.
     *
     * @return true caso exista um adaptador Bluetooth disponível.
     */
    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    /**
     * Verifica se o Bluetooth está atualmente ativado.
     *
     * @return true se o Bluetooth estiver disponível e ativado.
     */
    public boolean isBluetoothEnabled() {

        if (bluetoothAdapter == null) {
            return false;
        }

        /*
         * A partir do Android 12 (API 31), determinadas operações
         * Bluetooth exigem a permissão BLUETOOTH_CONNECT.
         */
        if (!hasBluetoothConnectPermission()) {
            return false;
        }

        return bluetoothAdapter.isEnabled();
    }

    /**
     * Verifica se a aplicação possui permissão para realizar
     * operações de conexão Bluetooth.
     *
     * <p>
     * A permissão BLUETOOTH_CONNECT foi introduzida no Android 12
     * (API 31). Nas versões anteriores essa verificação específica
     * não é necessária.
     * </p>
     *
     * @return true se a aplicação possuir a permissão necessária.
     */
    public boolean hasBluetoothConnectPermission() {

        /*
         * BLUETOOTH_CONNECT somente existe a partir da API 31.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            return ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED;
        }

        /*
         * Para Android 11 ou inferior, as permissões tradicionais
         * declaradas no Manifest são utilizadas.
         */
        return true;
    }

    /**
     * Retorna os dispositivos Bluetooth que já foram pareados
     * com o aparelho Android.
     *
     * <p>
     * Nesta primeira versão do OBD Light não realizaremos descoberta
     * de novos dispositivos. O ELM327 deverá ser pareado previamente
     * através das configurações do Android.
     * </p>
     *
     * @return conjunto contendo os dispositivos pareados.
     *
     * @throws SecurityException caso a permissão BLUETOOTH_CONNECT
     *                           não tenha sido concedida.
     */
    public Set<BluetoothDevice> getPairedDevices() {

        if (bluetoothAdapter == null) {
            throw new IllegalStateException(
                    "Este dispositivo não possui suporte a Bluetooth."
            );
        }

        if (!hasBluetoothConnectPermission()) {
            throw new SecurityException(
                    "Permissão BLUETOOTH_CONNECT não concedida."
            );
        }

        return bluetoothAdapter.getBondedDevices();
    }

    /**
     * Estabelece uma conexão Bluetooth RFCOMM com o dispositivo informado.
     *
     * <p>
     * O método utiliza o UUID padrão do Serial Port Profile (SPP),
     * normalmente utilizado por adaptadores ELM327 Bluetooth Classic.
     * </p>
     *
     * <p>
     * IMPORTANTE: este método realiza uma operação bloqueante e,
     * portanto, não deve ser chamado diretamente na thread principal
     * da interface gráfica.
     * </p>
     *
     * @param device dispositivo Bluetooth que representa o ELM327.
     *
     * @throws IOException caso não seja possível criar ou estabelecer
     *                     a conexão Bluetooth.
     *
     * @throws SecurityException caso a aplicação não possua a
     *                           permissão necessária.
     */
    public void connect(BluetoothDevice device) throws IOException {

        if (device == null) {
            throw new IllegalArgumentException(
                    "O dispositivo Bluetooth não pode ser nulo."
            );
        }

        if (bluetoothAdapter == null) {
            throw new IllegalStateException(
                    "Bluetooth não suportado neste dispositivo."
            );
        }

        if (!hasBluetoothConnectPermission()) {
            throw new SecurityException(
                    "Permissão BLUETOOTH_CONNECT não concedida."
            );
        }

        /*
         * Caso exista uma conexão anterior, ela é encerrada antes
         * de iniciarmos uma nova conexão.
         */
        disconnect();

        /*
         * Cria um socket RFCOMM utilizando o UUID padrão SPP.
         *
         * O RFCOMM fornece uma comunicação semelhante a uma porta
         * serial tradicional sobre Bluetooth.
         */
        bluetoothSocket =
                device.createRfcommSocketToServiceRecord(SPP_UUID);

        /*
         * Solicita a abertura efetiva da conexão.
         *
         * Esta chamada é bloqueante e poderá levar alguns segundos,
         * motivo pelo qual deverá ser executada em uma thread secundária.
         */
        bluetoothSocket.connect();

        /*
         * Após a conexão ser estabelecida, obtemos os fluxos de
         * entrada e saída associados ao socket.
         */
        inputStream = bluetoothSocket.getInputStream();
        outputStream = bluetoothSocket.getOutputStream();
    }

    /**
     * Envia dados através da conexão Bluetooth.
     *
     * <p>
     * Este método trabalha diretamente com bytes. Dessa forma,
     * BluetoothManager permanece independente do protocolo utilizado
     * pela camada superior.
     * </p>
     *
     * @param data bytes que serão enviados.
     *
     * @throws IOException caso ocorra uma falha durante o envio.
     */
    public void write(byte[] data) throws IOException {

        if (!isConnected()) {
            throw new IOException(
                    "Não existe uma conexão Bluetooth ativa."
            );
        }

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException(
                    "Os dados enviados não podem estar vazios."
            );
        }

        /*
         * Envia os bytes para o dispositivo conectado.
         */
        outputStream.write(data);

        /*
         * Força o envio de eventuais dados que ainda estejam
         * armazenados no buffer de saída.
         */
        outputStream.flush();
    }

    /**
     * Realiza a leitura de dados recebidos pela conexão Bluetooth.
     *
     * <p>
     * Assim como connect(), esta operação poderá bloquear enquanto
     * aguarda dados. Portanto, deverá ser utilizada fora da thread
     * principal da aplicação.
     * </p>
     *
     * @param buffer buffer onde os bytes recebidos serão armazenados.
     *
     * @return quantidade de bytes efetivamente recebidos.
     *
     * @throws IOException caso ocorra uma falha durante a leitura.
     */
    public int read(byte[] buffer) throws IOException {

        if (!isConnected()) {
            throw new IOException(
                    "Não existe uma conexão Bluetooth ativa."
            );
        }

        if (buffer == null || buffer.length == 0) {
            throw new IllegalArgumentException(
                    "O buffer de leitura não pode estar vazio."
            );
        }

        return inputStream.read(buffer);
    }

    /**
     * Verifica se existe atualmente uma conexão Bluetooth ativa.
     *
     * @return true caso o socket exista e esteja conectado.
     */
    public boolean isConnected() {

        return bluetoothSocket != null
                && bluetoothSocket.isConnected();
    }

    /**
     * Encerra a conexão Bluetooth e libera os recursos utilizados.
     *
     * <p>
     * O fechamento é realizado na ordem dos fluxos e posteriormente
     * do socket. Exceções durante o fechamento são ignoradas porque
     * o objetivo deste método é garantir a liberação dos recursos.
     * </p>
     */
    public void disconnect() {

        /*
         * Fecha o fluxo de entrada.
         */
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // O recurso continuará sendo liberado.
            }

            inputStream = null;
        }

        /*
         * Fecha o fluxo de saída.
         */
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
                // O recurso continuará sendo liberado.
            }

            outputStream = null;
        }

        /*
         * Por último, encerra o socket Bluetooth.
         */
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException ignored) {
                // Não há outra ação necessária neste ponto.
            }

            bluetoothSocket = null;
        }
    }
}
