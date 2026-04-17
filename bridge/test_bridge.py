import unittest
from unittest.mock import MagicMock, patch
import sys
import os

# Ensure the bridge directory is in the path so we can import bridge
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from bridge import FridaBridge, LogBufferHandler

class TestFridaBridge(unittest.TestCase):
    # Tests the core logic and state management of the FridaBridge
    
    def setUp(self):
        # Initializes a fresh bridge instance for each test
        self.bridge = FridaBridge()

    def test_update_progress(self):
        # Verifies that _update_progress correctly modifies the internal state of a specific step
        self.bridge._update_progress("get_target", "completed")
        step = next(s for s in self.bridge.injection_progress["steps"] if s["id"] == "get_target")
        self.assertEqual(step["status"], "completed")

    @patch('bridge.subprocess.run')
    def test_setup_forwards_skipped(self, mock_run):
        # Simulates a scenario where ADB forwards are already configured and should be skipped
        mock_run.return_value = MagicMock(stdout="tcp:8700 jdwp:1234\ntcp:27042 tcp:27042", returncode=0)
        skipped = self.bridge._setup_forwards_if_needed("1234")
        self.assertTrue(skipped)

    @patch('bridge.subprocess.run')
    def test_setup_forwards_needed(self, mock_run):
        # Simulates a scenario where ADB forwards are missing and need to be created
        mock_run.return_value = MagicMock(stdout="", returncode=0)
        skipped = self.bridge._setup_forwards_if_needed("1234")
        self.assertFalse(skipped)

    def test_log_buffer_handler(self):
        # Verifies that the custom log handler correctly buffers and limits log entries
        buffer = []
        handler = LogBufferHandler(buffer, limit=2)
        
        record1 = MagicMock()
        record1.getMessage.return_value = "log1"
        record1.exc_info = None
        record1.exc_text = None
        record1.stack_info = None
        handler.emit(record1)
        
        record2 = MagicMock()
        record2.getMessage.return_value = "log2"
        record2.exc_info = None
        record2.exc_text = None
        record2.stack_info = None
        handler.emit(record2)
        
        record3 = MagicMock()
        record3.getMessage.return_value = "log3"
        record3.exc_info = None
        record3.exc_text = None
        record3.stack_info = None
        handler.emit(record3)
        
        # Should only contain log2 and log3 due to limit=2
        self.assertEqual(len(buffer), 2)
        self.assertIn("log2", buffer[0])
        self.assertIn("log3", buffer[1])

    @patch('bridge.FridaBridge.get_session')
    def test_handle_rpc_routing(self, mock_get_session):
        # Mock script and exports
        mock_script = MagicMock()
        self.bridge.script = mock_script
        
        # Test cases for handle_rpc routing: (method_name, params, expected_export_name, expected_args)
        test_cases = [
            ("listClasses", {"search_param": "S"}, "listclasses", ("S",)),
            ("inspectClass", {"className": "C"}, "inspectclass", ("C",)),
            ("countInstances", {"className": "C"}, "countinstances", ("C",)),
            ("listInstances", {"className": "C"}, "listinstances", ("C",)),
            ("inspectInstance", {"className": "C", "id": "1", "offset": 0, "limit": 10}, "inspectinstance", ("C", "1", 0, 10)),
            ("setFieldValue", {"className": "C", "id": "1", "fieldName": "f", "type": "int", "newValue": "2"}, "setfieldvalue", ("C", "1", "f", "int", "2")),
            ("getpackagename", {}, "getpackagename", ()),
            ("hookMethod", {"className": "C", "methodSig": "M"}, "hookmethod", ("C", "M")),
            ("unhookMethod", {"className": "C", "methodSig": "M"}, "unhookmethod", ("C", "M")),
            ("getHookEvents", {}, "gethookevents", ()),
            ("getInstanceAddresses", {"className": "C"}, "getinstanceaddresses", ("C",)),
            ("runOnce", {"className": "C", "methodSig": "M", "code": "code"}, "runonce", ("C", "M", "code")),
        ]

        for method, params, export_name, expected_args in test_cases:
            export_mock = MagicMock()
            setattr(mock_script.exports_sync, export_name, export_mock)
            
            self.bridge.handle_rpc(method, params)
            export_mock.assert_called_with(*expected_args)

    @patch('bridge.FridaBridge._get_application_pid_and_package')
    @patch('bridge.FridaBridge._prepare_gadget')
    def test_handle_rpc_prepare_environment(self, mock_prepare, mock_get_app):
        mock_get_app.return_value = (1234, "com.example")
        result = self.bridge.handle_rpc("prepareEnvironment", {})
        self.assertEqual(result["pid"], 1234)
        mock_prepare.assert_called_with(1234)

    @patch('bridge.FridaBridge._pushGadget')
    def test_handle_rpc_check_push_gadget(self, mock_push):
        result = self.bridge.handle_rpc("checkOrPushGadget", {})
        self.assertEqual(result["status"], "ok")
        mock_push.assert_called_once()

    def test_handle_rpc_reset_injection(self):
        self.bridge.is_injecting_gadget = False
        result = self.bridge.handle_rpc("resetInjection", {})
        self.assertEqual(result["status"], "ok")

    @patch('bridge.threading.Thread')
    def test_handle_rpc_inject_gadget_from_scratch(self, mock_thread):
        result = self.bridge.handle_rpc("injectGadgetFromScratch", {})
        self.assertEqual(result["status"], "running")
        mock_thread.assert_called_once()

    @patch('bridge.FridaBridge._run_jdwp')
    @patch('bridge.FridaBridge._reattach_frida')
    def test_handle_rpc_inject_jdwp(self, mock_reattach, mock_run_jdwp):
        mock_run_jdwp.return_value = {"status": "completed"}
        result = self.bridge.handle_rpc("injectJdwp", {"package_name": "p"})
        self.assertEqual(result["status"], "completed")
        mock_run_jdwp.assert_called_once()

if __name__ == '__main__':
    unittest.main()
