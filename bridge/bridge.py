#!/usr/bin/env python3
import time
import traceback
import lzma
import shutil
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import frida
import json
import re
import logging
import threading
import subprocess
import argparse
import os
import sys
from jdwp_frida import run_jdwp

logging.basicConfig(level=logging.INFO)

def strip_ts_types(code):
    """
    Remove basic TypeScript type annotations and imports to make it valid JS for Frida.
    This is a simplified approach and might not handle all edge cases.
    """
    # Remove all imports
    code = re.sub(r'import\s+[\s\S]*?;', '', code)
    # Remove interfaces
    code = re.sub(r'interface\s+\w+\s*\{[\s\S]*?\}', '', code)
    # Remove type aliases
    code = re.sub(r'type\s+\w+\s*=\s*[\s\S]*?;', '', code)
    # Remove type annotations like : string, : number, : any, : MyClass, : string[]
    # We look for a colon followed by a type name, but we need to be careful not to match object literals
    # or ternary operators.
    # Usually type annotations are followed by ',', ')', '=', or '{'
    code = re.sub(r':\s*[a-zA-Z_][\w<>\[\]\s]*(\s*[,)=;{])', r'\1', code)
    # Remove return type annotations
    code = re.sub(r'\)\s*:\s*[a-zA-Z_][\w<>\[\]\s]*\s*\{', ') {', code)
    # Remove 'as Type'
    code = re.sub(r'\s+as\s+[a-zA-Z_][\w<>\[\]\s]*', '', code)
    # Remove visibility and other modifiers
    code = re.sub(r'\b(public|private|protected|readonly|static|async)\b\s+', '', code)
    return code

# Configures environment variables so bundled Frida binaries can be located when running as a PyInstaller executable
def setup_runtime_env():
    if hasattr(sys, '_MEIPASS'):
        mei_path = sys._MEIPASS
        # Frida puts its binaries/helpers in a subdirectory within the package
        # We need to find where _frida.abi3.so and friends are
        frida_dir = os.path.join(mei_path, 'frida')
        
        # Add both to library search paths
        for env_var in ['DYLD_LIBRARY_PATH', 'LD_LIBRARY_PATH']:
            current = os.environ.get(env_var, '')
            paths = [mei_path, frida_dir]
            if current:
                paths.append(current)
            os.environ[env_var] = ':'.join(paths)
        
        # Add to PATH for helper binaries
        current_path = os.environ.get('PATH', '')
        os.environ['PATH'] = f"{mei_path}:{frida_dir}:{current_path}"
        
        logging.info(f"Runtime environment setup. _MEIPASS: {mei_path}")

setup_runtime_env()

# Resolves the absolute path for bundled resources, handling both dev environments and PyInstaller extraction paths
def get_resource_path(relative_path):
    """ Get absolute path to resource, works for dev and for PyInstaller """
    try:
        # PyInstaller creates a temp folder and stores path in _MEIPASS
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.dirname(os.path.abspath(__file__))

    return os.path.join(base_path, relative_path)

# Custom HTTP request handler to process JSON-RPC calls and health checks
class RpcHandler(BaseHTTPRequestHandler):
    
    # Handles GET requests, specifically acting as a health check endpoint on '/ping'
    def do_GET(self):
        if self.path == '/ping':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "pong"}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    # Handles incoming POST requests for the JSON-RPC interface on '/rpc', parsing parameters and returning results
    def do_POST(self):
        if self.path == '/rpc':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            try:
                req = json.loads(post_data.decode('utf-8'))
                method = req.get('method')
                params = req.get('params', {})
                result = self.server.bridge.handle_rpc(method, params)
                res = {"jsonrpc": "2.0", "result": result, "id": req.get("id")}
                try:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps(res).encode('utf-8'))
                except BrokenPipeError:
                    pass
            except Exception as e:
                logging.error(f"Error handling RPC: {e}")
                logging.error(f"Request body: {post_data.decode('utf-8', errors='replace')}")
                traceback.print_exc()
                err_res = {
                    "jsonrpc": "2.0",
                    "error": {"status": "unknown_error", "error_message": str(e)},
                    "id": req.get("id") if 'req' in locals() and isinstance(req, dict) else None
                }
                try:
                    self.send_response(500)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(json.dumps(err_res).encode('utf-8'))
                except BrokenPipeError:
                    pass
        else:
            self.send_response(404)
            self.end_headers()

    # Suppresses the default HTTP server logging output to keep the console clean
    def log_message(self, format, *args):
        pass

    # Overrides the default handle method to gracefully ignore BrokenPipeErrors caused by disconnected clients
    def handle(self):
        try:
            super().handle()
        except BrokenPipeError:
            pass

# Custom logging handler to store recent logs in a circular memory buffer
class LogBufferHandler(logging.Handler):
    # Initializes the handler with a target list and size limit
    def __init__(self, buffer_list, limit=100):
        super().__init__()
        self.buffer_list = buffer_list
        self.limit = limit
    
    # Appends formatted log records to the buffer while respecting the capacity limit
    def emit(self, record):
        try:
            log_entry = self.format(record)
            self.buffer_list.append(log_entry)
            if len(self.buffer_list) > self.limit:
                self.buffer_list.pop(0)
        except Exception:
            self.handleError(record)

