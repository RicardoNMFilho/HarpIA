
# HarpIA: Benchmark de Modelos de IA em Android

HarpIA é um app Android para benchmark de modelos de inferência com múltiplos frameworks (TensorFlow Lite, PyTorch e MindSpore), medindo tempo, consumo de energia e dispositivo de execução (CPU/GPU).

## Recursos atuais
- Suporte a múltiplos backends de inferência:
   - TensorFlow Lite: CPU e GPU (delegate) com reuso de Interpreter/Delegate entre inferências.
   - PyTorch Android 2.1.0: CPU e GPU (Vulkan) com fallback automático para CPU se Vulkan falhar.
   - MindSpore Lite 2.6.0 (AAR local): CPU e tentativa de GPU com fallback automático para CPU; carregamento JNI garantido com System.loadLibrary.
- Cache e reuso dos runners por framework/dispositivo/modelo para evitar reconstrução de grafo e retransferência para GPU a cada chamada.
- Benchmark com imagens reais do ImageNet-V2 em assets.
- Medição de desempenho: tempo (ms) e energia (J) com amostragem periódica e estatísticas (média, desvio padrão, IC 95%).
- Seleção na UI do framework (TFLite/PyTorch/MindSpore) e do dispositivo (CPU/GPU).
- Tasks de VS Code para compilar, instalar, iniciar o app e seguir logs via ADB.

## Estado atual por backend
- TensorFlow Lite: OK em CPU e GPU (deps gpu-api + gpu adicionadas, regras ProGuard configuradas).
- PyTorch: OK em CPU e GPU (Vulkan). Se o dispositivo não suportar Vulkan, cai para CPU.
- MindSpore: Experimental. CPU OK; GPU depende do suporte do dispositivo (por exemplo, OpenCL) e do AAR fornecido. Falhas de GPU fazem fallback para CPU. Alguns modelos podem não carregar.

## Requisitos
- Android SDK e ADB; JDK 17; Kotlin 1.9.
- Dispositivo físico Android (armeabi-v7a ou arm64-v8a). Para PyTorch/Vulkan, exija suporte a Vulkan; para MindSpore/GPU, suporte a driver da GPU (pode variar por fabricante).
- VS Code (opcional) para usar as tasks integradas.

## Como usar
1) Clone o repositório e abra no VS Code.
2) Conecte um dispositivo com depuração USB.
3) Use as tasks em .vscode para ciclo básico:
    - Gradle: assembleDebug
    - ADB: installDebug
    - ADB: startApp
    - ADB: logcat (app)
4) No app:
    - Escolha Framework (TFLite, PyTorch, MindSpore) e Dispositivo (CPU/GPU).
    - Carregue um arquivo de modelo pelo botão “Carregar modelo”.
    - Execute uma inferência única ou “Benchmark” (usa assets/imagenetv2).

Opcional (PowerShell):
```powershell
adb logcat | findstr /i "com.example.harpia"
```

## Modelos e pré-processamento
- Entrada padrão esperada: imagem RGB 224x224 normalizada para [0,1] (ver `ImageUtils.kt`).
- TFLite: tensor NHWC [1,224,224,3].
- PyTorch: tensor NCHW [1,3,224,224].
- MindSpore: detecção automática do layout (NHWC ou NCHW) e transposição quando necessário.
Nota: modelos com dimensões/normalizações diferentes podem requerer ajustes de pré-processamento.

## Dependências e build
- Principais libs:
   - org.tensorflow:tensorflow-lite:2.14.0, tensorflow-lite-gpu-api:2.14.0, tensorflow-lite-gpu:2.14.0, tensorflow-lite-support:0.4.4.
   - org.pytorch:pytorch_android:2.1.0, pytorch_android_torchvision:2.1.0.
   - AAR local: app/libs/mindspore-lite-full-2.6.0.aar (inclui libs nativas). Embalado via flatDir.
- Gradle/Empacotamento:
   - NDK abiFilters: arm64-v8a e armeabi-v7a.
   - packagingOptions: doNotStrip para libs MindSpore.
   - Regras ProGuard para manter classes TFLite e GPU delegate.
   - Carregamento JNI do MindSpore: System.loadLibrary("jpeg", "turbojpeg", "minddata-lite", "mindspore-lite", "mindspore-lite-jni").

## Estrutura
- `app/` — Código-fonte Android (Kotlin)
- `app/src/main/assets/imagenetv2/` — Amostras de imagens para benchmark
- `.vscode/tasks.json` — Tasks de build/run/logcat no VS Code
- `.github/` — Instruções e automações

## Observações e limitações
- Energia: estimada via BatteryManager com amostragem periódica (100 ms). Valores são aproximados.
- MindSpore: o suporte a GPU depende do dispositivo e do AAR; falhas fazem fallback para CPU e uma mensagem de erro é exibida.
- MIUI/SELinux: avisos “miuilog” no logcat podem aparecer e não afetam a execução do app.
- Nem todos os modelos são compatíveis sem ajustes (shapes/layout, normalização, operadores).

## Changelog resumido
Consulte `CHANGELOG.md` para detalhes. Destaques recentes (11/08/2025):
- TFLite GPU corrigido (deps + ProGuard) e reuso de Interpreter/Delegate.
- PyTorch com seleção CPU/Vulkan e fallback automático para CPU.
- MindSpore com carregamento JNI explícito, seleção CPU/GPU e fallback.
- Cache de runners por framework/dispositivo/modelo para evitar reconstruções.
- Tasks de VS Code adicionadas; README atualizado.

---
Contribuições, sugestões e issues são bem-vindos!
