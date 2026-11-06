"""Swap the icon inside a Windows .exe without breaking anything appended to it.

PyInstaller (and some launchers) append their program data after the end of the
Windows part of the .exe. Icon tools rewrite the file and drop that data, so we
cut it off first, change the icon on the Windows part with rcedit, then put the
data back exactly as it was.

usage: python set_icon.py app.exe icon.ico rcedit.exe
"""
import subprocess
import sys

import pefile

exe, ico, rcedit = sys.argv[1:4]
data = open(exe, "rb").read()
pe = pefile.PE(data=data, fast_load=True)
end = pe.get_overlay_data_start_offset() or len(data)
pe.close()
stub, overlay = data[:end], data[end:]
print(f"{exe}: {len(data)} bytes, program data appended: {len(overlay)} bytes")

open(exe, "wb").write(stub)
subprocess.run([rcedit, exe, "--set-icon", ico], check=True)
open(exe, "ab").write(overlay)
print("icon set")
