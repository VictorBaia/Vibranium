R1. PROIBIDO alterar layout, espaçamentos, tipografia, cores, formas, animações, componentes existentes (cards, listas, shelves, barras, sheets, player, tema dinâmico pela capa, efeito de vidro fosco). Dados do Navidrome entram nos componentes que já existem, através dos modelos existentes.

R2. Se um recurso exigir um componente que não existe, PARE e pergunte antes. Não crie tela nova, não redesenhe. A única adição visual permitida é o grupo "Navidrome" nas configurações, usando o padrão já existente (SearchableSettingsGroup / SettingsGroup).

R3. Compatibilidade com o upstream: este será um fork que precisa continuar recebendo merges do repositório original. Portanto:

Código novo vai em pacotes novos (ex.: com.music.bitchord.data.navidrome).
Edições em arquivos existentes devem ser mínimas e pontuais (pontos de roteamento). Não reformatar, não renomear, não mover arquivos, não apagar código do YouTube.
Antes de editar um arquivo grande (MainViewModel.kt, PlaybackService.kt, MainActivity.kt), liste exatamente quais linhas/funções serão tocadas.
R4. Modo de fonte: o app opera no modo Navidrome por padrão. O código do YouTube permanece no projeto, mas dormente e inacessível pela interface, por flag de build/configuração. Não há mistura de fontes na mesma tela.

R5. Fora de escopo, não implementar e ocultar pela flag quando aparecerem na UI: vídeos, login Google, inscrever-se em artistas, moods/gêneros do YouTube, rádios do YouTube, histórico da conta, Listen Together.

R6. Segurança: nenhuma credencial no código ou no repositório. Usuário, senha e URL do servidor são digitados nas configurações e guardados de forma segura (EncryptedSharedPreferences/Keystore). URLs de stream com token NUNCA são persistidas em fila, cache ou banco; são geradas na hora de tocar.

R7. Toda string nova vai em strings.xml (com tradução pt-BR e en). Nenhum texto fixo em Compose.

R8. Em caso de dúvida sobre comportamento do servidor, teste contra o servidor real ou a documentação oficial (navidrome.org/docs e opensubsonic.netlify.app) em vez de assumir.

Nunca modifique os componentes visuais. Se perceber que algo só funciona mudando o visual, pare e me consulte.

Em qualquer divergência entre este prompt e o que o código real permite, priorize as Regras Absolutas e avise.

5.1 Ocultar (por flag) tudo que sobrar do YouTube na interface: login Google, inscrever-se, vídeos, Listen Together. Não apagar código. 5.2 Revisar telas com estado vazio ou erro quando o servidor estiver fora do ar. 
5.3 Documentar o que muda em relação ao upstream (lista de arquivos tocados) para facilitar merges futuros. 5.4 Teste completo de regressão visual: comparar capturas de tela de cada tela antes e depois.

10. RESULTADO ESPERADO
Um fork do BitChord que abre direto no Navidrome, com visual idêntico ao original, onde: a biblioteca (Home, busca, curtidas, álbuns, artistas, playlists) é 100% do servidor; a reprodução respeita qualidade por rede, gapless, crossfade, downloads e scrobble; as letras palavra a palavra podem vir do servidor ou dos provedores do app; as capas animadas seguem funcionando; as páginas de artista mostram bio, foto e monthly listeners quando há match confiável; o Explore mostra gêneros e playlists de humor do servidor; e existe uma seção "Navidrome" nas configurações. O fork continua recebendo atualizações do repositório original com o menor atrito possível.

