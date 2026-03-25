# UI-Utils Meteor Addon
Addon para Meteor Client que agrega utilidades de depuracion y control de GUI inspiradas en UI-Utils.

## Requisitos
- Minecraft (Fabric) + Fabric API
- Meteor Client (addon cargado)

## Como usar
- Activa el modulo `ui-utils` dentro de Meteor.
- Abre cualquier inventario o contenedor. Apareceran botones y un campo de texto sobre la GUI.
- Funciona en inventarios/containers, lectern, edicion de carteles y el chat de dormir.

## Funcionalidad
- **Close without packet**: cierra la GUI sin enviar `CloseHandledScreenC2SPacket` al servidor.
- **De-sync**: cierra la GUI solo del lado del servidor y mantiene la pantalla abierta en el cliente.
- **Send packets: true/false**: permite o bloquea `ClickSlotC2SPacket` y `ButtonClickC2SPacket`.
- **Delay packets: true/false**: guarda los paquetes de click y los envia todos al desactivar.
- **Save GUI**: guarda la GUI actual para restaurarla luego.
- **Disconnect and send packets**: envia los paquetes almacenados y desconecta al cliente.
- **Sync ID** y **Revision**: valores internos visibles en pantalla.
- **Copy Sync ID / Copy Revision**: copia valores al portapapeles.
- **Copy GUI Title JSON**: copia el titulo de la GUI en formato JSON.
- **Fabricate packet**: crea y envia paquetes `ClickSlotC2SPacket` o `ButtonClickC2SPacket`.
- **Chat box**: cuadro de texto para escribir en chat o ejecutar comandos sin cerrar la GUI.
- **Sign Edit**: boton "Clientside Close" que cierra la GUI y evita el siguiente `UpdateSignC2SPacket`.
- **Sleeping Chat**: boton "Client Wake Up" para despertar al cliente sin esperar al servidor.

## Restaurar GUI guardada
Despues de usar **Save GUI**, puedes restaurar la GUI cuando no hay pantalla abierta:
- Tecla por defecto: `V`

## Tutorial rapido: Fabricate packet
### Click Slot Packet
1. Abre **Fabricate packet** -> **Click Slot**.
2. Rellena `Sync Id` y `Revision` con los valores que ves en la GUI.
3. Define `Slot`, `Button` y `Action`.
4. Opcional: marca **Delay** y ajusta **Times to send**.
5. Pulsa **Send**.

### Button Click Packet
1. Abre **Fabricate packet** -> **Button Click**.
2. Rellena `Sync Id` con el valor de la GUI.
3. Define `Button Id`.
4. Opcional: marca **Delay** y ajusta **Times to send**.
5. Pulsa **Send**.

## Notas
- La interfaz de "Fabricate packet" puede fallar en macOS.
- Algunas pantallas pueden comportarse distinto segun el servidor/mods instalados.
