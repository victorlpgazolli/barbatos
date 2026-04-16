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
        mock_script.exports_sync.listclasses.return_value = ["java.lang.String"]
        
        # Test listClasses routing
        result = self.bridge.handle_rpc("listClasses", {"search_param": "String"})
        self.assertEqual(result, ["java.lang.String"])
        mock_script.exports_sync.listclasses.assert_called_with("String")

        # Test runOnce routing
        mock_script.exports_sync.runonce.return_value = True
        result = self.bridge.handle_rpc("runOnce", {"className": "A", "methodSig": "M", "code": "C"})
        self.assertTrue(result)
        mock_script.exports_sync.runonce.assert_called_with("A", "M", "C")

if __name__ == '__main__':
    unittest.main()
