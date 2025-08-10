
# HarpIA: Benchmark de Modelos de IA em Android

HarpIA é uma ferramenta Android para benchmark de modelos de inferência de IA, com suporte a múltiplos frameworks (TensorFlow Lite, PyTorch, MindSpore) e medição de tempo, energia e dispositivo de execução (CPU/GPU).

## Funcionalidades
- Execução de inferência com modelos TFLite, PyTorch e MindSpore (experimental)
- Benchmark automatizado usando imagens do ImageNet-V2 (assets)
- Medição de tempo de inferência e consumo energético (Joules)
- Seleção de dispositivo (CPU/GPU) e framework
- Interface moderna com CardView e informações separadas (resultado, tempo, energia, dispositivo, modelo)
- Tasks para build, instalação e logcat integradas ao VS Code

## Estado Atual
- **TensorFlow Lite**: Totalmente funcional (CPU/GPU)
- **PyTorch**: Funcional (CPU)
- **MindSpore**: Integração preliminar, pode apresentar falhas ao carregar modelos (logs detalhados em desenvolvimento)
- **Energia**: Medida em Joules para inferência única e benchmark
- **Benchmark**: Usa imagens reais do ImageNet-V2 presentes em `assets/imagenetv2/`
- **UI**: Layout moderno, informações separadas em cartões

## Como usar
1. Clone o repositório:
   ```sh
   git clone https://github.com/RicardoNMFilho/HarpIA.git
   ```
2. Abra no VS Code.
3. Conecte um dispositivo Android com depuração USB ativada.
4. Use as tasks do VS Code para build, instalar e visualizar logs (`assembleDebug`, `installDebug`, `logcat`).
5. Selecione o modelo e framework na interface do app e execute o benchmark ou inferência.

## Requisitos
- Android SDK
- Java 17+
- Kotlin 1.9+
- Gradle 8.2+
- VS Code (com extensões recomendadas para Android/Kotlin)
- Dispositivo Android físico

## Estrutura
- `app/` - Código fonte Android
- `assets/imagenetv2/` - Imagens para benchmark
- `.vscode/` - Tasks para automação no VS Code
- `.github/` - Instruções e automações

## Observações
- MindSpore pode exigir bibliotecas nativas específicas no APK/dispositivo.
- Logs detalhados são exibidos via logcat para facilitar depuração.
- O projeto é compatível com Android Studio, mas otimizado para uso no VS Code.

---
Contribuições, sugestões e issues são bem-vindos!
