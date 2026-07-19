#!/usr/bin/env python3
"""
Xiaomi Cooling Fan BLE Controller
==================================
Control Xiaomi Ice Freeze / Magnetic Cooling Fan via Bluetooth on Windows.

This device uses a Telink vendor-specific service (0xAF00) rather than
the standard Xiaomi MiBeacon encrypted service (0xFE95). Commands are
likely sent as plain BLE writes to custom characteristics.

Usage:
    python xiaomi_cooler_control.py              # interactive
    python xiaomi_cooler_control.py scan         # scan only
    python xiaomi_cooler_control.py <addr> brute # brute-force discovery
"""

import asyncio
import struct
import sys
from enum import IntEnum

from bleak import BleakScanner, BleakClient
from bleak.backends.device import BLEDevice

# ============================================================
# Device MIoT Spec (from home.miot-spec.com)
# xiaomi.cooler.r02p
# ============================================================
COOLER_SIID = 3
LIGHT_SIID  = 4

class CoolerMode(IntEnum):
    LOW     = 0
    MID     = 1
    HIGH    = 2

class LightMode(IntEnum):
    RAINBOW   = 0
    BREATH    = 1
    FLOWING   = 2
    BRIGHT    = 3
    OFF       = 4

# F4 通知验证的真实 2 字节 LE 值（2026-07-19 物理按键实测）
F2_LIGHT_BYTES = {
    LightMode.RAINBOW: 0x0001,  # → 01 00
    LightMode.BREATH:  0x0202,  # → 02 02
    LightMode.FLOWING: 0x0103,  # → 03 01
    LightMode.BRIGHT:  0x0104,  # → 04 01
    LightMode.OFF:     0x0000,  # → 00 00
}
F4_LIGHT_BYTES = {v: k for k, v in F2_LIGHT_BYTES.items()}  # 反向查 LightMode

# ============================================================
# Helpers
# ============================================================

def fmt_hex(data: bytes) -> str:
    return ' '.join(f'{b:02x}' for b in data)

def banner():
    print("=" * 60)
    print("  Xiaomi Cooling Fan BLE Controller")
    print("=" * 60)
    print()

# ============================================================
# Main Controller
# ============================================================