# Main bridge class that orchestrates Frida sessions, ADB connections, and Gadget injection
class FridaBridge:
    
    # Initializes the bridge state, threading locks, and progress tracking structures
    def __init__(self, serial=None):
        self.device = None
        self.session = None
        self.script = None
        self.serial = serial
        self._lock = threading.Lock()
        self.gadget_port = 8700
        self.gadget_target = "127.0.0.1"
        self.is_injecting_gadget = False

        # Track the progress of the gadget injection sequence
        self.injection_progress = {
            "steps": [
                {"id": "get_target", "title": "Identify target application", "status": "pending"},
                {"id": "setup_adb", "title": "Configure ADB port forwards", "status": "pending"},
                {"id": "check_gadget", "title": "Verify Frida Gadget availability", "status": "pending"},
                {"id": "push_gadget", "title": "Push Gadget library to device", "status": "pending"},
                {"id": "inject_jdwp", "title": "Trigger JDWP gadget injection", "status": "pending"},
                {"id": "load_agent", "title": "Load Frida instrumentation agent", "status": "pending"}
            ],
            "error_message": None
        }

        # Buffer to store the last N logs for the TUI to fetch
        self.log_buffer = []
        handler = LogBufferHandler(self.log_buffer)
        handler.setFormatter(logging.Formatter('%(message)s'))
        logging.getLogger().addHandler(handler)

    # Helper to update the status of a specific injection step
    def _update_progress(self, step_id, status, error=None):
        for s in self.injection_progress["steps"]:
            if s["id"] == step_id:
                s["status"] = status
                if error:
                    self.injection_progress["error_message"] = error
                break

    # Connects to the appropriate Frida device object via USB or specific ADB serial
    def _get_device(self):
        try:
            if self.serial:
                return frida.get_device(self.serial, timeout=60)
            else:
                return frida.get_usb_device(timeout=60)
        except Exception as e:
            raise Exception(f"Failed to find device: {e}")

    # Retrieves the frontmost application directly using the Frida API
    def _get_front_app(self, device):
        return device.get_frontmost_application()

    # Fallback method to discover the frontmost application's package name using ADB shell dumpsys
    def _get_front_app_using_adb(self):
        logging.info("[_get_front_app_using_adb] Failed to get frontmost app after retries, attempting fallback via adb...")
        try:
            adb_cmd = ["adb"]
            if self.serial:
                adb_cmd.extend(["-s", self.serial])
            adb_cmd.extend(["shell", "dumpsys", "window", "|", "grep", "-E", "\"mCurrentFocus\"", "|", "xargs", "|", "cut", "-d' '", "-f3", "|", "cut", "-d'/'", "-f1"])
            result = subprocess.run(adb_cmd, shell=True, capture_output=True, text=True)
            if result.returncode == 0:
                package_name = result.stdout.strip()
                logging.info(f"[_get_front_app_using_adb] Fallback got frontmost package: {package_name}")
                return package_name
            else:
                logging.warning(f"[_get_front_app_using_adb] ADB fallback failed: {result.stderr}")
        except Exception as e:
            logging.warning(f"ADB fallback exception: {e}")

    # Fallback method to get the PID of a given package name using ADB shell pidof
    def _get_front_app_pid_using_adb(self, package_name):
        adb_cmd = ["adb"]
        if self.serial:
            adb_cmd.extend(["-s", self.serial])
        adb_cmd.extend(["shell", "pidof", package_name])
        result = subprocess.run(" ".join(adb_cmd), shell=True, capture_output=True, text=True)
        if result.returncode == 0:
            pid = int(result.stdout.strip())
            logging.info(f"[_get_front_app_pid_using_adb] Got PID {pid} for package {package_name} via adb fallback")
            return pid
        else:
            raise Exception(f"[_get_front_app_pid_using_adb] Could not get pid of frontmost app: {result}")

    # Orchestrates the retrieval of the target app's PID and package, trying Frida first, then falling back to ADB
    def _get_application_pid_and_package(self):
        try:
            device = self._get_device()
            front_app = self._get_front_app(device)
            if front_app:
                logging.info(f"[_get_application_pid_and_package] Got frontmost app: {front_app.identifier} (PID: {front_app.pid})")
                return front_app.pid, front_app.identifier
        except Exception as e:
            logging.warning(f"[_get_application_pid_and_package] Error getting frontmost app: {e}")
        package_name = self._get_front_app_using_adb()
        pid = self._get_front_app_pid_using_adb(package_name)
        return pid, package_name

    # Checks if the Frida Gadget library is on the device, downloads it if missing based on architecture, and pushes it via ADB
    def _pushGadget(self):
        adb_base = ["adb"]
        if self.serial:
            adb_base.extend(["-s", self.serial])

        gadgetCheck = subprocess.run(
            adb_base + ["shell", "ls", "/data/local/tmp/frida-gadget.so"],
            capture_output=True, text=True
        )
        configCheck = subprocess.run(
            adb_base + ["shell", "ls", "/data/local/tmp/frida-gadget.config"],
            capture_output=True, text=True
        )


        if gadgetCheck.returncode == 0 and configCheck.returncode == 0:
            # Check if frida-gadget.so on device is a valid ELF
            magicCheck = subprocess.run(
                adb_base + ["shell", "dd", "if=/data/local/tmp/frida-gadget.so", "bs=1", "count=4", "2>/dev/null"],
                capture_output=True
            ).stdout
            if magicCheck == b'\x7fELF':
                logging.info("[_pushGadget] frida-gadget.so and frida-gadget.config already on device and valid, skipping push")
                return { "status": "ok" }
            else:
                logging.warning("[_pushGadget] frida-gadget.so on device is invalid (likely compressed), forcing push...")

        logging.info("[_pushGadget] frida-gadget.so or frida-gadget.config not found or invalid on device, downloading and pushing...")

        device_arch_raw = subprocess.run(
            adb_base + ["shell", "uname", "-m"],
            capture_output=True, text=True
        ).stdout.strip().lower()

        if "aarch64" in device_arch_raw:
            device_arch_parsed = "arm64"
        elif "arm" in device_arch_raw:
            device_arch_parsed = "arm"
        elif "x86_64" in device_arch_raw:
            device_arch_parsed = "x86_64"
        elif "i686" in device_arch_raw or "i386" in device_arch_raw:
            device_arch_parsed = "x86"
        else:
            device_arch_parsed = "unknown"

        logging.info(f"[_pushGadget] Device architecture: {device_arch_parsed} (raw: {device_arch_raw})")

        cache_dir = os.path.expanduser("~/.cache/barbatos")
        os.makedirs(cache_dir, exist_ok=True)
        gadget_path = os.path.join(cache_dir, f"frida-gadget-{device_arch_parsed}.so")

        # If local gadget exists, check if it's a valid ELF
        if os.path.exists(gadget_path):
            with open(gadget_path, 'rb') as f:
                if f.read(4) != b'\x7fELF':
                    logging.info(f"[_pushGadget] Local {gadget_path} is invalid (compressed?), deleting...")
                    os.remove(gadget_path)

        if not os.path.exists(gadget_path):
            frida_version = "17.9.1"  # Update this version as needed
            download_url = f"https://github.com/frida/frida/releases/download/{frida_version}/frida-gadget-{frida_version}-android-{device_arch_parsed}.so.xz"
            gadget_path_xz = gadget_path + ".xz"

            logging.info(f"[_pushGadget] Downloading Frida gadget from {download_url}...")
            try:
                import urllib.request
                urllib.request.urlretrieve(download_url, gadget_path_xz)
                logging.info(f"[_pushGadget] Frida gadget downloaded to {gadget_path_xz}")
                
                logging.info(f"[_pushGadget] Decompressing {gadget_path_xz} to {gadget_path}...")
                with lzma.open(gadget_path_xz) as f_in:
                    with open(gadget_path, 'wb') as f_out:
                        shutil.copyfileobj(f_in, f_out)
                os.remove(gadget_path_xz)
                logging.info(f"[_pushGadget] Decompression complete.")
            except Exception as e:
                if os.path.exists(gadget_path_xz): os.remove(gadget_path_xz)
                raise Exception(f"[_pushGadget] Failed to download or decompress Frida gadget: {e}")
        
        # adb push to device
        logging.info(f"[_pushGadget] Pushing {gadget_path} to device...")
        r = subprocess.run(
            adb_base + ["push", gadget_path, "/data/local/tmp/frida-gadget.so"],
            capture_output=True, text=True
        )
        if r.returncode != 0:
            raise Exception(f"[_pushGadget] adb push failed: {r.stderr}")
        
        # Ensure correct permissions
        subprocess.run(adb_base + ["shell", "chmod", "755", "/data/local/tmp/frida-gadget.so"], capture_output=True)
        
        logging.info(f"[_pushGadget] adb push ok: {r.stdout.strip()}")

    # Clears current Frida sessions and sets up the initial ADB port forward required for JDWP communication
    def _prepare_gadget(self, pid):
        self._detach_frida()

        logging.info(f"[_prepare_gadget] Setting up adb forward tcp:{self.gadget_port} jdwp:{pid}")
        adb_cmd = ["adb"]
        if self.serial:
            adb_cmd.extend(["-s", self.serial])
        adb_cmd.extend(["forward", f"tcp:{self.gadget_port}", f"jdwp:{pid}"])

        r = subprocess.run(
            adb_cmd,
            capture_output=True, text=True
        )
        if r.returncode != 0:
            raise Exception(f"[_prepare_gadget] adb forward failed: {r.stderr}")

        logging.info(f"[_prepare_gadget] adb forward ok, waiting for JDWP to initialize...")

    # Utility method to identify the foreground app's package name and PID directly via ADB commands
    def _get_front_package_and_pid(self):
            """Descobre o package e o PID do app em primeiro plano via ADB."""
            adb_cmd = ["adb"]
            if self.serial:
                adb_cmd.extend(["-s", self.serial])

            logging.info("[inject] Getting frontmost package and PID...")
            pkg_cmd = adb_cmd.copy() + ["shell", "dumpsys", "window", "|", "grep", "-E", "\"mCurrentFocus\"", "|", "xargs", "|", "cut", "-d' '", "-f3", "|", "cut", "-d'/'", "-f1"]
            r_pkg = subprocess.run(" ".join(pkg_cmd), shell=True, capture_output=True, text=True)
            if r_pkg.returncode != 0:
                raise Exception(f"Failed to get frontmost package: {r_pkg.stderr}")
            
            package_name = r_pkg.stdout.strip()

            pid_cmd = adb_cmd.copy() + ["shell", "pidof", package_name]
            r_pid = subprocess.run(" ".join(pid_cmd), shell=True, capture_output=True, text=True)
            if r_pid.returncode != 0:
                raise Exception(f"Failed to get PID for {package_name}: {r_pid.stderr}")
            
            pid = r_pid.stdout.strip()
            logging.info(f"[inject] Target: {package_name} (PID: {pid})")
            return package_name, pid

    # Idempotent helper to map local TCP ports to the device's JDWP and Gadget ports only if not already mapped
    def _setup_forwards_if_needed(self, pid):
        """Only recreates ADB forwards if they don't exist for the current PID."""
        adb_cmd = ["adb"]
        if self.serial:
            adb_cmd.extend(["-s", self.serial])

        # Check current state
        r_list = subprocess.run(adb_cmd + ["forward", "--list"], capture_output=True, text=True)
        current_forwards = r_list.stdout.strip()

        expected_jdwp = f"tcp:{self.gadget_port} jdwp:{pid}"
        expected_gadget = "tcp:27042 tcp:27042"

        if expected_jdwp in current_forwards and expected_gadget in current_forwards:
            logging.info("[inject] ADB forwards are already set up correctly. Skipping.")
            return True

        logging.info("[inject] Setting up new ADB forwards...")
        subprocess.run(adb_cmd + ["forward", "--remove-all"], capture_output=True)

        r_jdwp = subprocess.run(adb_cmd + ["forward", f"tcp:{self.gadget_port}", f"jdwp:{pid}"], capture_output=True, text=True)
        if r_jdwp.returncode != 0:
            raise Exception(f"Failed to set JDWP forward: {r_jdwp.stderr}")

        r_gadget = subprocess.run(adb_cmd + ["forward", "tcp:27042", "tcp:27042"], capture_output=True, text=True)
        if r_gadget.returncode != 0:
            raise Exception(f"Failed to set Gadget forward: {r_gadget.stderr}")
        
        return False

    # Performs a silent TCP socket check on localhost to verify if the Gadget is already listening
    def _is_gadget_listening(self):
        """Perform a silent 'ping' on the TCP port to see if the Gadget is already running."""
        import socket
        try:
            with socket.create_connection(('127.0.0.1', 27042), timeout=1):
                return True
        except OSError:
            return False

    # Establishes connection to the remote Frida Gadget TCP server and conditionally loads the JavaScript agent
    def _connect_and_load_agent(self, pid):
        """Connects the Frida session and loads the agent only if necessary."""
        logging.info(f"[inject] Connecting to remote Gadget at 127.0.0.1:27042... (target PID: {pid})")
        
        # Retry logic for attachment as the Gadget server might be finishing its startup
        max_retries = 10
        last_err = None
        
        for i in range(max_retries):
            try:
                # First check if the TCP port is actually open
                if not self._is_gadget_listening():
                    logging.info(f"[inject] Gadget port not open yet (attempt {i+1}/{max_retries})...")
                    time.sleep(2)
                    continue

                time.sleep(2)
                
                logging.info(f"[inject] Attachment attempt {i+1}/{max_retries}...")
                
                device_manager = frida.get_device_manager()
                # Ensure we have a fresh handle to the remote device
                self.device = device_manager.add_remote_device('127.0.0.1:27042')
                logging.info(f"[inject] Remote device added: {self.device}")
                
                self.session = self.device.attach("Gadget")
                self.session._pid = int(pid)
                last_err = None
                logging.info(f"[inject] Attached to Gadget successfully, session_pid: {self.session._pid}")
                break
            except Exception as e:
                last_err = e
                logging.warning(f"[inject] Attachment attempt {i+1} failed: {e}")
                # Log traceback for deeper investigation if it's not a simple connection refused
                if "connection closed" in str(e).lower() or "timeout" in str(e).lower():
                    logging.debug(traceback.format_exc())
                time.sleep(2)
        
        if last_err:
            logging.error(f"[inject] All attachment attempts failed: {last_err}")
            raise Exception(f"Failed to attach to Frida Gadget after {max_retries} attempts: {last_err}")

        # Prevents reloading the JS if it's already injected and active in this session
        if self.script and not getattr(self.script, "is_destroyed", False):
            logging.info("[inject] Agent is already loaded. Skipping JS injection.")
            return

        logging.info("[inject] Loading Frida agent bundle...")
        try:
            agent_path = get_resource_path('agent.bundle.js')
            with open(agent_path, 'r', encoding='utf-8') as f:
                source = f.read()

            self.script = self.session.create_script(source)
            self.script.load()
            logging.info("[inject] Agent loaded successfully.")
        except Exception as e:
            logging.error(f"[inject] Failed to load script: {e}")
            raise e

    # =================================================================
    # ROOT PATH HELPERS
    # =================================================================

    def _is_device_rooted(self):
        """Check if the device is rooted by running 'which su' via ADB."""
        adb_cmd = ["adb"]
        if self.serial:
            adb_cmd.extend(["-s", self.serial])
        result = subprocess.run(
            adb_cmd + ["shell", "which", "su"],
            capture_output=True, text=True
        )
        is_rooted = result.returncode == 0 and result.stdout.strip() != ""
        logging.info(f"[root] Device rooted: {is_rooted} (su path: {result.stdout.strip()!r})")
        return is_rooted

    def _is_app_debuggable(self, package_name):
        """Check if the target app has debuggable=true in its manifest."""
        adb_cmd = ["adb"]
        if self.serial:
            adb_cmd.extend(["-s", self.serial])
        result = subprocess.run(
            " ".join(adb_cmd + ["shell", "dumpsys", "package", package_name, "|", "grep", "-i", "debuggable"]),
            shell=True, capture_output=True, text=True
        )
        is_debuggable = "true" in result.stdout.lower()
        logging.info(f"[root] App '{package_name}' debuggable: {is_debuggable}")
        return is_debuggable

    def _ensure_frida_server_binary(self):
        """Download frida-server matching the current frida version and push it to the device."""
        adb_base = ["adb"]
        if self.serial:
            adb_base.extend(["-s", self.serial])

        device_arch_raw = subprocess.run(
            adb_base + ["shell", "uname", "-m"],
            capture_output=True, text=True
        ).stdout.strip().lower()

        if "aarch64" in device_arch_raw:
            device_arch = "arm64"
        elif "arm" in device_arch_raw:
            device_arch = "arm"
        elif "x86_64" in device_arch_raw:
            device_arch = "x86_64"
        elif "i686" in device_arch_raw or "i386" in device_arch_raw:
            device_arch = "x86"
        else:
            device_arch = "unknown"

        frida_version = frida.__version__
        logging.info(f"[frida-server] Version: {frida_version}, arch: {device_arch}")

        cache_dir = os.path.expanduser("~/.cache/barbatos")
        os.makedirs(cache_dir, exist_ok=True)
        server_local = os.path.join(cache_dir, f"frida-server-{frida_version}-android-{device_arch}")

        if not os.path.exists(server_local) or os.path.getsize(server_local) == 0:
            download_url = (
                f"https://github.com/frida/frida/releases/download/{frida_version}/"
                f"frida-server-{frida_version}-android-{device_arch}.xz"
            )
            server_local_xz = server_local + ".xz"
            logging.info(f"[frida-server] Downloading from {download_url}...")
            try:
                import urllib.request
                urllib.request.urlretrieve(download_url, server_local_xz)
                with lzma.open(server_local_xz) as f_in:
                    with open(server_local, 'wb') as f_out:
                        shutil.copyfileobj(f_in, f_out)
                os.remove(server_local_xz)
                logging.info(f"[frida-server] Downloaded and extracted.")
            except Exception as e:
                if os.path.exists(server_local_xz):
                    os.remove(server_local_xz)
                raise Exception(f"[frida-server] Failed to download: {e}")

        remote_path = "/data/local/tmp/barbatos-server"
        logging.info(f"[frida-server] Pushing binary to {remote_path}...")
        r = subprocess.run(
            adb_base + ["push", server_local, remote_path],
            capture_output=True, text=True
        )
        if r.returncode != 0:
            raise Exception(f"[frida-server] adb push failed: {r.stderr}")
        subprocess.run(adb_base + ["shell", "chmod", "755", remote_path], capture_output=True)
        logging.info(f"[frida-server] Binary pushed and chmod done.")

    def _start_frida_server(self):
        """Kill any running instance and start frida-server as root via su -c."""
        adb_base = ["adb"]
        if self.serial:
            adb_base.extend(["-s", self.serial])

        remote_path = "/data/local/tmp/barbatos-server"

        # Kill existing instances
        subprocess.run(
            " ".join(adb_base + ["shell", "su", "-c",
                                  "'pkill -f barbatos-server 2>/dev/null; pkill -f frida-server 2>/dev/null; true'"]),
            shell=True, capture_output=True
        )
        time.sleep(1)

        logging.info(f"[frida-server] Starting as root...")
        subprocess.Popen(
            adb_base + ["shell", "su", "-c", f"{remote_path}"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        time.sleep(3)
        logging.info(f"[frida-server] Started.")

    def _load_agent_via_frida_server(self, pid):
        """Attach to the target PID via USB (frida-server) and load the JS agent."""
        logging.info(f"[root] Attaching to PID {pid} via USB frida-server...")
        device = self._get_device()
        self.session = device.attach(int(pid))
        self.session._pid = int(pid)
        logging.info(f"[root] Attached to PID {pid}.")

        agent_path = get_resource_path('agent.bundle.js')
        with open(agent_path, 'r', encoding='utf-8') as f:
            source = f.read()

        self.script = self.session.create_script(source)
        self.script.load()
        logging.info("[root] Agent loaded via frida-server.")

    # =================================================================
    # MAIN ORCHESTRATOR FUNCTION
    # =================================================================

    # Main orchestrator function that safely executes the entire JDWP gadget injection lifecycle
    def inject_gadget_from_scratch(self):
        with self._lock:
            if getattr(self, "_in_actual_injection", False):
                logging.info("Already in actual injection process, skipping redundant call.")
                return
            self._in_actual_injection = True
            self.is_injecting_gadget = True

        try:
            # Reset progress state
            for s in self.injection_progress["steps"]:
                s["status"] = "pending"
            self.injection_progress["error_message"] = None

            logging.info("\n=== [ STARTING INJECTION SEQUENCE ] ===")

            # 1. Identify Target
            self._update_progress("get_target", "running")
            package_name, pid = self._get_front_package_and_pid()
            self._update_progress("get_target", "completed")

            # Detect environment to pick the right injection path
            is_rooted = self._is_device_rooted()
            is_debuggable = self._is_app_debuggable(package_name)

            if is_rooted and not is_debuggable:
                # ── ROOT PATH ────────────────────────────────────────────────
                logging.info("[*] Root device + non-debuggable app → Root Path (frida-server).")
                self.injection_progress["steps"] = [
                    {"id": "get_target",     "title": "Identify target application",      "status": "completed"},
                    {"id": "prepare_server", "title": "Prepare frida-server on device",   "status": "pending"},
                    {"id": "start_server",   "title": "Start frida-server as root",       "status": "pending"},
                    {"id": "load_agent",     "title": "Attach to process and load agent", "status": "pending"},
                ]

                self._update_progress("prepare_server", "running")
                self._ensure_frida_server_binary()
                self._update_progress("prepare_server", "completed")

                self._update_progress("start_server", "running")
                self._start_frida_server()
                self._update_progress("start_server", "completed")

                self._update_progress("load_agent", "running")
                self._load_agent_via_frida_server(pid)
                self._update_progress("load_agent", "completed")

            elif not is_debuggable and not is_rooted:
                raise Exception(
                    f"App '{package_name}' is not debuggable and the device is not rooted. "
                    "Cannot inject: enable developer options on the device or use a rooted device."
                )

            else:
                # ── GADGET PATH (existing logic) ─────────────────────────────
                logging.info("[*] Debuggable app detected → Gadget Path (JDWP).")

                # 2. Configure Forwards
                self._update_progress("setup_adb", "running")
                skipped_adb = self._setup_forwards_if_needed(pid)
                self._update_progress("setup_adb", "skipped" if skipped_adb else "completed")

                # 3. Check if already listening
                self._update_progress("check_gadget", "running")
                is_listening = self._is_gadget_listening()
                if is_listening:
                    logging.info("[*] Port 27042 is open. Testing if we can attach...")
                    try:
                        device_manager = frida.get_device_manager()
                        temp_device = device_manager.add_remote_device('127.0.0.1:27042')
                        temp_session = temp_device.attach("Gadget")
                        temp_session.detach()
                        logging.info("[+] Gadget is healthy and attachable.")
                    except Exception as e:
                        logging.warning(f"[-] Port 27042 open but Gadget not responding: {e}")
                        logging.warning("    Will proceed with JDWP injection.")
                        is_listening = False

                self._update_progress("check_gadget", "completed")

                if not is_listening:
                    logging.info("[*] Gadget not detected. Proceeding with JDWP injection...")

                    self._update_progress("push_gadget", "running")
                    self._pushGadget()
                    self._update_progress("push_gadget", "completed")

                    self._update_progress("inject_jdwp", "running")
                    adb_clear_cmd = f"adb {'-s ' + self.serial if self.serial else ''} shell am clear-debug-app"
                    subprocess.run(adb_clear_cmd, shell=True, capture_output=True)
                    self._inject_with_retry(package_name)
                    time.sleep(2)
                    self._update_progress("inject_jdwp", "completed")
                else:
                    logging.info("[*] Gadget already listening. Skipping JDWP injection.")
                    self._update_progress("push_gadget", "skipped")
                    self._update_progress("inject_jdwp", "skipped")

                # 6. Load Agent
                self._update_progress("load_agent", "running")
                self._connect_and_load_agent(pid)
                self._update_progress("load_agent", "completed")

            logging.info("=== [ INJECTION SEQUENCE COMPLETED ] ===\n")

        except Exception as e:
            logging.error(f"Injection failed: {e}")
            # Mark current running step as error
            for s in self.injection_progress["steps"]:
                if s["status"] == "running":
                    s["status"] = "error"
                    break
            self.injection_progress["error_message"] = str(e)
        finally:
            with self._lock:
                self._in_actual_injection = False
                self.is_injecting_gadget = False

    # Returns the active Frida session, creating a new attachment and script load if the current one is invalid
    def get_session(self):
        # Wait OUTSIDE the lock — inject_gadget_from_scratch's finally block also acquires
        # self._lock to clear is_injecting_gadget, so holding it here causes a deadlock.
        while self.is_injecting_gadget:
            logging.info("Waiting for gadget injection to complete...")
            time.sleep(1)
        with self._lock:
            device = self._get_device()
            front_app = self._get_front_app(device)
            if not front_app:
                raise Exception("No frontmost application found on device")
            
            if self.session:
                try:
                    if not self.session.is_detached and getattr(self.session, "_pid", None) == front_app.pid:
                        logging.debug(f"[get_session] Reusing existing session (script id={id(self.script)})")
                        return self.session
                except Exception as e:
                    logging.info(f"[get_session] Existing session invalid ({e}), re-attaching")

            logging.info(f"[get_session] Attaching to {front_app.identifier} (PID: {front_app.pid})")
            self.session = device.attach(front_app.pid)
            self.session._pid = front_app.pid

            logging.info("[get_session] Successfully attached to process, now loading Frida agent...")
            agent_path = get_resource_path('agent.bundle.js')
            logging.info(f"[get_session] Loading Frida agent from: {agent_path}")
            
            with open(agent_path, 'r', encoding='utf-8') as f:
                source = f.read()

            self.script = self.session.create_script(source)
            self.script.load()

            return self.session

    # Gracefully disconnects the current Frida session and unloads the loaded script
    def _detach_frida(self):
        if self.session and not self.session.is_detached:
            logging.info("Detaching Frida session...")
            try:
                self.session.detach()
            except Exception as e:
                logging.warning(f"Error detaching: {e}")
            self.session = None
            self.script = None

    # Forces the bridge to clear the existing connection state and reconnect to the target application
    def _reattach_frida(self):
        logging.info("Re-attaching Frida session...")
        try:
            self.get_session()
            logging.info("Frida re-attached successfully")
        except Exception as e:
            logging.warning(f"Failed to re-attach Frida: {e}")

    # Executes the external JDWP exploitation script to load the injected payload library
    def _run_jdwp(self, cmd=None, break_on="android.os.Handler.dispatchMessage", package_name=None):
        return run_jdwp(
            target=self.gadget_target,
            port=self.gadget_port,
            cmd=cmd,
            break_on=break_on,
            package_name=package_name,
            serial=self.serial
        )

    # Force closes and subsequently restarts the target Android application using ADB monkey events
    def _force_restart_app(self, package_name):
        adb_base = ["adb"]
        if self.serial:
            adb_base.extend(["-s", self.serial])
            
        logging.info(f"[*] Forcing stop of app: {package_name}")
        
        subprocess.run(
            adb_base + ["shell", "am", "force-stop", package_name],
            capture_output=True
        )
        
        time.sleep(1) 
        
        logging.info(f"[*] Restarting app: {package_name}")
        
        r_start = subprocess.run(
            adb_base + ["shell", "monkey", "-p", package_name, "-c", "android.intent.category.LAUNCHER", "1"],
            capture_output=True, text=True
        )
        
        if r_start.returncode != 0 or "monkey aborted" in r_start.stderr.lower():
            raise Exception(f"[-] Failed to restart the app: {r_start.stderr}")
            
        logging.info("[+] App restarted successfully, waiting for it to come up...")
        time.sleep(3)

    # Attempts JDWP injection and applies an automatic app-restart fallback if the JDWP port is busy/locked
    def _inject_with_retry(self, package_name):
        try:
            result = self._run_jdwp(package_name=package_name)
        except Exception as e:
            pass
        try:
            if result.get("status") == "unknown_error" and "Failed to handshake" in result.get("error_message", ""):
                self._force_restart_app(package_name)
                
                new_pid = self._get_front_app_pid_using_adb(package_name)
                
                adb_cmd = ["adb"]
                if self.serial:
                    adb_cmd.extend(["-s", self.serial])
                
                subprocess.run(" ".join(adb_cmd + ["forward", f"tcp:{self.gadget_port}", f"jdwp:{new_pid}"]), shell=True, capture_output=True)
                subprocess.run(" ".join(adb_cmd + ["forward", "tcp:27042", "tcp:27042"]), shell=True, capture_output=True)
                
                result = self._run_jdwp(package_name=package_name)
                
            if result.get("status") not in ["completed", "gadget_detected"]:
                raise Exception(f"JDWP injection failed: {result}")        
        except Exception as e:
            raise e

    # RPC endpoint: Retrieves loaded Java classes with a custom sorting heuristic based on target package
    def list_classes(self, search_param="", app_package="", offset=0, limit=200):
        self.get_session()
        classes = self.script.exports_sync.listclasses(search_param)
        
        def get_priority(class_name):
            priority = 0
            if app_package:
                parts = app_package.split(".")
                first_two = ".".join(parts[:2]) if len(parts) >= 2 else ""
                
                if class_name.startswith(f"{app_package}.") or class_name == app_package:
                    priority = 3
                elif first_two and (class_name.startswith(f"{first_two}.") or class_name == first_two):
                    priority = 2
                else:
                    priority = 1
            else:
                priority = 1
                
            if "[" in class_name:
                priority = -1
                
            if search_param and priority >= 0:
                simple_name = class_name.split(".")[-1]
                if simple_name.lower().startswith(search_param.lower()) or class_name.lower().startswith(search_param.lower()):
                    priority += 10
                    
            return priority
            
        classes.sort(key=lambda c: (-get_priority(c), c))
        return classes[offset:offset+limit]

    # RPC endpoint: Counts the number of active instances of a specified Java class
    def count_instances(self, class_name):
        self.get_session()
        count = self.script.exports_sync.countinstances(class_name)
        if count == -1:
            raise Exception(f"Failed to count instances for {class_name}")
        return count

    # Resets the injection progress state to allow for a clean retry
    def reset_injection(self):
        with self._lock:
            if self.is_injecting_gadget:
                return False
            for s in self.injection_progress["steps"]:
                s["status"] = "pending"
            self.injection_progress["error_message"] = None
            logging.info("[reset] Injection state reset.")
            return True

    # Routing logic mapping string method names from the JSON-RPC payload to their internal Python implementations
    def handle_rpc(self, method, params):
        if method == "listClasses":
            return self.list_classes(
                search_param=params.get("search_param", ""),
                app_package=params.get("app_package", ""),
                offset=params.get("offset", 0),
                limit=params.get("limit", 200)
            )

        elif method == "inspectClass":
            self.get_session()
            return self.script.exports_sync.inspectclass(params.get("className", ""))

        elif method == "countInstances":
            return self.count_instances(params.get("className", ""))

        elif method == "listInstances":
            self.get_session()
            return self.script.exports_sync.listinstances(params.get("className", ""))

        elif method == "inspectInstance":
            self.get_session()
            return self.script.exports_sync.inspectinstance(
                params.get("className", ""), 
                params.get("id", ""),
                params.get("offset", 0),
                params.get("limit", 50)
            )

        elif method == "setFieldValue":
            self.get_session()
            return self.script.exports_sync.setfieldvalue(
                params.get("className", ""), 
                params.get("id", ""), 
                params.get("fieldName", ""), 
                params.get("type", ""), 
                params.get("newValue", "")
            )

        elif method == "prepareEnvironment":
            pid, package_name = self._get_application_pid_and_package()
            self._prepare_gadget(pid)

            return {
                "pid": pid,
                "package_name": package_name,
                "port": self.gadget_port,
                "target": self.gadget_target
            }

        elif method == "checkOrPushGadget":
            self._pushGadget()

            return { "status": "ok" }

        elif method == "resetInjection":
            if self.reset_injection():
                return { "status": "ok" }
            else:
                return { "status": "error", "error_message": "Cannot reset while injection is in progress" }

        elif method == "injectGadgetFromScratch":
            # Initiates the background injection thread if not already active and NOT in a terminal state
            with self._lock:
                # A state is terminal if we have an error or if all steps are no longer 'pending' and we're not 'running'
                is_terminal = self.injection_progress["error_message"] is not None or \
                              (all(s["status"] != "pending" for s in self.injection_progress["steps"]))
                
                if not self.is_injecting_gadget and (not is_terminal or params.get("force")):
                    logging.info("[rpc] Starting background injection thread...")
                    self.is_injecting_gadget = True
                    threading.Thread(target=self.inject_gadget_from_scratch, daemon=True).start()

            # Determine global status for the response
            if self.is_injecting_gadget:
                status = "running"
            elif self.injection_progress["error_message"]:
                status = "error"
            else:
                status = "completed"

            res = {
                "status": status,
                "steps": self.injection_progress["steps"],
                "error_message": self.injection_progress["error_message"]
            }
            if params.get("with_logs"):
                limit = int(params.get("limit", 50))
                res["logs"] = self.log_buffer[-limit:]
            return res

        elif method == "injectJdwp":

            result = self._run_jdwp(
                cmd=params.get("cmd"),
                break_on=params.get("break_on", "android.os.Handler.dispatchMessage"),
                package_name=params.get("package_name"),
            )
            # run_jdwp succeeded, re-attach frida for subsequent operations
            try:
                self._reattach_frida()
            except Exception as reattach_err:
                raise Exception(f"unable to re-attach to frida-server: {reattach_err}")

            return result

        elif method == "getpackagename":
            self.get_session()
            return self.script.exports_sync.getpackagename()

        elif method == "hookMethod":
            self.get_session()
            return self.script.exports_sync.hookmethod(params.get("className"), params.get("methodSig"))
        
        elif method == "unhookMethod":
            self.get_session()
            return self.script.exports_sync.unhookmethod(params.get("className"), params.get("methodSig"))

        elif method == "setMethodImplementation":
            self.get_session()
            code = params.get("code", "")
            # Transpile TS-like code to plain JS
            transpiled_code = strip_ts_types(code)
            return self.script.exports_sync.setmethodimplementation(
                params.get("className"), 
                params.get("methodSig"), 
                transpiled_code
            )

        elif method == "getHookEvents":
            self.get_session()
            script_id = id(self.script)
            events = self.script.exports_sync.gethookevents()
            if events:
                logging.info(f"[getHookEvents] script id={script_id}, returning {len(events)} events")
            return events

        elif method == "getInstanceAddresses":
            self.get_session()
            return self.script.exports_sync.getinstanceaddresses(params.get("className", ""))

        elif method == "runOnce":
            self.get_session()
            code = params.get("code", "")
            transpiled_code = strip_ts_types(code)
            script_id = id(self.script)
            logging.info(f"[runOnce] script id={script_id}, class={params.get('className')}, method={params.get('methodSig')}")
            result = self.script.exports_sync.runonce(
                params.get("className"),
                params.get("methodSig"),
                transpiled_code
            )
            logging.info(f"[runOnce] returned: {result}, script id still={id(self.script)}")
            return result

        else:
            raise Exception(f"Method {method} not found")

# Bootstraps and starts the JSON-RPC local HTTP server blocking the main thread
def run_server(port=8080, serial=None):
    server = ThreadingHTTPServer(('127.0.0.1', port), RpcHandler)
    server.bridge = FridaBridge(serial=serial)
    logging.info(f"[run_server] Starting JSON-RPC Bridge on http://127.0.0.1:{port}...")
    if serial:
        logging.info(f"[run_server] Targeting ADB serial: {serial}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    server.server_close()
    logging.info("Server stopped.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='barbatos JSON-RPC Bridge')
    parser.add_argument('--port', type=int, default=8080, help='Listen port (default: 8080)')
    parser.add_argument('--serial', type=str, help='Target ADB device serial number')
    args = parser.parse_args()
    
    run_server(port=args.port, serial=args.serial)
