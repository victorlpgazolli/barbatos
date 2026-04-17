# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules, collect_dynamic_libs, collect_data_files

frida_hidden = collect_submodules('frida')
frida_binaries = collect_dynamic_libs('frida')
frida_datas = collect_data_files('frida')

a = Analysis(
    ['bridge.py'],
    pathex=[],
    binaries=frida_binaries,
    datas=[
        ('agent.bundle.js', '.'),
        ('../version.txt', '.')
    ] + frida_datas,
    hiddenimports=['jdwp_frida'] + frida_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='barbatos-bridge',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
