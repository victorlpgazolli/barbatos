import os
import zipfile
import subprocess
import plistlib
import tempfile
import urllib.request
import shutil
import lief
import lzma
from pathlib import Path

def download_gadget(cache_path: str):
    if not os.path.exists(cache_path):
        os.makedirs(os.path.dirname(cache_path), exist_ok=True)
        url = "https://github.com/frida/frida/releases/download/16.2.1/frida-gadget-16.2.1-ios-universal.dylib.xz"
        xz_path = cache_path + ".xz"
        print(f"Downloading Frida gadget from {url}...")
        urllib.request.urlretrieve(url, xz_path)
        print("Decompressing gadget...")
        with lzma.open(xz_path) as f_in, open(cache_path, "wb") as f_out:
            shutil.copyfileobj(f_in, f_out)
        os.remove(xz_path)

def repack_and_install(ipa_path: str, cert_id: str):
    cache_path = os.path.expanduser("~/.cache/frida/gadget-ios.dylib")
    
    with tempfile.TemporaryDirectory() as temp_dir:
        print(f"Extracting {ipa_path} to {temp_dir}...")
        with zipfile.ZipFile(ipa_path, 'r') as zip_ref:
            zip_ref.extractall(temp_dir)
            
        payload_dir = os.path.join(temp_dir, "Payload")
        app_dirs = [d for d in os.listdir(payload_dir) if d.endswith(".app")]
        if not app_dirs:
            raise RuntimeError("No .app directory found in Payload")
            
        app_name = app_dirs[0]
        app_dir = os.path.join(payload_dir, app_name)
        
        plist_path = os.path.join(app_dir, "Info.plist")
        with open(plist_path, 'rb') as f:
            plist = plistlib.load(f)
            
        executable_name = plist.get('CFBundleExecutable')
        bundle_id = plist.get('CFBundleIdentifier')
        
        if not executable_name:
            raise RuntimeError("Could not find CFBundleExecutable in Info.plist")
            
        executable_path = os.path.join(app_dir, executable_name)
        
        download_gadget(cache_path)
        
        frameworks_dir = os.path.join(app_dir, "Frameworks")
        os.makedirs(frameworks_dir, exist_ok=True)
        
        gadget_dest = os.path.join(frameworks_dir, "frida-gadget-ios.dylib")
        shutil.copy(cache_path, gadget_dest)
        
        print("Patching main executable...")
        binary = lief.parse(executable_path)
        if binary is None:
            raise RuntimeError(f"Failed to parse binary at {executable_path}")
            
        binary.add_library("@executable_path/Frameworks/frida-gadget-ios.dylib")
        binary.write(executable_path)
        
        print("Extracting entitlements...")
        entitlements_path = os.path.join(temp_dir, "entitlements.xml")
        res = subprocess.run(
            ['codesign', '-d', '--entitlements', ':-', app_dir],
            capture_output=True, check=True
        )
        with open(entitlements_path, 'wb') as f:
            f.write(res.stdout)
            
        print("Signing gadget...")
        subprocess.run(
            ['codesign', '-f', '-s', cert_id, gadget_dest],
            check=True
        )
        
        print("Signing app...")
        subprocess.run(
            ['codesign', '-f', '-s', cert_id, '--entitlements', entitlements_path, app_dir],
            check=True
        )
        
        base_name = os.path.splitext(os.path.basename(ipa_path))[0]
        patched_ipa_name = f"{base_name}_patched"
        
        print(f"Repackaging to {patched_ipa_name}.ipa...")
        shutil.make_archive(patched_ipa_name, 'zip', temp_dir, 'Payload')
        
        if os.path.exists(f"{patched_ipa_name}.ipa"):
            os.remove(f"{patched_ipa_name}.ipa")
        os.rename(f"{patched_ipa_name}.zip", f"{patched_ipa_name}.ipa")
        
        print(f"Installing {patched_ipa_name}.ipa...")
        subprocess.run(['devicectl', 'device', 'install', 'app', f"{patched_ipa_name}.ipa"], check=True)
        
        print(f"Launching {bundle_id}...")
        subprocess.run(['devicectl', 'device', 'process', 'launch', bundle_id], check=True)
