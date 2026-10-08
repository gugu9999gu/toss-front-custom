"""Maintain only the FrontDeck reverse port when the selected device reconnects."""
import argparse
import time
from device import Device

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--serial", required=True); parser.add_argument("--adb")
    args = parser.parse_args(); device = Device(args.serial, args.adb)
    connected = False
    print("선택한 기기의 FrontDeck 연결을 유지합니다. 종료: Ctrl+C", flush=True)
    while True:
        try:
            device.inspect()
            ports = device.run("reverse", "--list")
            if "tcp:38765 tcp:38765" not in ports: device.run("reverse", "tcp:38765", "tcp:38765")
            if not connected: print("기기 연결됨", flush=True)
            connected = True
        except (RuntimeError, TimeoutError):
            if connected: print("기기 연결 대기 중", flush=True)
            connected = False
        except Exception as error:
            # ADB timeout is expected when the device is switched off; no retries of actions.
            import subprocess
            if not isinstance(error, subprocess.TimeoutExpired): raise
            connected = False
        time.sleep(5)

if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: pass
