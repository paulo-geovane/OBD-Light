package br.com.obdlight.model;

/**
 * Representa um Código de Falha de Diagnóstico
 * (Diagnostic Trouble Code - DTC) identificado
 * através do sistema OBD-II.
 *
 * <p>
 * Esta classe pertence à camada de modelo da aplicação
 * OBD Light e possui a responsabilidade exclusiva de
 * representar os dados associados a uma falha.
 * </p>
 *
 * <p>
 * Um código DTC OBD-II normalmente possui cinco caracteres.
 * O primeiro caractere identifica a categoria do sistema:
 * </p>
 *
 * <pre>
 * P - Powertrain (motor e transmissão)
 * C - Chassis
 * B - Body (carroceria)
 * U - Network (comunicação entre módulos)
 *
 * Exemplos:
 *
 * P0300 - Falha de combustão aleatória/múltipla
 * P0133 - Resposta lenta do sensor de oxigênio
 * U0100 - Perda de comunicação com determinado módulo
 * </pre>
 *
 * <p>
 * Nesta etapa do desenvolvimento, cada objeto armazena
 * apenas o código da falha e sua descrição textual.
 * A estrutura poderá posteriormente ser ampliada para
 * armazenar informações adicionais.
 * </p>
 */
public class Dtc {

    /**
     * Código padronizado da falha.
     *
     * <p>
     * Exemplos:
     * P0300, P0133, U0100.
     * </p>
     */
    private final String code;

    /**
     * Descrição textual associada ao código de falha.
     *
     * <p>
     * A descrição poderá futuramente ser obtida através
     * de um repositório de códigos DTC.
     * </p>
     */
    private final String description;

    /**
     * Construtor utilizado para criar uma representação
     * de um código de falha.
     *
     * @param code        código DTC no formato OBD-II.
     * @param description descrição textual da falha.
     */
    public Dtc(String code, String description) {

        this.code = code;
        this.description = description;
    }

    /**
     * Retorna o código da falha.
     *
     * @return código DTC.
     */
    public String getCode() {
        return code;
    }

    /**
     * Retorna a descrição associada à falha.
     *
     * @return descrição textual do DTC.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Retorna uma representação textual do objeto.
     *
     * <p>
     * Este método facilita operações de depuração,
     * registro de logs e apresentação simplificada
     * das informações.
     * </p>
     *
     * @return representação textual do DTC.
     */
    @Override
    public String toString() {

        return code + " - " + description;
    }
}