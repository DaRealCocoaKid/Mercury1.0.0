# Mercury Client (26.2)

Cliente unificado: Mercury 1.0.0 (ESP, Chunk Finder, aviso de jugadores, Freecam, Optimizador) + Zylowwwpvpclient
(Auto Anchor, Auto Crystal, Fast Totem, Combat Zylow, Crystal Hitbox) con interfaz nueva.

## Compilar (GitHub)
1. Sube esta carpeta a un repo de GitHub (el workflow `.github/workflows/build.yml` ya esta).
2. Pestania Actions -> build -> descarga el artifact `mercury-client` (el .jar).
3. Pon el .jar en `.minecraft/mods` (necesita Fabric Loader >= 0.19.1, Fabric API y Java 25).
   Quita los mods viejos (mercury-client-1_0_0 y cpvp-zylow) para que no choquen.

## Uso
- Menu del cliente: **Right Shift** (cambiable). Se abre solo al entrar a un servidor (se puede apagar en Configuracion).
- Cada modulo: switch on/off + tecla propia (click en la tecla, presiona otra; Backspace la quita).
- Combat Zylow: G, Freecam: V, Freelook: Alt izquierdo mantenido (todas cambiables).
- Config guardada en `config/mercury-client.properties` y `config/visionplus.json`.

## Notas
- `libs/mercury-classes` son las clases ya compiladas de Mercury 1.0.0 (no tenia codigo fuente); el build las mete en el jar.

## Freelook
Mantén la tecla (Alt izquierdo por defecto) y mueve el mouse: la camara gira 360 alrededor de tu personaje
sin que tu cuerpo ni tu direccion cambien, y atraviesa bloques sin chocar. Al soltar vuelve a tu vista normal.
Opciones: modo Mantener/Alternar, Atravesar bloques on/off, Distancia.

## Novedades (visuals + Mace Combo)
Todos los efectos se dibujan en el HUD proyectando el mundo a pantalla (como el Crystal Hitbox), asi se ven a traves de paredes.
- **Aura de bloque**: el bloque que miras brilla y deja estela. Color/estela/brillo ajustables.
- **Estelas**: items, totems, proyectiles, cristales, jugadores. Color (paleta, arcoiris, galaxia, fuego, hielo, RGB propio), largo, grosor.
- **ESP de jugadores**: modos Completo / Solo hitbox / Solo tracer, nombre, vida, color por vida, amigos en verde.
- **Portales (pasos)**: cada paso abre un portal de chispas tipo Doctor Strange (suelo o vertical), dura 1-6 s.
- **Aura**: bolitas de luz con estela, destellos y rayos alrededor tuyo.
- **Skybox**: cielo galactico con estrellas, auroras animadas, estrellas fugaces, estilo arcoiris RGB, nebulosa, neon sunset.
- **Mace Combo** (Cliente): volando con elytra hacia un jugador -> pechera -> mazo -> golpe; al salir volando se pone la elytra.
- **Xray**: minerales (diamante, esmeralda, oro, hierro, cobre, redstone, lapis, carbon, cuarzo) a traves de bloques, cada uno activable; tracer on/off, relleno, color natural o el que elijas.
- **Ancient Debris**: buscador tipo xray con tracer on/off, color configurable, distancia y coordenadas.
- **Skybox "Video (nebulosa)"**: cielo degradado con nubes oscuras suaves, estrellas y franjas de aurora diagonales; color del cielo (paleta o RGB) y de la aurora configurables.
- **Render suave (v25)**: brillos, lineas y destellos ahora son sprites difuminados (assets/mercuryclient/textures/fx), rotados y escalados con la pose del GUI: sin cuadritos. Skybox en columnas con degradado vertical, nubes de nebulosa con ruido horneado, borde del terreno recortado y difuminado.
- **Safe Anchor** (Cliente): al poner un respawn anchor cerca de un enemigo -> carga con glowstone, coloca bloque de escudo, cambia a totem y detona. No actua en el Nether.
- **Pestana PvP**: Aim Assist, Auto Totem, Auto Armor, Auto Gapple, Lanzar perla (asignale tecla), Sprint, Fast Place, Mace Combo, Safe Anchor.
- **Visuals**: Fullbright, Target HUD; Crystal Hitbox y ESP de jugadores ahora usan el mismo render suave (con brillo) que el resto.
- **Detector cazadores** (Cliente): vigila nombres (pagina Amigos > Cazadores del menu principal; vacia = todos). Avisa si estan en espectador, invisibles o pegados a ti con tu misma rotacion (te espectean). Panel + sonido + ESP/tracer. No ve a quien use vanish de plugin.