class XiaomiCoolerController:
    KNOWN_NAMES = [
        "Xiaomi Cooler", "MI Cooler", "Mijia Cooler", "Cooler",
        "冰封散热", "散热背夹",
    ]

    def __init__(self, mac_file="cooler_mac.txt"):
        self.client: BleakClient | None = None
        self.device: BLEDevice | None = None
        self.char_map: dict = {}
        self.write_chars: list[str] = []
        self.notify_chars: list[str] = []
        self.read_chars: list[str] = []
        self.notif_log: list[str] = []
        self._notif_q: asyncio.Queue | None = None
        self._notif_active = False
        self._mac_file = mac_file
        self._light_state = 1  # tracked light mode (default OFF)
        self._power_state = 0  # tracked power mode (default LOW)

    def _load_mac(self) -> str | None:
        """Load saved MAC address."""
        try:
            with open(self._mac_file) as f:
                mac = f.read().strip()
                if mac: return mac
        except: pass
        return None

    def _save_mac(self, addr: str):
        """Save MAC address for fast reconnect."""
        try:
            with open(self._mac_file, 'w') as f:
                f.write(addr)
        except: pass

    # ---------- Persistent notification listener ----------

    async def _start_notif(self):
        """Subscribe to all notify chars and keep them active."""
        if self._notif_active:
            return
        self._notif_q = asyncio.Queue()
        def handler(s, d):
            self._notif_q.put_nowait(bytes(d))
        ok = 0
        for nuid in self.notify_chars:
            try:
                await self.client.start_notify(nuid, handler)
                ok += 1
            except Exception as e:
                print(f"  [Notif] sub FAIL {nuid}: {e}")
        self._notif_active = ok > 0
        if ok:
            print(f"  [Notif] subscribed ({ok}/{len(self.notify_chars)})")
        else:
            print("  [Notif] ALL subscriptions FAILED!")

    async def _stop_notif(self):
        """Unsubscribe all notify chars."""
        if not self._notif_active:
            return
        self._notif_active = False
        for nuid in self.notify_chars:
            try:
                await self.client.stop_notify(nuid)
            except Exception:
                pass
        self._notif_q = None

    async def _wait_notif(self, timeout=2.0):
        """Get one notification from the persistent queue."""
        if not self._notif_q:
            return None
        try:
            return await asyncio.wait_for(self._notif_q.get(), timeout=timeout)
        except asyncio.TimeoutError:
            return None

    # ---------- Scan ----------

    async def scan(self, timeout: float = 10.0) -> list[tuple[BLEDevice, int]]:
        print(f"\n[Scan] scanning BLE devices (timeout={timeout}s)...")
        discovered = await BleakScanner.discover(timeout=timeout, return_adv=True)
        result = []
        for addr, (dev, adv) in discovered.items():
            result.append((dev, adv.rssi))
        result.sort(key=lambda x: x[1], reverse=True)
        return result

    async def scan_with_names(self, timeout: float = 8.0) -> list[dict]:
        """Scan and return [{name, address, rssi}] sorted by RSSI desc.
        Devices without a name are still included, name shown as '(unknown)'."""
        print(f"\n[Scan] scanning BLE devices (timeout={timeout}s)...")
        discovered = await BleakScanner.discover(timeout=timeout, return_adv=True)
        result = []
        for addr, (dev, adv) in discovered.items():
            name = dev.name or adv.local_name or "(unknown)"
            result.append({
                "name": name,
                "address": addr,
                "rssi": adv.rssi,
            })
        # Sort: known cooler names first, then by RSSI desc
        def sort_key(d):
            is_known = any(k.lower() in d["name"].lower() for k in self.KNOWN_NAMES)
            return (0 if is_known else 1, -d["rssi"])
        result.sort(key=sort_key)
        return result

    async def find_device(self, timeout: float = 8.0) -> BLEDevice | None:
        # Try saved MAC first (fast reconnect)
        saved = self._load_mac()
        if saved:
            print(f"  [Fast] trying saved MAC: {saved}")
            from bleak import BleakScanner
            devs = await BleakScanner.discover(timeout=3, return_adv=True)
            for addr, (d, adv) in devs.items():
                if addr.upper() == saved.upper() or (d.name and addr.upper() == saved.upper()):
                    print(f"  [OK] found saved device: {d.name} @ {addr}")
                    self.device = d
                    return d
            print(f"  [Fast] saved MAC not found, scanning...")

        # Fall back to full scan
        devices = await self.scan(timeout)
        for d, rssi in devices:
            if d.name and any(k.lower() in d.name.lower() for k in self.KNOWN_NAMES):
                print(f"  [OK] found: {d.name} @ {d.address}  RSSI: {rssi}")
                self.device = d
                self._save_mac(d.address)
                return d
        print("  [FAIL] device not found")
        return None

    async def connect(self, device: BLEDevice | str) -> bool:
        addr = device.address if isinstance(device, BLEDevice) else device
        if isinstance(device, BLEDevice):
            self.device = device
        print(f"\n[Connect] connecting to {addr} ...")
        self.client = BleakClient(addr)
        try:
            await self.client.connect(timeout=15)
            if self.client.is_connected:
                print(f"  [OK] connected")
                return True
        except Exception as e:
            print(f"  [FAIL] {e}")
        return False

    async def ensure_connected(self) -> bool:
        if self.client and self.client.is_connected:
            return True
        if not self.device:
            return False
        print("  [Reconnect] ...", end='', flush=True)
        try:
            self.client = BleakClient(self.device.address)
            await self.client.connect(timeout=15)
            print("ok")
            return True
        except Exception:
            print("fail")
            return False

    async def discover_services(self):
        """Discover and categorize all characteristics."""
        if not await self.ensure_connected():
            return

        print("\n[Service Discovery] enumerating...")
        services = self.client.services
        self.write_chars = []
        self.notify_chars = []
        self.read_chars = []

        for service in services:
            print(f"\n  Service: {service.uuid}")
            for char in service.characteristics:
                props = ', '.join(char.properties)
                self.char_map[char.uuid] = {
                    'handle': char.handle,
                    'properties': char.properties,
                    'service_uuid': service.uuid,
                }
                print(f"    Char: {char.uuid}")
                print(f"          props: {props}")
                print(f"          handle: {char.handle}")

                if 'write' in char.properties or 'write-without-response' in char.properties:
                    self.write_chars.append(char.uuid)
                if 'notify' in char.properties:
                    self.notify_chars.append(char.uuid)
                if 'read' in char.properties:
                    self.read_chars.append(char.uuid)
                    try:
                        val = await self.client.read_gatt_char(char.uuid)
                        if val and len(val) < 64:
                            print(f"          value: {fmt_hex(val)} ({val})")
                    except Exception:
                        pass

        print(f"\n  --- Summary ---")
        print(f"  Writable:    {len(self.write_chars)} chars")
        for u in self.write_chars:
            print(f"    {u}")
        print(f"  Notifiable: {len(self.notify_chars)} chars")
        print()

    # ---------- Raw write ----------

    async def write_raw(self, char_uuid: str, data: bytes, desc: str = "") -> bool:
        """Write raw bytes to a specific characteristic."""
        if not await self.ensure_connected():
            return False
        try:
            await self.client.write_gatt_char(char_uuid, data, response=False)
            print(f"  [OK] wrote {fmt_hex(data)} -> {char_uuid} {desc}")
            return True
        except Exception as e:
            print(f"  [FAIL] {char_uuid}: {e}")
            return False

    async def write_and_subscribe(self, char_uuid: str, data: bytes, desc: str = ""):
        """Write to a char while monitoring all notifiable chars for response."""
        # Subscribe to all notify chars first
        notify_events = []
        def make_handler(cuuid):
            def handler(sender, d):
                msg = f"  [NOTIFY] {cuuid}: {fmt_hex(bytes(d))}"
                print(msg)
                notify_events.append((cuuid, bytes(d)))
            return handler

        for nuid in self.notify_chars:
            try:
                await self.client.start_notify(nuid, make_handler(nuid))
            except Exception:
                pass

        # Write
        await self.write_raw(char_uuid, data, desc)
        await asyncio.sleep(1.0)

        # Unsubscribe
        for nuid in self.notify_chars:
            try:
                await self.client.stop_notify(nuid)
            except Exception:
                pass

        return notify_events

    # ---------- Brute force discovery ----------

    async def brute_force(self):
        """Try comprehensive command patterns on all writable chars."""
        if not self.write_chars:
            print("[Error] no writable characteristics found")
            return

        # Also read 0xFEE1 to see current value
        fee1_uuid = "0000fee1-0000-1000-8000-00805f9b34fb"
        if fee1_uuid in self.char_map:
            try:
                val = await self.client.read_gatt_char(fee1_uuid)
                print(f"\n[Read 0xFEE1] current value: {fmt_hex(val)} ({len(val)} bytes)")
            except Exception as e:
                print(f"\n[Read 0xFEE1] failed: {e}")

        # Mode value to try (HIGH = 2)
        mode_val = 2
        light_val = 0x02  # Rainbow

        patterns = [
            # --- Single bytes ---
            (b'\x00', "byte 0x00"),
            (b'\x01', "byte 0x01"),
            (b'\x02', "byte 0x02"),
            (bytes([mode_val]), f"byte {mode_val} (mode)"),
            (bytes([light_val]), f"byte {light_val} (light)"),
            (b'\x03', "byte 0x03"),
            (b'\xff', "byte 0xff"),

            # --- 2-byte commands ---
            (b'\x01\x00', "01 00"),
            (b'\x01\x01', "01 01"),
            (b'\x01\x02', "01 02"),
            (b'\x02\x00', "02 00"),
            (b'\x02\x01', "02 01"),
            (b'\x02\x02', "02 02"),
            (b'\x03\x00', "03 00 (siid=3,piid=0?)"),
            (b'\x03\x01', "03 01 (siid=3,piid=1?)"),
            (b'\x03\x02', "03 02"),
            (b'\x04\x00', "04 00 (light off?)"),
            (b'\x04\x01', "04 01 (light on?)"),
            (b'\x04\x02', "04 02 (light mode?)"),
            (b'\x40\x00', "40 00 (mode smart?)"),
            (b'\x40\x01', "40 01 (mode mid?)"),
            (b'\x40\x02', "40 02 (mode high?)"),
            (b'\x42\x00', "42 00 (light bright?)"),
            (b'\x42\x01', "42 01 (light off?)"),
            (b'\x42\x02', "42 02 (light rainbow?)"),
            (b'\x42\x03', "42 03 (light breath?)"),
            (b'\x42\x04', "42 04 (light flowing?)"),

            # --- 3-byte: cmd + value ---
            (b'\x00\x40\x00', "00 40 00"),
            (b'\x00\x40\x01', "00 40 01"),
            (b'\x00\x40\x02', "00 40 02"),
            (b'\x00\x42\x00', "00 42 00"),
            (b'\x00\x42\x01', "00 42 01"),
            (b'\x00\x42\x02', "00 42 02"),
            (b'\x00\x42\x03', "00 42 03"),
            (b'\x00\x42\x04', "00 42 04"),

            # --- 4-byte ---
            (b'\x03\x00\x01\x00', "03 00 01 00"),
            (b'\x03\x00\x01\x01', "03 00 01 01"),
            (b'\x03\x00\x01\x02', "03 00 01 02"),
            (b'\x04\x00\x01\x01', "04 00 01 01 (light on?)"),
            (b'\x04\x00\x01\x00', "04 00 01 00 (light off?)"),
            (b'\x04\x02\x01\x00', "04 02 01 00 (rainbow)"),
            (b'\x04\x02\x01\x01', "04 02 01 01 (breath)"),
            (b'\x04\x02\x01\x02', "04 02 01 02 (flowing)"),
            (b'\x04\x02\x01\x03', "04 02 01 03 (bright)"),
            (b'\x04\x02\x01\x04', "04 02 01 04 (off)"),

            # --- Notification sub-packet frames ---
            (b'\xfe\xdc\xba\x81\xf4\x00\x05\x00\x03\x00\x40\x02\xef',
             "sub-frame mode=2"),
            (b'\xfe\xdc\xba\x81\xf4\x00\x06\x00\x04\x00\x42\x02\x01\xef',
             "sub-frame light=rainbow"),
        ]

        for char_uuid in self.write_chars:
            short = char_uuid[:8]
            print(f"\n{'='*50}")
            print(f"[Brute] {len(patterns)} patterns on {short}...")
            print(f"{'='*50}")

            for data, desc in patterns:
                events = await self.write_and_subscribe(char_uuid, data, f"[{desc}]")
                if any("42 02" in str(e) or "40 02" in str(e) for e in 
                       [fmt_hex(d) for _, d in events]):
                    print(f"  *** MODE/LIGHT CHANGED IN RESPONSE! ***")
                await asyncio.sleep(0.15)

    # ---------- Auth + Commands (from HCI log analysis) ----------

    async def do_auth(self) -> bool:
        """Two-stage auth: c0 (static) + c1 (replay from HCI log).
        Uses persistent notification listener (started once, kept alive)."""
        af07 = "0000af07-0000-1000-8000-00805f9b34fb"
        if af07 not in self.char_map:
            return False

        await self._start_notif()

        c0_50 = bytes.fromhex("fe dc ba c0 50 00 12 9b 01 f5 8e 94 9e f4 6f 0f b3 3b 42 dd 4e ba c8 b7 0f ef")
        c0_51 = bytes.fromhex("fe dc ba c0 51 00 03 01 01 00 ef")
        c1_50 = bytes.fromhex("fe dc ba c1 50 00 12 69 01 70 e9 3e a1 41 e1 fc 67 3e 01 7e 97 ea dc 6b 96 ef")
        c1_51 = bytes.fromhex("fe dc ba c1 51 00 03 6a 01 00 ef")
        init_frames = [
            bytes.fromhex("fe dc ba c1 0c 00 05 6b 00 00 00 00 ef"),
            bytes.fromhex("fe dc ba c1 02 00 05 6c ff ff ff ff ef"),
            bytes.fromhex("fe dc ba c1 09 00 05 6d ff ff ff ff ef"),
        ]

        async def step(label, cmd, expect, timeout=2.0):
            await self.write_raw(af07, cmd, f"[{label}]")
            rsp = await self._wait_notif(timeout)
            ok = rsp and expect in rsp[:6]
            print(f"  {'[OK]' if ok else '[..]'} {label}")
            return ok

        print("  [Auth] c0 (static key)...")
        await step("c0-50", c0_50, b'\x01\x50')
        await step("c0-51", c0_51, b'\x01\x51')

        print("  [Auth] c1 (replay)...")
        await step("c1-50", c1_50, b'\x01\x50')
        await step("c1-51", c1_51, b'\x01\x51')

        for i, f in enumerate(init_frames):
            await self.write_raw(af07, f, f"[init-{i}]")
            await asyncio.sleep(0.1)

        print("  [OK] dual auth complete")
        return True

    async def set_mode(self, mode: CoolerMode):
        """Set power - direct F2 write."""
        # Swap: LOW→val=0x01, MID→val=0x00 (physical feel confirmed by user)
        val = {0: 0x01, 1: 0x00, 2: 0x02}.get(mode, 0)
        self._power_state = mode
        frame = b'\xfe\xdc\xba\xc1\xf2\x00\x06\x01\x04\x00\x40' + struct.pack('<H', val) + b'\xef'
        await self.write_raw(
            "0000af07-0000-1000-8000-00805f9b34fb",
            frame, f"[mode={'LOW' if val==0 else 'MID' if val==1 else 'HIGH'}({val})]")

    async def set_light_mode(self, mode: LightMode):
        """Set light - direct F2 write.
        Uses F4-verified 2-byte LE values (2026-07-19 confirmed)."""
        val = F2_LIGHT_BYTES.get(mode, 0x0001)
        self._light_state = mode
        frame = b'\xfe\xdc\xba\xc1\xf2\x00\x06\x02\x04\x00\x42' + struct.pack('<H', val) + b'\xef'
        await self.write_raw(
            "0000af07-0000-1000-8000-00805f9b34fb",
            frame, f"[light={mode.name}(0x{val:04x})]")

    async def set_light_on(self, on: bool):
        """Not needed - light mode setting includes on/off."""
        pass

    async def read_properties(self):
        """Read status via temporary subscription (most reliable)."""
        af08 = "0000af08-0000-1000-8000-00805f9b34fb"
        q = asyncio.Queue()
        def handler(s, d):
            q.put_nowait(bytes(d))
        rsp = None
        try:
            await self.client.start_notify(af08, handler)
            # Try up to 5 notifications to find an F4 broadcast
            for _ in range(5):
                try:
                    rsp = await asyncio.wait_for(q.get(), timeout=0.5)
                    if rsp and b'\x81\xf4' in rsp[:6]:
                        break
                except asyncio.TimeoutError:
                    pass
        except Exception as e:
            print(f"  [FAIL] {e}")
            return
        finally:
            try:
                await self.client.stop_notify(af08)
            except Exception:
                pass

        if not rsp or b'\x81\xf4' not in rsp[:6]:
            print("  [FAIL] no F4 notification")
            return
        idx = rsp.find(b'\x00\x3f')
        if idx != -1 and idx + 2 < len(rsp):
            t = rsp[idx + 2]
            temp = t if t < 128 else t - 256
        idx = rsp.find(b'\x00\x40')
        if idx != -1 and idx + 2 < len(rsp):
            power = rsp[idx + 2]
        idx = rsp.find(b'\x00\x42')
        if idx != -1 and idx + 3 < len(rsp):
            # F4 light: 2 bytes LE (2026-07-19 confirmed)
            light_raw = rsp[idx + 2] | (rsp[idx + 3] << 8)
            lm = F4_LIGHT_BYTES.get(light_raw)
            if lm is not None:
                self._light_state = lm
                light = lm

        print(f"\n  [Status]:")
        if temp is not None: print(f"    temp: {temp}°C")
        # Power: read from F4 (accurate), swap 0↔1 labels
        pm = ['Mid', 'Low', 'High']  # F4 0=Mid,1=Low,2=High
        if power is not None:
            p = min(power, 2)
            print(f"    power: {p} ({pm[p]})")
        # Light: parsed from F4 2-byte LE (accurate)
        if light is not None:
            lm = ['Rainbow', 'Breath', 'Flowing', 'Bright', 'OFF']
            print(f"    light: {lm[light]}")
        print()

    async def listen_button(self, duration: int = 20):
        """
        Listen for BLE notifications from the device.
        Press the physical button on the fan to see what data it sends.
        """
        if not self.notify_chars:
            print("[Error] no notification characteristics found")
            return

        print(f"\n{'='*50}")
        print(f"  [LISTEN] Subscribing to {len(self.notify_chars)} notification channels...")
        print(f"  Now press the button on your cooling fan!")
        print(f"  Listening for {duration} seconds...")
        print(f"{'='*50}\n")

        received = []

        def make_handler(cuuid):
            def handler(sender, data):
                d = bytes(data)
                print(f"  >>> [NOTIFY] {cuuid}: {fmt_hex(d)}  ({len(d)} bytes)")
                received.append((cuuid, d))
            return handler

        for nuid in self.notify_chars:
            try:
                await self.client.start_notify(nuid, make_handler(nuid))
                print(f"  Subscribed: {nuid}")
            except Exception as e:
                print(f"  Sub FAIL: {nuid}: {e}")

        for i in range(duration, 0, -1):
            print(f"  Listening... {i}s remaining  \r", end='', flush=True)
            await asyncio.sleep(1)

        print(f"\n\n[Listen] done. Unsubscribing...")
        for nuid in self.notify_chars:
            try:
                await self.client.stop_notify(nuid)
            except Exception:
                pass

        if received:
            print(f"\n{'='*50}")
            print(f"  Captured {len(received)} notifications:")
            print(f"{'='*50}")
            for cuuid, data in received:
                print(f"  [{cuuid[:8]}] {fmt_hex(data)}  | ascii: {repr(data)}")
                if len(data) >= 4:
                    # Try parsing as miot-style: [siid][piid][len][value]
                    siid = struct.unpack('<H', data[0:2])[0]
                    piid = struct.unpack('<H', data[2:4])[0]
                    print(f"         miot parse: siid={siid}, piid={piid}")
                    if len(data) > 4:
                        print(f"         value bytes: {fmt_hex(data[4:])}")
        else:
            print("\n  No notifications received.")
            print("  Try pressing the button multiple times or holding it.")

    async def disconnect(self):
        await self._stop_notif()
        if self.client and self.client.is_connected:
            try:
                await asyncio.wait_for(self.client.unpair(), timeout=3)
            except Exception:
                pass
            try:
                await self.client.disconnect()
                print("\n[Disconnect] done (device removed for next scan)")
            except Exception:
                pass

    async def raw_hex_loop(self):
        """Interactive hex write mode."""
        print("\n[Raw Hex Mode]")
        print("  Enter hex bytes to write, e.g.: 03 01 00")
        print("  Prefix with char UUID: <uuid>=<hex>")
        print("  Examples:")
        print("    03 01 00")
        print("    0000af01=03 01 01")
        print("  'l' to list chars, 'q' to quit")
        print()

        while True:
            line = input("hex> ").strip()
            if line == 'q':
                break
            if line == 'l':
                for u in self.write_chars:
                    print(f"  {u}")
                continue
            if not line:
                continue

            char_uuid = None
            hex_str = line
            if '=' in line:
                parts = line.split('=', 1)
                cuuid = parts[0].strip()
                # Normalize UUID
                if len(cuuid) == 4:
                    cuuid = f"0000{cuuid}-0000-1000-8000-00805f9b34fb"
                elif len(cuuid) == 8:
                    cuuid = f"{cuuid}-0000-1000-8000-00805f9b34fb"
                if cuuid in self.char_map:
                    char_uuid = cuuid
                hex_str = parts[1]

            try:
                data = bytes.fromhex(hex_str.replace(' ', ''))
            except ValueError:
                print("  [FAIL] invalid hex")
                continue

            if char_uuid:
                await self.write_and_subscribe(char_uuid, data, f"[raw]")
            else:
                for cu in self.write_chars:
                    await self.write_and_subscribe(cu, data, f"[raw broadcast]")
                    await asyncio.sleep(0.3)

