# Template Android VS Code

Projeto Android mínimo em Kotlin, pronto para build, execução e debug no VS Code.

## Recursos
- Estrutura mínima Android (Kotlin, Gradle, AndroidX)
- Compatível com build, run e debug via VS Code
- Tasks para build, instalação e logcat
- Pronto para execução via ADB

## Como usar
1. Clone o repositório:
   ```sh
   git clone https://github.com/RicardoNMFilho/Template-Android-VsCode.git
   ```
2. Abra no VS Code.
3. Execute as tasks disponíveis (build, install, run, logcat) pelo menu de tarefas do VS Code.
4. Conecte um dispositivo Android via USB e ative a depuração USB.

## Requisitos
- Android SDK
- Java 17+
- Kotlin 1.9+
- Gradle 8.2+
- VS Code (com extensões recomendadas para Android/Kotlin)

## Estrutura
- `app/` - Código fonte Android
- `.vscode/` - Tasks para automação no VS Code
- `.github/` - Instruções e automações

## Observações
- O tema já está configurado para AppCompat.
- O projeto é compatível com Android Studio, mas otimizado para uso no VS Code.

---

Contribuições são bem-vindas!
