# UI-Utils Meteor Addon
Meteor Client addon that brings UI-Utils style GUI debugging and control tools.

## Requirements
- Minecraft (Fabric) + Fabric API
- Meteor Client (addon loaded)

## How to use
- Enable the `ui-utils` module in Meteor.
- Open any inventory or container. A set of buttons and a text field will appear on top of the GUI.
- Works on inventories/containers and lectern screens.

![UI-Utils main panel](docs/images/ui-utils-panel.png)

## Features
- **Close without packet**: closes the GUI without sending `CloseHandledScreenC2SPacket` to the server.
- **De-sync**: closes the GUI server-side while keeping it open client-side.
- **Send packets: true/false**: allows or blocks `ClickSlotC2SPacket` and `ButtonClickC2SPacket`.
- **Delay packets: true/false**: queues click packets and sends them all at once when disabled.
- **Save GUI**: stores the current GUI for later restoration.
- **Disconnect and send packets**: flushes queued packets and disconnects the client.
- **Sync ID** and **Revision**: shows internal sync values.
- **Copy Sync ID / Copy Revision**: copies values to clipboard.
- **Copy GUI Title JSON**: copies the GUI title in JSON format.
- **Fabricate packet**: build and send `ClickSlotC2SPacket` or `ButtonClickC2SPacket`.
- **Chat box**: type chat or run commands without closing the GUI.

## Restore saved GUI
After using **Save GUI**, you can restore it while no screen is open:
- Default key: `V`

## Quick tutorial: Fabricate packet
`ClickSlotC2SPacket` and `ButtonClickC2SPacket` are the two packet types this addon can craft.

When clicking **Fabricate packet** you should see this window:

![Fabricate packet menu](docs/images/fabricate-packet-menu.png)

### Click Slot Packet
1. Open **Fabricate packet** -> **Click Slot**.

![Click Slot Packet builder](docs/images/click-slot-packet.png)
2. Fill `Sync Id` and `Revision` with the values shown in the GUI.
3. Set `Slot`, `Button`, and `Action`.
4. Optional: enable **Delay** and adjust **Times to send**.
5. Click **Send**.

### Button Click Packet
1. Open **Fabricate packet** -> **Button Click**.

![Button Click Packet builder](docs/images/button-click-packet.png)
2. Fill `Sync Id` with the value shown in the GUI.
3. Set `Button Id`.
4. Optional: enable **Delay** and adjust **Times to send**.
5. Click **Send**.
