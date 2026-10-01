package br.com.obdlight.obd;

import java.util.ArrayList;
import java.util.List;

import br.com.obdlight.model.Dtc;

/**
 * Responsável pela interpretação das respostas relacionadas
 * à leitura de códigos de falha (DTCs) no aplicativo OBD Light.
 *
 * <p>
 * Esta implementação considera o formato observado durante
 * os ensaios realizados com o veículo utilizado no projeto.
 * </p>
 *
 * <p>
 * Durante os testes, foram observadas respostas no formato:
 * </p>
 *
 * <pre>
 * 43 XX [DTCs...]
 * </pre>
 *
 * <p>
 * Onde:
 * </p>
 *
 * <pre>
 * 43 = identificação da resposta de leitura de DTCs;
 *
 * XX = quantidade de códigos presentes naquela resposta;
 *
 * cada DTC = 2 bytes.
 * </pre>
 *
 * <p>
 * Exemplos observados:
 * </p>
 *
 * <pre>
 * 4300
 *
 * 43 = resposta identificada
 * 00 = nenhum DTC nessa resposta
 *
 *
 * 4303012101220222
 *
 * 43   = resposta identificada
 * 03   = três DTCs
 * 0121 = primeiro DTC
 * 0122 = segundo DTC
 * 0222 = terceiro DTC
 * </pre>
 *
 * <p>
 * É importante observar que o veículo poderá apresentar
 * mais de uma resposta. Portanto, encontrar "4300" não
 * encerra o processamento.
 * </p>
 *
 * <pre>
 * Exemplo:
 *
 * 4300
 * 4303012101220222
 *
 * A primeira resposta não contém DTCs, porém a segunda
 * contém três. Todas as respostas devem ser analisadas
 * antes da determinação do resultado final.
 * </pre>
 */
public final class DtcParser {

    /**
     * Construtor privado.
     *
     * A classe possui somente métodos estáticos e não
     * necessita ser instanciada.
     */
    private DtcParser() {
    }

    /**
     * Estados possíveis obtidos durante a análise da resposta.
     */
    public enum Status {

        /**
         * Pelo menos um DTC foi encontrado.
         */
        DTC_FOUND,

        /**
         * Foram encontradas respostas iniciadas por 43,
         * porém nenhuma delas informou DTCs.
         */
        NO_DTC,

        /**
         * Nenhuma resposta válida iniciada por 43
         * foi encontrada.
         */
        SEARCH_ERROR
    }

    /**
     * Resultado completo da interpretação.
     *
     * <p>
     * Além da lista de DTCs, armazenamos o estado da leitura.
     * Isso permite diferenciar:
     * </p>
     *
     * <pre>
     * veículo sem falhas
     *
     * de
     *
     * erro ao buscar falhas.
     * </pre>
     */
    public static class ParseResult {

        private final Status status;

        private final List<Dtc> dtcs;

        /**
         * Construtor do resultado.
         *
         * @param status estado final da análise.
         * @param dtcs   códigos encontrados.
         */
        public ParseResult(
                Status status,
                List<Dtc> dtcs
        ) {

            this.status = status;
            this.dtcs = dtcs;
        }

        /**
         * Retorna o estado da leitura.
         */
        public Status getStatus() {
            return status;
        }

        /**
         * Retorna os DTCs encontrados.
         */
        public List<Dtc> getDtcs() {
            return dtcs;
        }
    }

