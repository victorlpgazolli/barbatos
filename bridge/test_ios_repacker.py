import unittest
from unittest.mock import patch, MagicMock, mock_open
import os
import plistlib
import sys

# Ensure the bridge directory is in the path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    import ios_repacker
except ImportError:
    ios_repacker = None

class TestIosRepacker(unittest.TestCase):
    def test_import(self):
        self.assertIsNotNone(ios_repacker, "ios_repacker module should exist")

    @patch('ios_repacker.get_device_id')
    @patch('ios_repacker.download_gadget')
    @patch('os.makedirs')
    @patch('shutil.copy')
    @patch('plistlib.load')
    @patch('builtins.open', new_callable=mock_open)
    @patch('os.path.expanduser')
    def test_repack_and_install(
        self, mock_expanduser, mock_file, mock_plist_load, mock_copy, mock_makedirs, mock_download, mock_get_id
    ):
        mock_get_id.return_value = "test_device_id"
        mock_expanduser.return_value = "/tmp/.cache/frida/gadget-ios.dylib"
        mock_plist_load.return_value = {'CFBundleIdentifier': 'com.test.app'}
        
        device_id, bundle_id = ios_repacker.repack_and_install("/path/to/Test.app")
        
        self.assertEqual(device_id, "test_device_id")
        self.assertEqual(bundle_id, "com.test.app")
        mock_copy.assert_called()
        mock_download.assert_called_with("/tmp/.cache/frida/gadget-ios.dylib")
        mock_makedirs.assert_called()

if __name__ == '__main__':
    unittest.main()
