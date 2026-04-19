import unittest
from unittest.mock import patch, MagicMock, MagicMock
import os
import sys
import threading
import time

# Ensure the bridge directory is in the path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from bridge import FridaBridge
import ios_repacker

class TestIosHijackWorkflow(unittest.TestCase):

    @patch('ios_repacker.get_device_id')
    @patch('ios_repacker.download_gadget')
    @patch('os.makedirs')
    @patch('shutil.copy')
    @patch('plistlib.load')
    @patch('builtins.open')
    def test_repack_and_install_injection(self, mock_open, mock_plist_load, mock_copy, mock_makedirs, mock_download, mock_get_id):
        """Test that repack_and_install correctly injects the gadget and returns IDs."""
        mock_get_id.return_value = "test_device_id"
        mock_plist_load.return_value = {'CFBundleIdentifier': 'com.test.app'}
        
        device_id, bundle_id = ios_repacker.repack_and_install("/path/to/Test.app")
        
        self.assertEqual(device_id, "test_device_id")
        self.assertEqual(bundle_id, "com.test.app")
        mock_copy.assert_called()
        mock_download.assert_called()

    @patch('subprocess.run')
    def test_monitor_and_hijack_success(self, mock_run):
        """Test the background hijacking thread succeeds when app is detected."""
        bridge = FridaBridge()
        
        # Configure mocks to simulate:
        # 1. xcodebuild is NOT running initially
        # 2. app is detected on device in second poll
        
        # Side effect for subprocess.run calls inside the while loop
        def run_side_effect(cmd, *args, **kwargs):
            mock = MagicMock()
            mock.returncode = 0
            if isinstance(cmd, str) and "xcodebuild" in cmd:
                mock.stdout = "" # Xcode not running
            elif isinstance(cmd, list) and cmd[0] == "frida-ps":
                # First time not found, second time found
                if not hasattr(run_side_effect, 'called'):
                    run_side_effect.called = True
                    mock.stdout = "PID Identifier\n123 OtherApp"
                else:
                    mock.stdout = "PID Identifier\n456 com.test.app"
            else:
                mock.stdout = "Success"
                mock.stderr = ""
            return mock

        mock_run.side_effect = run_side_effect
        
        # Run monitor logic in a way we can test (we'll call it directly for isolation)
        # but with a limited wait to avoid infinite loop if it fails
        with patch('time.sleep'): # Fast forward time
            bridge._monitor_and_hijack_ios("test_device", "com.test.app")
        
        self.assertEqual(bridge.ios_deploy_status["state"], "success")
        self.assertIn("launched with Frida", bridge.ios_deploy_status["message"])

    @patch('subprocess.run')
    def test_monitor_and_hijack_timeout(self, mock_run):
        """Test the background hijacking thread times out if app is never detected."""
        bridge = FridaBridge()
        
        mock_res = MagicMock()
        mock_res.stdout = ""
        mock_run.return_value = mock_res
        
        # Mock time to simulate immediate timeout
        with patch('time.time') as mock_time, patch('time.sleep'):
            mock_time.side_effect = [0, 600] # Start at 0, next call at 600 (exceeds 300 max_wait)
            bridge._monitor_and_hijack_ios("test_device", "com.test.app")
            
        self.assertEqual(bridge.ios_deploy_status["state"], "error")
        self.assertIn("Timed out", bridge.ios_deploy_status["message"])

if __name__ == '__main__':
    unittest.main()
