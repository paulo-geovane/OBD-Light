# OBD Light

<p align="center">
  <img src="app/src/main/res/drawable/logo.png" alt="OBD Light" height="120">
</p>

<p align="center">
  <strong>Aplicativo Android para diagnóstico automotivo OBD-II utilizando adaptador ELM327 via Bluetooth</strong>
</p>

<p align="center">
  Projeto desenvolvido como Trabalho de Conclusão de Curso (TCC) da Universidade Santo Amaro — UNISA.
</p>

---

## 📋 Sobre o projeto

O **OBD Light** é um aplicativo Android desenvolvido em **Java** para comunicação com veículos leves por meio de um adaptador compatível com **ELM327**, utilizando Bluetooth Classic.

O projeto tem como objetivo demonstrar, de forma prática, a comunicação entre um dispositivo Android e o sistema de diagnóstico embarcado de um veículo por meio do padrão OBD-II.

A aplicação foi estruturada de maneira modular, separando as responsabilidades de comunicação Bluetooth, gerenciamento do ELM327, comandos OBD-II, interpretação de respostas e representação dos códigos de falha.

---

## 🎓 Projeto acadêmico

Este projeto foi desenvolvido como parte do **Trabalho de Conclusão de Curso (TCC)** da **Universidade Santo Amaro (UNISA)**.

O desenvolvimento contempla tanto a implementação do software quanto sua validação prática em veículo real, utilizando um adaptador ELM327 conectado à interface OBD-II.

O código-fonte é disponibilizado neste repositório com finalidade acadêmica, documental e educacional.

---

## 🚗 Arquitetura de comunicação

O aplicativo não acessa diretamente a rede CAN do veículo.

A comunicação é realizada através do adaptador ELM327, responsável por intermediar os comandos enviados pelo Android e o protocolo de diagnóstico utilizado pelo veículo.

```text
┌─────────────────────┐
│      OBD Light      │
│      Android        │
└──────────┬──────────┘
           │
           │ Bluetooth Classic
           ▼
┌─────────────────────┐
│       ELM327        │
│    Interpretador    │
└──────────┬──────────┘
           │
           │ OBD-II
           │ ISO 15765-4 / CAN
           ▼
┌─────────────────────┐
│         ECU         │
│      do veículo     │
└─────────────────────┘
```

Dessa forma, o ELM327 abstrai a camada de comunicação com a rede veicular, enquanto o aplicativo é responsável pelo envio dos comandos e interpretação das respostas recebidas.

---

## ⚙️ Tecnologias utilizadas

- Java
- Android SDK
- Android Studio
- Bluetooth Classic
- ELM327
- OBD-II
- ISO 15765-4 / CAN
- Gradle
- Git e GitHub

O projeto possui **Minimum SDK API 26 (Android 8.0)**.

---

## 📱 Funcionalidades implementadas

Atualmente, o OBD Light possui suporte às seguintes funcionalidades:

- conexão com adaptador ELM327 previamente pareado;
- comunicação utilizando Bluetooth Classic;
- inicialização automática do ELM327;
- seleção automática do protocolo OBD-II;
- consulta do protocolo selecionado;
- leitura de códigos de falha;
- interpretação de DTCs;
- identificação de códigos `P`, `C`, `B` e `U`;
- tratamento de respostas sem DTCs;
- normalização de respostas fragmentadas do ELM327;
- prevenção de DTCs duplicados;
- comando para limpeza dos códigos de falha;
- apresentação da resposta bruta para fins de teste e validação.

---

## 🔧 Inicialização do ELM327

Após o estabelecimento da conexão Bluetooth, o aplicativo inicializa o adaptador utilizando comandos AT.

```text
ATZ
ATE0
ATL0
ATS0
ATH0
ATSP0
ATDP
```

### Comandos utilizados

| Comando | Função |
|---|---|
| `ATZ` | Reinicializa o ELM327 |
| `ATE0` | Desativa o eco dos comandos |
| `ATL0` | Desativa Line Feed |
| `ATS0` | Remove espaços da resposta |
| `ATH0` | Desativa a apresentação dos headers |
| `ATSP0` | Seleciona automaticamente o protocolo |
| `ATDP` | Consulta o protocolo atualmente selecionado |

A seleção automática por meio de `ATSP0` foi utilizada para permitir que o próprio adaptador identifique o protocolo compatível com o veículo conectado.

---

## 🔍 Leitura dos códigos de falha

A leitura dos DTCs é realizada utilizando o **OBD-II Mode 03**.

```text
03
```

O aplicativo analisa as respostas retornadas pelo adaptador e converte os dados recebidos para a representação convencional dos códigos de diagnóstico.

Durante os testes realizados no desenvolvimento, foram observadas respostas contendo o identificador `43`, seguido das informações utilizadas pela aplicação para determinar a quantidade e os códigos presentes na mensagem.

Exemplo observado durante o ensaio:

```text
4303012101220222
```

Interpretado pelo protótipo como:

```text
43   → resposta da leitura
03   → três códigos identificados

0121 → P0121
0122 → P0122
0222 → P0222
```

O software também trata respostas sem códigos de falha:

```text
4300
```

Nesse caso, a interface informa que não foram encontrados DTCs.

---

## 🧩 Tratamento das respostas do ELM327

Durante os ensaios foi observado que adaptadores compatíveis com ELM327 podem apresentar diferenças na formatação textual das respostas.

Uma resposta registrada durante o teste apresentou o seguinte formato:

```text
008
0:430301210122
1:02220000000000
4300
```

Por esse motivo, o OBD Light possui uma etapa de **normalização da resposta** antes da interpretação dos DTCs.

Essa abordagem permite separar a representação textual fornecida pelo adaptador da lógica responsável pela interpretação dos códigos de diagnóstico.