    /**
     * Analisa toda a resposta recebida durante a solicitação
     * de leitura de DTCs.
     *
     * <p>
     * O algoritmo não encerra ao encontrar "4300".
     * Todas as ocorrências de respostas iniciadas por 43
     * são analisadas.
     * </p>
     *
     * @param response resposta bruta recebida do ELM327.
     * @return resultado completo da interpretação.
     */
    public static ParseResult parse(String response) {

        List<Dtc> dtcs =
                new ArrayList<>();

        /*
         * Indica se pelo menos uma resposta iniciada
         * por 43 foi encontrada.
         */
        boolean found43Response = false;

        /*
         * Resposta inexistente não pode ser considerada
         * "veículo sem falhas".
         *
         * Nesse caso ocorreu uma falha na obtenção
         * das informações.
         */
        if (response == null ||
                response.trim().isEmpty()) {

            return new ParseResult(
                    Status.SEARCH_ERROR,
                    dtcs
            );
        }

        /*
         * =====================================================
         * NORMALIZAÇÃO
         * =====================================================
         *
         * Remove elementos de apresentação utilizados pelo
         * ELM327 sem destruir a sequência dos bytes.
         *
         * Exemplo recebido:
         *
         * 008
         * 0:430301210122
         * 1:02220000000000
         * 4300
         *
         * Neste estágio queremos preservar os bytes para
         * posteriormente localizar as respostas 43XX.
         */

        String normalized =
                response.toUpperCase();

        /*
         * Remove prefixos de linhas apresentados no formato:
         *
         * 0:
         * 1:
         * 2:
         *
         * O prefixo não pertence aos dados da resposta.
         */
        normalized = normalized.replaceAll(
                "(?m)^\\s*[0-9A-F]+:",
                ""
        );

        /*
         * Remove espaços e quebras de linha.
         *
         * Depois da remoção dos prefixos, os fragmentos
         * numerados voltam a formar uma sequência contínua.
         */
        normalized = normalized.replaceAll(
                "[^0-9A-F]",
                ""
        );

        /*
         * =====================================================
         * PROCURA DAS RESPOSTAS 43XX
         * =====================================================
         *
         * Não assumimos que a primeira ocorrência de 43 seja
         * a única resposta existente.
         */
        int position = 0;

        while (position + 4 <= normalized.length()) {

            /*
             * Procura a próxima ocorrência do byte 43.
             */
            int index43 =
                    normalized.indexOf(
                            "43",
                            position
                    );

            /*
             * Nenhuma outra resposta 43 foi encontrada.
             */
            if (index43 < 0) {
                break;
            }

            /*
             * Precisamos possuir pelo menos:
             *
             * 43 XX
             *
             * para interpretar a resposta.
             */
            if (index43 + 4 >
                    normalized.length()) {

                break;
            }

            int dtcCount;

            try {

                /*
                 * Obtém XX.
                 *
                 * Exemplo:
                 *
                 * 43 03
                 *
                 * XX = 03
                 */
                dtcCount =
                        Integer.parseInt(
                                normalized.substring(
                                        index43 + 2,
                                        index43 + 4
                                ),
                                16
                        );

            } catch (NumberFormatException e) {

                /*
                 * Se o byte seguinte não puder ser
                 * interpretado, avançamos a busca.
                 */
                position =
                        index43 + 2;

                continue;
            }

            /*
             * Agora sabemos que encontramos uma resposta
             * 43XX interpretável.
             */
            found43Response = true;

            /*
             * =================================================
             * RESPOSTA 4300
             * =================================================
             *
             * Zero DTCs nesta resposta.
             *
             * IMPORTANTE:
             *
             * Não encerramos o processamento porque outra
             * resposta poderá informar DTCs.
             */
            if (dtcCount == 0) {

                position =
                        index43 + 4;

                continue;
            }

            /*
             * =================================================
             * CÁLCULO DA QUANTIDADE DE DADOS
             * =================================================
             *
             * Cada DTC possui:
             *
             * 2 bytes
             *
             * Cada byte hexadecimal utiliza:
             *
             * 2 caracteres
             *
             * Portanto:
             *
             * 1 DTC = 4 caracteres hexadecimais.
             *
             *
             * Exemplos:
             *
             * XX = 01
             * precisamos de 4 caracteres:
             *
             * 0121
             *
             *
             * XX = 02
             * precisamos de 8 caracteres:
             *
             * 01210122
             *
             *
             * XX = 03
             * precisamos de 12 caracteres:
             *
             * 012101220222
             */

            int requiredCharacters =
                    dtcCount * 4;

            /*
             * Os dados começam imediatamente depois
             * dos bytes:
             *
             * 43 XX
             */
            int dataStart =
                    index43 + 4;

            int dataEnd =
                    dataStart +
                            requiredCharacters;

            /*
             * Se não existem bytes suficientes, a resposta
             * está incompleta.
             *
             * Não tentamos fabricar DTCs utilizando dados
             * inexistentes.
             */
            if (dataEnd >
                    normalized.length()) {

                /*
                 * Avança para procurar outra possível
                 * resposta 43.
                 */
                position =
                        index43 + 2;

                continue;
            }

            /*
             * =================================================
             * EXTRAÇÃO DOS DTCs
             * =================================================
             */

            for (int i = 0;
                 i < dtcCount;
                 i++) {

                /*
                 * Cada código ocupa quatro caracteres.
                 */
                int dtcStart =
                        dataStart +
                                (i * 4);

                int dtcEnd =
                        dtcStart + 4;

                String dtcHex =
                        normalized.substring(
                                dtcStart,
                                dtcEnd
                        );

                /*
                 * 0000 não representa um código válido.
                 */
                if ("0000".equals(dtcHex)) {
                    continue;
                }

                /*
                 * Converte os dois bytes para o formato
                 * textual do DTC.
                 */
                String code =
                        decodeDtc(dtcHex);

                if (code != null &&
                        !containsDtc(
                                dtcs,
                                code
                        )) {

                    dtcs.add(
                            new Dtc(
                                    code,
                                    "Descrição não disponível"
                            )
                    );
                }
            }

            /*
             * Continua a procura depois dos dados que
             * acabamos de processar.
             */
            position = dataEnd;
        }

        /*
         * =====================================================
         * DETERMINAÇÃO DO RESULTADO FINAL
         * =====================================================
         */

        /*
         * Encontramos pelo menos um DTC.
         */
        if (!dtcs.isEmpty()) {

            return new ParseResult(
                    Status.DTC_FOUND,
                    dtcs
            );
        }

        /*
         * Encontramos uma ou mais respostas 43XX,
         * porém todas indicaram zero DTCs.
         */
        if (found43Response) {

            return new ParseResult(
                    Status.NO_DTC,
                    dtcs
            );
        }

        /*
         * Nenhuma resposta 43XX foi localizada.
         *
         * Portanto, não podemos afirmar que o módulo
         * está sem falhas.
         */
        return new ParseResult(
                Status.SEARCH_ERROR,
                dtcs
        );
    }

