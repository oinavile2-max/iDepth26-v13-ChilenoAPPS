# iDepth 26 Wallpapers — ChilenoAPPS

Versão 1.4.0 do app Android de Live Wallpaper com profundidade espacial.

## Recursos
- 20 wallpapers offline em duas camadas.
- Catálogo remoto Supabase com cache offline.
- Abas Início, Categorias, Favoritos e Ajustes.
- Download sob demanda de wallpapers online.
- Categorias e novos wallpapers atualizáveis sem recompilar o APK.
- Canal Telegram: @iDepth26Wallpapers.
- Parallax / giroscópio.
- Relógio em plano intermediário de profundidade.
- Ajuste por arrastar, pinçar e toque duplo para redefinir.
- Troca automática de wallpapers offline / fotos importadas.

## Catálogo remoto
O app tenta primeiro:
`https://eyxwgmcxullybhsqboqh.supabase.co/functions/v1/wallpaper-catalog`

Se essa função ainda não existir, usa automaticamente:
`https://eyxwgmcxullybhsqboqh.supabase.co/storage/v1/object/public/idepth26/catalog.json`

Veja `SUPABASE_V1.4_README.txt` para implantar a função que lê a tabela `public.wallpapers`.

## Build
O workflow `.github/workflows/build-apk.yml` compila o APK pelo GitHub Actions.