# ============================================================
# Interactive Mode
# ============================================================

async def interactive():
    ctrl = XiaomiCoolerController()
    banner()

    device = await ctrl.find_device(timeout=8)
    if not device:
        addr = input("Enter device MAC: ").strip()
        if not addr:
            return
        device = addr

    if not await ctrl.connect(device):
        return

    await ctrl.discover_services()
    await ctrl.do_auth()

    print("\n" + "=" * 50)
    print("  Menu:")
    print("  [1] Power: LOW")
    print("  [2] Power: MID")
    print("  [3] Power: HIGH")
    print("  [4] Light: Rainbow")
    print("  [5] Light: Breath")
    print("  [6] Light: Flowing")
    print("  [7] Light: Bright")
    print("  [8] Light: OFF")
    print("  [r] Read status (temp/power/light)")
    print("  [b] BRUTE FORCE")
    print("  [l] LISTEN - press button, capture notifications")
    print("  [h] Hex raw write mode")
    print("  [d] Rediscover services")
    print("  [q] Quit")
    print("=" * 50)

    while True:
        cmd = input("\n> ").strip().lower()
        if cmd == 'q':
            break
        elif cmd == '1':
            await ctrl.set_mode(CoolerMode.LOW)
        elif cmd == '2':
            await ctrl.set_mode(CoolerMode.MID)
        elif cmd == '3':
            await ctrl.set_mode(CoolerMode.HIGH)
        elif cmd == '4':
            await ctrl.set_light_mode(LightMode.RAINBOW)
        elif cmd == '5':
            await ctrl.set_light_mode(LightMode.BREATH)
        elif cmd == '6':
            await ctrl.set_light_mode(LightMode.FLOWING)
        elif cmd == '7':
            await ctrl.set_light_mode(LightMode.BRIGHT)
        elif cmd == '8':
            await ctrl.set_light_mode(LightMode.OFF)
        elif cmd == 'r':
            await ctrl.read_properties()
        elif cmd == 'b':
            await ctrl.brute_force()
        elif cmd == 'l':
            await ctrl.listen_button(duration=20)
        elif cmd == 'h':
            await ctrl.raw_hex_loop()
        elif cmd == 'd':
            await ctrl.discover_services()
        else:
            print("  Unknown")
    await ctrl.disconnect()

# ============================================================
# Brute-force one-shot
# ============================================================

async def brute_oneshot(addr: str | None):
    ctrl = XiaomiCoolerController()
    if not addr:
        dev = await ctrl.find_device(timeout=6)
        if not dev:
            print("Device not found")
            return
        addr = dev.address
    if not await ctrl.connect(addr):
        return
    await ctrl.discover_services()
    await ctrl.do_auth()
    await ctrl.brute_force()
    await ctrl.disconnect()

# ============================================================
# Main
# ============================================================

async def main():
    if len(sys.argv) > 1 and sys.argv[1] == "scan":
        ctrl = XiaomiCoolerController()
        await ctrl.scan(timeout=8)
        return
    if len(sys.argv) > 1 and sys.argv[1] == "brute":
        addr = sys.argv[2] if len(sys.argv) > 2 else None
        await brute_oneshot(addr)
        return
    await interactive()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\nExit")