    /**
     * Verifica se determinado DTC já está presente
     * na lista.
     *
     * <p>
     * Essa verificação evita duplicidade quando diferentes
     * respostas informarem o mesmo código.
     * </p>
     *
     * @param dtcs lista atual.
     * @param code código pesquisado.
     * @return true quando o código já estiver presente.
     */
    private static boolean containsDtc(
            List<Dtc> dtcs,
            String code
    ) {

        for (Dtc dtc : dtcs) {

            if (dtc.getCode()
                    .equalsIgnoreCase(code)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Converte dois bytes para a representação textual
     * utilizada pelos códigos DTC.
     *
     * @param hex dois bytes representados em hexadecimal.
     * @return código formatado.
     */
    private static String decodeDtc(
            String hex
    ) {

        try {

            int value =
                    Integer.parseInt(
                            hex,
                            16
                    );

            /*
             * Os dois bits mais significativos definem
             * a categoria do código.
             */
            int category =
                    (value >> 14) & 0x03;

            char prefix;

            switch (category) {

                case 0:
                    prefix = 'P';
                    break;

                case 1:
                    prefix = 'C';
                    break;

                case 2:
                    prefix = 'B';
                    break;

                case 3:
                    prefix = 'U';
                    break;

                default:
                    return null;
            }

            /*
             * Remove os bits utilizados pela categoria.
             */
            int numericPart =
                    value & 0x3FFF;

            /*
             * Constrói o código final.
             *
             * Exemplo:
             *
             * 0121 -> P0121
             */
            return String.format(
                    "%c%04X",
                    prefix,
                    numericPart
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }
}
