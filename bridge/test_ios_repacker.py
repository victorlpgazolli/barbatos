import unittest
from unittest.mock import patch, MagicMock, mock_open
import os
import zipfile
import subprocess
import plistlib
import tempfile
import urllib.request
import shutil

try:
    import ios_repacker
except ImportError:
    ios_repacker = None

class TestIosRepacker(unittest.TestCase):
    def test_import(self):
        self.assertIsNotNone(ios_repacker, "ios_repacker module should exist")

    @patch('ios_repacker.subprocess.run')
    @patch('ios_repacker.lief.parse')
    @patch('ios_repacker.urllib.request.urlretrieve')
    @patch('ios_repacker.lzma.open')
    @patch('ios_repacker.plistlib.load')
    @patch('ios_repacker.zipfile.ZipFile')
    @patch('ios_repacker.tempfile.TemporaryDirectory')
    @patch('ios_repacker.shutil.make_archive')
    @patch('ios_repacker.shutil.copy')
    @patch('ios_repacker.shutil.copyfileobj')
    @patch('ios_repacker.os.path.exists')
    @patch('ios_repacker.os.makedirs')
    @patch('ios_repacker.os.listdir')
    @patch('ios_repacker.os.remove')
    @patch('ios_repacker.os.rename')
    @patch('builtins.open', new_callable=mock_open)
    def test_repack_and_install(
        self, mock_file, mock_rename, mock_remove, mock_listdir, mock_makedirs,
        mock_exists, mock_copyfileobj, mock_copy, mock_make_archive, mock_tempdir,
        mock_zip, mock_plist, mock_lzma, mock_urlretrieve, mock_lief, mock_run
    ):
        mock_tempdir.return_value.__enter__.return_value = '/tmp/fake_dir'
        mock_listdir.return_value = ['TestApp.app']
        mock_plist.return_value = {
            'CFBundleExecutable': 'TestApp',
            'CFBundleIdentifier': 'com.test.app'
        }
        mock_exists.return_value = False # Gadget doesn't exist locally

        # Mock lief binary
        mock_binary = MagicMock()
        mock_lief.return_value = mock_binary

        # Mock subprocess.run for codesign entitlements extraction
        mock_run_result = MagicMock()
        mock_run_result.stdout = b'<?xml version="1.0" encoding="UTF-8"?><dict></dict>'
        mock_run.return_value = mock_run_result

        ios_repacker.repack_and_install('test.ipa', 'fake_cert_id')

        # Assert ZIP extraction
        mock_zip.assert_called_with('test.ipa', 'r')
        
        # Assert Gadget download
        mock_urlretrieve.assert_called()

        # Assert lief was used to add load command
        mock_lief.assert_called_with('/tmp/fake_dir/Payload/TestApp.app/TestApp')
        mock_binary.add_library.assert_called_with('@executable_path/Frameworks/frida-gadget-ios.dylib')
        mock_binary.write.assert_called()

        # Assert codesign was called
        # 1. extract entitlements
        # 2. sign gadget
        # 3. sign app
        mock_run.assert_any_call([
            'codesign', '-d', '--entitlements', ':-', '/tmp/fake_dir/Payload/TestApp.app'
        ], capture_output=True, check=True)

        mock_run.assert_any_call([
            'codesign', '-f', '-s', 'fake_cert_id',
            '/tmp/fake_dir/Payload/TestApp.app/Frameworks/frida-gadget-ios.dylib'
        ], check=True)

        mock_run.assert_any_call([
            'codesign', '-f', '-s', 'fake_cert_id', '--entitlements',
            '/tmp/fake_dir/entitlements.xml', '/tmp/fake_dir/Payload/TestApp.app'
        ], check=True)

        # Assert ZIP creation
        mock_make_archive.assert_called_with('test_patched', 'zip', '/tmp/fake_dir', 'Payload')
        mock_rename.assert_called_with('test_patched.zip', 'test_patched.ipa')

        # Assert installation and launch
        mock_run.assert_any_call(['devicectl', 'device', 'install', 'app', 'test_patched.ipa'], check=True)
        mock_run.assert_any_call(['devicectl', 'device', 'process', 'launch', 'com.test.app'], check=True)

if __name__ == '__main__':
    unittest.main()