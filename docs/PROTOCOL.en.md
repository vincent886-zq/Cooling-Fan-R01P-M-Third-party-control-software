# BLE communication and protocol notes

[简体中文](PROTOCOL.md) · **English**

[Home](../README.en.md) · [Development](DEVELOPMENT.en.md)

These notes describe what I learned while inspecting captures, checking the physical button and implementing control. They follow the current source and later development records, not an official Xiaomi protocol specification. Full phone HCI captures and personal device addresses are not published.

![Local BLE control and device feedback](images/communication-overview.png)

[Open SVG](images/communication-overview.svg)

## GATT channels

UUIDs use the Bluetooth base suffix `-0000-1000-8000-00805f9b34fb`.

| Short UUID | Full prefix | Role |
| --- | --- | --- |
| AF00 | `0000af00` | Vendor-specific service |
| AF07 | `0000af07` | Authentication / initialization / control writes; Android currently uses write-without-response |
| AF08 | `0000af08` | Device replies and unsolicited status notifications |

Names help identify scan results, but the service and characteristics are the actual communication entry points. A successful GATT write does not by itself establish that the device application accepted a command.

## MMA frames

Historical notes call these MMA private frames. The observed basic structure is:

```text
FE DC BA | header | opcode | payload_length (2B, BE) | payload | EF
```

| Field / operation | Current interpretation |
| --- | --- |
| `C0` / `C1` | Different headers observed in historical host exchanges; not every header bit has a confirmed meaning |
| `01` | Header observed in reply frames |
| `81 F4` | Unsolicited device status |
| `50` / `51` | Historical authentication exchanges |
| `F2` | Property control |
| `F3` | Property queries observed in records; the everyday control path mainly uses F4 status |
| `02` / `09` / `0C` | Initialization / device-information query sequence |

**The outer payload length is big-endian; power and lighting values are little-endian.** An `EF` inside the payload must not automatically be treated as the terminator. A notification may contain multiple subframes, so one callback is not necessarily one complete business message.

## Three power modes

| UI mode | Value | Two-byte LE |
| --- | --- | --- |
| Silent / LOW | `0x0001` | `01 00` |
| Smart / MID | `0x0000` | `00 00` |
| Extreme / HIGH | `0x0002` | `02 00` |

The UI index is not the property value: Silent and Smart have the opposite order. Android currently constructs:

```text
FE DC BA C1 F2 00 06 01 04 00 40 [value_L] [value_H] EF
```

`40` identifies the power property. Some writes have direct historical capture examples; later physical-button and device-behavior checks contributed to the mapping. The raw capture evidence is not equally complete for every mode.

## Five lighting modes

| UI mode | Value | Two-byte LE |
| --- | --- | --- |
| Rainbow | `0x0001` | `01 00` |
| Breathing | `0x0202` | `02 02` |
| Flowing | `0x0103` | `03 01` |
| Steady | `0x0104` | `04 01` |
| Off | `0x0000` | `00 00` |

Android currently constructs:

```text
FE DC BA C1 F2 00 06 02 04 00 42 [value_L] [value_H] EF
```

`42` identifies the lighting property. The table follows current code and later physical-button checks, not the superseded table in an early report. The second value byte's full meaning remains unconfirmed; it is not arbitrarily labeled speed or brightness.

**A remaining field ambiguity:** historical notes interpreted the first F2 payload byte as either a sequence or a subpacket identifier. Android fixes it at `01` for power and `02` for lighting; Web modifies it. These notes describe the implementations as they exist rather than silently unifying them into an unproven rule across firmware versions.

## F4 status

Observed single-property notification examples:

```text
FE DC BA 81 F4 00 05 [seq] 03 00 3F [temperature] EF
FE DC BA 81 F4 00 05 [seq] 03 00 40 [power] EF
FE DC BA 81 F4 00 06 [seq] 04 00 42 [light_L] [light_H] EF
```

- Temperature `003F`: signed 8-bit value; raw bytes above 127 represent negative values. This is the cooler's sensor report, not phone processor temperature.
- Power `0040`: mapped using the power table above.
- Lighting `0042`: combine both LE bytes and look up the result, rather than using only the first byte.
- `03` / `04` correspond to property-entry lengths, not power or lighting mode numbers.

Physical-button changes can also enter this notification path. Android passes parsed F4 values to the ViewModel; the Python GUI updates through its background status handling and Qt signals.

## Authentication and initialization

1. The records show c0 and c1 exchanges. Later analysis found a repeatable c0 challenge payload in the observed records, while c1 payloads varied by session.
2. Later hardware notes report control after c0 alone. **The published code still retains both fixed c0 and c1 sequences**, including a c1 replay; that is a difference between the record and the implementation.
3. A captured fixed exchange working on a particular unit is not knowledge of a universal key or a complete dynamic challenge-response algorithm.
4. Historical records show that F4 can appear before control authentication is complete. A temperature notification alone does not establish authentication success or execution of a particular F2 command.
5. I use the app successfully on my Android phone, but its code is not a protocol state machine that strictly validates every reply. When investigating another device, check its behavior and reports rather than relying only on an Auth complete log.

## Details still to confirm

Seven local HCI files include overlapping, repeated or differently segmented records. File count is not the number of independent tests; old frame totals, session counts and response-rate claims are not copied without qualification.

The complete meaning of the second lighting byte, cross-device authentication compatibility, maximum update rate and temperature accuracy remain open. Mode mappings here follow current code and later button checks, not an early table that was subsequently corrected.
