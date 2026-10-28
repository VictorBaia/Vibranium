<div align="center">

<img src="Logo.png" alt="Logo do Vibranium" width="150" />

# Vibranium

### Sua música, do seu jeito, em todas as telas.

Player musical de código aberto para Android, Wear OS, TV, desktop e navegador.

<p>
  <a href="https://github.com/VictorBaia/Vibranium/releases"><img alt="Versões" src="https://img.shields.io/github/v/release/VictorBaia/Vibranium?style=for-the-badge&label=vers%C3%A3o&color=8b5cf6" /></a>
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/licen%C3%A7a-GPL--3.0-6366f1?style=for-the-badge" /></a>
  <img alt="Kotlin e Compose" src="https://img.shields.io/badge/Kotlin-Compose-ec4899?style=for-the-badge&logo=kotlin&logoColor=white" />
</p>

[Recursos](#-recursos) · [Plataformas](#-plataformas) · [Instalação](#-instalação) · [Compilação](#-compilação)

<img src="Banner.png" alt="Interface do Vibranium" width="100%" />

</div>

## ✨ Recursos

### Biblioteca e reprodução

- Pesquisa, navegação e reprodução pelo YouTube Music, arquivos locais e fontes de biblioteca configuradas.
- Integração com Navidrome por OpenSubsonic: artistas, álbuns, músicas, playlists, favoritos e streaming. Quando o servidor fornece as tags, os lançamentos são separados em singles, EPs e álbuns.
- Fontes WebDAV e SMB e integração com Spotify conforme a plataforma e a configuração.
- Downloads para ouvir offline, fila, reprodução em segundo plano e controles de mídia.
- Ajustes de qualidade de áudio, velocidade, crossfade, temporizador para dormir e equalização. O desktop inclui análise de áudio para Automix.

### Interface e descoberta

- Cores e efeitos visuais derivados da capa, interface translúcida, Canvas animado quando disponível e player em tela cheia.
- Letras sincronizadas, seleção de provedores e tradução sob demanda.
- Histórico, estatísticas de audição, Replay, scrobbling e presença no Discord quando configurados.

### Ouvir juntos

**Ouvir juntos** cria uma sala com código de seis caracteres para sincronizar fila e reprodução entre até cinco dispositivos. O servidor opcional usa Go e WebSocket. Os participantes podem controlar a música, e o anfitrião pode restringir os controles. Veja [API, implantação e limites](backend/README.md).

> [!NOTE]
> Catálogo, letras, Canvas, integrações e reprodução dependem da plataforma, dos serviços conectados, da conta e das permissões configuradas. O Vibranium não hospeda arquivos de música.

## 📱 Plataformas

| Plataforma | Implementação | Recursos específicos |
| :--- | :--- | :--- |
| Android | [`app/`](app/) | Aplicativo principal, fontes de música, downloads e controles de reprodução. |
| Wear OS | [`wearos/`](wearos/) | Controle remoto do celular com fila, volume, favoritos e seleção de saída; o relógio também pode funcionar como saída de áudio. [Guia](wearos/README.md). |
| Android TV / Google TV | [`tv/`](tv/) | Interface para controle remoto, biblioteca, pesquisa, player em tela cheia, letras e login por QR code. [Guia](tv/README.md). |
| Windows, macOS e Linux | [`desktopApp/`](desktopApp/) | Aplicativo Compose Multiplatform e pacotes por sistema. [Guia](DESKTOP.md). |
| Navegador | [`web/`](web/) | Player React e extensão para Chromium e Firefox. A extensão habilita reprodução, autenticação e integrações que exigem acesso ao navegador. [Guia](web/README.md). |

A lógica e os componentes compartilhados ficam em [`shared/`](shared/), [`sharedUi/`](sharedUi/) e [`desktopUi/`](desktopUi/). O servidor de salas fica em [`backend/`](backend/).

## ⬇️ Instalação

Consulte os pacotes em [Releases](https://github.com/VictorBaia/Vibranium/releases). Os formatos disponíveis variam por versão. Para instalar o desktop ou a extensão, siga os guias acima. O aplicativo do relógio precisa de um aplicativo Android compatível, com o mesmo tipo de build e a mesma chave de assinatura.

## 🛠️ Compilação

### Android, TV e Wear OS

Use **JDK 17**, Android SDK e, para componentes nativos, Android NDK e CMake. O aplicativo compila com **API 37**, tem como alvo **API 36** e suporta **API 26+**. Configure `sdk.dir` em `local.properties` ou defina `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

```bash
./gradlew :app:assembleDevDebug
./gradlew :tv:assembleProdDebug
./gradlew :wearos:assembleDevDebug
./gradlew :app:testDevDebugUnitTest
```

No Windows, use `gradlew.bat`. Os módulos Android só são incluídos quando há SDK configurado. Builds de produção assinados exigem a configuração descrita em [`keystore.properties.example`](keystore.properties.example).

### Desktop

Use **JDK 21**:

```bash
./gradlew :desktopApp:run
```

O [guia de desktop](DESKTOP.md) detalha as dependências e a criação de instaladores.

### Web e extensão

Use **Node.js 22+** e pnpm via Corepack:

```bash
corepack pnpm --dir web install
corepack pnpm --dir web/app dev
corepack pnpm --dir web/extension build
```

O site local abre em `http://localhost:5173`. Consulte o [guia web](web/README.md) para carregar a extensão e publicar o site.

### Servidor de salas

Na pasta `backend/`, com Go instalado:

```bash
go run .
go test ./...
```

## 📚 Documentação

| Tema | Guia |
| :--- | :--- |
| Navidrome e classificação de lançamentos | [Tipos de lançamento](docs/NAVIDROME_RELEASE_TYPES.md) |
| Tradução de letras | [Pipeline de tradução](docs/LYRICS_TRANSLATION.md) |
| Escuta compartilhada | [API e sincronização](backend/README.md) |
| Desenvolvimento web | [Player e extensão](web/README.md) |
| Contribuição | [Como contribuir](CONTRIBUTING.md) · [Código de conduta](CODE_OF_CONDUCT.md) |

## 📄 Licença

Distribuído sob a **GNU General Public License v3.0**. Leia [`LICENSE`](LICENSE) para os termos completos. Use os serviços conectados e os arquivos de mídia de acordo com suas condições e a legislação aplicável.
