import os
import subprocess
import shutil
import lzma
import urllib.request
import plistlib
import time
from pathlib import Path

def download_gadget(cache_path: str, version="17.9.1"):
    if not os.path.exists(cache_path):
        os.makedirs(os.path.dirname(cache_path), exist_ok=True)
        url = f"https://github.com/frida/frida/releases/download/{version}/frida-gadget-{version}-ios-universal.dylib.xz"
        xz_path = cache_path + ".xz"
        print(f"Downloading Frida gadget {version}...")
        urllib.request.urlretrieve(url, xz_path)
        print("Decompressing gadget...")
        with lzma.open(xz_path) as f_in, open(cache_path, "wb") as f_out:
            shutil.copyfileobj(f_in, f_out)
        os.remove(xz_path)

def get_device_id():
    res = subprocess.run(['idevice_id', '-l'], capture_output=True, check=True)
    ids = res.stdout.decode().strip().split('\n')
    return ids[0] if ids and ids[0] else None

def repack_and_install(app_path: str):
    """
    Step 1: Inject Frida Gadget into the local .app bundle.
    Returns: (device_id, bundle_id)
    """
    device_id = get_device_id()
    if not device_id:
        raise RuntimeError("No iOS device detected")

    cache_path = os.path.expanduser("~/.cache/frida/gadget-ios.dylib")
    download_gadget(cache_path)
    
    print(f"Surgically injecting Frida into: {app_path}")
    
    # 1. Inject Gadget
    frameworks_dir = os.path.join(app_path, "Frameworks")
    os.makedirs(frameworks_dir, exist_ok=True)
    gadget_dest = os.path.join(frameworks_dir, "frida-gadget-ios.dylib")
    shutil.copy(cache_path, gadget_dest)
    
    # Extract Bundle ID for the future launch
    plist_path = os.path.join(app_path, "Info.plist")
    with open(plist_path, 'rb') as f:
        plist = plistlib.load(f)
    bundle_id = plist['CFBundleIdentifier']
    
    print(f"Frida Gadget injected. Now please press 'Run' in Xcode to deploy.")
    return device_id, bundle_id