---

## 🧹 Limpeza dos códigos de falha

A solicitação de limpeza é realizada utilizando o **OBD-II Mode 04**:

```text
04
```

O aplicativo mantém a resposta bruta disponível na interface, permitindo observar as mensagens retornadas pelo veículo durante o procedimento.

A limpeza dos DTCs deve ser realizada somente após a correção ou remoção da condição responsável pela geração da falha.

---

## 🏗️ Estrutura do projeto

O código foi organizado em componentes com responsabilidades específicas:

```text
br.com.obdlight
│
├── MainActivity.java
│
├── bluetooth
│   └── BluetoothManager.java
│
├── elm
│   └── Elm327Manager.java
│
├── obd
│   ├── ObdCommand.java
│   └── DtcParser.java
│
├── model
│   └── Dtc.java
│
└── repository
    └── DtcRepository.java
```

### Responsabilidade das classes

| Classe | Responsabilidade |
|---|---|
| `MainActivity` | Interface gráfica, ações do usuário e apresentação dos resultados |
| `BluetoothManager` | Gerenciamento da conexão Bluetooth e do `BluetoothSocket` |
| `Elm327Manager` | Inicialização e comunicação com o adaptador ELM327 |
| `ObdCommand` | Centralização dos comandos AT e OBD-II utilizados pelo aplicativo |
| `DtcParser` | Normalização das respostas e interpretação dos DTCs |
| `Dtc` | Representação de um código de falha e sua descrição |
| `DtcRepository` | Responsável pelas informações associadas aos códigos de falha |

Essa organização evita concentrar toda a implementação na `MainActivity` e mantém separadas as diferentes responsabilidades do software.

---

## 🧪 Validação experimental

O aplicativo foi submetido a testes em um veículo real utilizando um adaptador ELM327 conectado à tomada OBD-II.

O procedimento experimental utilizado para validação consistiu nas seguintes etapas:

1. conexão do adaptador ELM327 ao veículo;
2. conexão do aplicativo ao adaptador por Bluetooth;
3. inicialização do ELM327;
4. leitura inicial dos DTCs;
5. geração controlada de uma condição de falha;
6. nova leitura dos códigos;
7. reconexão do componente;
8. execução do comando de limpeza.

---

## 🚘 Teste com o corpo de borboleta

Para validar experimentalmente a leitura dos DTCs, foi realizada a desconexão controlada do conector elétrico do **corpo de borboleta (TBI)**.

Após a desconexão, o sistema de gerenciamento do veículo reconheceu a condição e a luz indicadora de anomalia foi apresentada no painel.

Uma nova leitura realizada pelo OBD Light identificou:

```text
P0121
P0122
P0222
```

O ensaio permitiu validar a comunicação completa:

```text
Android
   ↓
Bluetooth
   ↓
ELM327
   ↓
OBD-II
   ↓
ECU
   ↓
Resposta
   ↓
DtcParser
   ↓
DTC apresentado ao usuário
```

---

## 📊 Resultado dos testes

Os testes realizados demonstraram o funcionamento das principais camadas implementadas no protótipo.

| Teste | Resultado |
|---|---|
| Conexão Bluetooth com ELM327 | Realizada |
| Resposta ao `ATZ` | Recebida |
| Inicialização do adaptador | Realizada |
| Comunicação OBD-II | Estabelecida |
| Leitura sem DTCs | Identificada |
| Geração controlada de falhas | Realizada |
| Leitura de múltiplos DTCs | Realizada |
| Interpretação de resposta fragmentada | Realizada |
| Identificação de P0121, P0122 e P0222 | Realizada |
| Envio do Mode 04 | Realizado |

Os resultados obtidos fornecem evidências experimentais do funcionamento da arquitetura proposta.

---

## 📄 Documentação

A documentação complementar dos testes está disponível no diretório:

```text
documentacao/
```

Arquivo:

```text
OBD_Light_Documentacao.pdf
```

O documento contém o procedimento experimental, resultados e registros fotográficos obtidos durante os testes realizados no veículo.

---

## 🔐 Permissões Android

Nas versões recentes do Android, o acesso aos dispositivos Bluetooth requer permissões específicas.

O projeto trata as permissões necessárias antes de executar operações que dependem do Bluetooth, evitando chamadas que possam resultar em `SecurityException`.

O aplicativo foi desenvolvido considerando o uso de um dispositivo ELM327 **previamente pareado** com o smartphone.

---

## ⚠️ Observações

Este projeto possui finalidade **acadêmica e educacional**.

Os resultados e formatos apresentados na documentação correspondem aos ensaios realizados durante o desenvolvimento do protótipo.

Adaptadores compatíveis com ELM327 podem apresentar diferenças de implementação e de formatação das respostas. Por esse motivo, outros adaptadores ou veículos podem exigir tratamentos adicionais.

A leitura ou limpeza de códigos de falha não substitui procedimentos técnicos adequados de diagnóstico automotivo.

---

## 📚 Trabalho de Conclusão de Curso

**Projeto:** OBD Light  
**Instituição:** Universidade Santo Amaro — UNISA  
**Modalidade:** Trabalho de Conclusão de Curso (TCC)  
**Plataforma:** Android  
**Linguagem:** Java  
**Área:** Diagnóstico automotivo / sistemas embarcados / comunicação OBD-II

---

## 📜 Licença

Este projeto é distribuído conforme os termos especificados no arquivo:

```text
LICENSE
```

Consulte a licença presente neste repositório antes de reutilizar ou redistribuir o código.

---

<p align="center">
  <strong>OBD Light</strong><br>
  Diagnóstico automotivo OBD-II utilizando Android e ELM327
</p>
