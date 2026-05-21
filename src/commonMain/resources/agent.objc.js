import ObjC from "frida-objc-bridge";
import Swift from "frida-swift-bridge";

var hookEvents = [];
var activeHookImplementations = {};
var instanceCache = {};

rpc.exports = {
    getpackagename: function() {
        var bundle = ObjC.classes.NSBundle.mainBundle();
        var identifier = bundle.bundleIdentifier();
        return identifier ? identifier.toString() : "";
    },

    gethookevents: function() {
        var events = hookEvents;
        hookEvents = [];
        return events;
    },

    togglehook: function(className, memberSignature, enabled, implementation) {
        // Basic placeholder for now, as ObjC hooking is more complex (Interceptor.attach)
        // We'll implement a basic hook for ObjC methods if needed later.
        return true;
    },

    hookmethod: function(className, methodSig) {
        // Placeholder for ObjC hooking
        return true;
    },

    unhookmethod: function(className, methodSig) {
        // Placeholder for ObjC unhooking
        return true;
    },

    getinstanceaddresses: function(className) {
        var addresses = [];
        try {
            var clazz = ObjC.classes[className];
            if (clazz) {
                ObjC.chooseSync(clazz).forEach(function(ins) {
                    addresses.push(ins.toString() + " (" + ins.handle.toString() + ")");
                });
            }
        } catch (e) {}
        return addresses.reverse();
    },

    setmethodimplementation: function(className, methodSig, code) {
        // Placeholder for ObjC custom implementation
        activeHookImplementations[className + ":" + methodSig] = code;
        return true;
    },

    runonce: function(className, methodSig, code) {
        var execResult = null;
        var execError = null;

        try {
            var clazz = ObjC.classes[className];
            var allInstances = [];
            if (clazz) {
                ObjC.chooseSync(clazz).forEach(function(ins) {
                    allInstances.push(ins);
                });
            }
            allInstances.reverse(); // newest-first heuristic

            // Prepare wrappers similar to Java version
            var instanceWrappers = allInstances.map(function(inst) {
                return {
                    instance: inst,
                    original: function() {
                        // For ObjC, we try to call the selector directly
                        var selector = methodSig.replace(/^\+ /, "").replace(/^- /, "");
                        if (inst[selector]) {
                            return inst[selector].apply(inst, arguments);
                        }
                        return "Method not found on instance";
                    }
                };
            });

            var context = {
                ObjC: ObjC,
                instances: instanceWrappers,
                log: function(msg) {
                    send({ 
                        type: 'hook-log', 
                        timestamp: Date.now(), 
                        className: className, 
                        methodSig: methodSig, 
                        data: { message: String(msg) } 
                    });
                }
            };

            var fn = new Function('context', code);
            execResult = fn(context);
            
            return { result: String(execResult), error: null };
        } catch (e) {
            return { result: null, error: e.toString() };
        }
    },

    getmethodimplementation: function(className, methodSig) {
        return activeHookImplementations[className + ":" + methodSig] || "";
    },

    inspectclass: function(className) {
        var methods = [];
        var instanceAttributes = [];
        var staticAttributes = [];

        try {
            var clazz = ObjC.classes[className];
            if (clazz) {
                // 1. Collect Class Methods (+)
                if (Array.isArray(clazz.$methods)) {
                    clazz.$methods.forEach(function(m) {
                        var sig = (m.indexOf('+ ') === 0 || m.indexOf('- ') === 0) ? m : "+ " + m;
                        methods.push(sig);
                    });
                }

                // 2. Collect Instance Methods (-)
                if (clazz.prototype && Array.isArray(clazz.prototype.$methods)) {
                    clazz.prototype.$methods.forEach(function(m) {
                        var sig = (m.indexOf('+ ') === 0 || m.indexOf('- ') === 0) ? m : "- " + m;
                        methods.push(sig);
                    });
                }

                // 3. Ivars as Instance Attributes (Real data fields)
                for (var iv in clazz.$ivars) {
                    instanceAttributes.push(iv);
                }
            }
            // Sort for better UX
            methods.sort();
            instanceAttributes.sort();
            
            return { staticAttributes: staticAttributes, instanceAttributes: instanceAttributes, methods: methods };
        } catch (e) {
            return { error: e.toString(), staticAttributes: [], instanceAttributes: [], methods: [] };
        }
    },

    listinstances: function(className) {
        var instances = [];
        try {
            var clazz = ObjC.classes[className];
            if (clazz) {
                ObjC.chooseSync(clazz).forEach(function(ins) {
                    var id = ins.handle.toString();
                    instanceCache[id] = ins;
                    instances.push({
                        id: id,
                        handle: id,
                        summary: ins.toString(),
                        detectionMethod: 'choose'
                    });
                });
            }
            return { instances: instances, totalCount: instances.length, detectionMethod: 'choose' };
        } catch (e) {
            return { error: e.toString(), instances: [], totalCount: 0, detectionMethod: 'error' };
        }
    },

    inspectinstance: function(className, id, offset, limit) {
        var attributes = [];
        try {
            var ins = instanceCache[id];
            if (!ins) {
                // Try to find it again if it's a valid handle
                ins = new ObjC.Object(ptr(id));
            }
            
            if (ins) {
                for (var iv in ins.$ivars) {
                    var val = ins.$ivars[iv];
                    attributes.push({
                        name: iv,
                        type: typeof val,
                        value: val ? val.toString() : "nil",
                        isStatic: false
                    });
                }
            }
            return { attributes: attributes };
        } catch (e) {
            return { error: e.toString(), attributes: [] };
        }
    },

    listclassesstream: function(searchParam, streamId) {
        var lowercaseSearch = searchParam ? searchParam.toLowerCase() : "";
        var batch = [];
        var batchSize = 100;
        var seen = new Set();

        function flushBatch() {
            if (batch.length > 0) {
                send({ type: "class_chunk", streamId: streamId, chunk: batch });
                batch = [];
            }
        }

        // Fetch all Objective-C classes
        for (var className in ObjC.classes) {
            if (ObjC.classes.hasOwnProperty(className)) {
                if (!lowercaseSearch || className.toLowerCase().includes(lowercaseSearch)) {
                    if (!seen.has(className)) {
                        seen.add(className);
                        batch.push(className);
                        if (batch.length >= batchSize) flushBatch();
                    }
                }
            }
        }
        
        // Fetch Swift classes
        if (Swift.available) {
            var swiftClasses = Swift.classes;
            for (var swiftName in swiftClasses) {
                if (swiftClasses.hasOwnProperty(swiftName)) {
                    if (!lowercaseSearch || swiftName.toLowerCase().includes(lowercaseSearch)) {
                        if (!seen.has(swiftName)) {
                            seen.add(swiftName);
                            batch.push(swiftName);
                            if (batch.length >= batchSize) flushBatch();
                        }
                    }
                }
            }
        }
        
        flushBatch();
        send({ type: "class_stream_end", streamId: streamId });
    },

    listclasses: async function(searchParam) {
        var lowercaseSearch = searchParam ? searchParam.toLowerCase() : "";
        const fetchObjcClassesPromise = new Promise((resolve) => {
            const classes = [];
            // Fetch all Objective-C classes
            for (var className in ObjC.classes) {
                if (ObjC.classes.hasOwnProperty(className)) {
                    if (!lowercaseSearch || className.toLowerCase().includes(lowercaseSearch)) {
                        classes.push(className);
                    }
                }
            }
            return classes
        });
        const fetchSwiftClassesPromise = new Promise((resolve) => {
            const classes = [];
            
            // Also enumerate pure Swift classes (not exposed to ObjC runtime)
            if (Swift.available) {
                var swiftClasses = Swift.classes;
                for (var swiftName in swiftClasses) {
                    if (swiftClasses.hasOwnProperty(swiftName)) {
                        if (!lowercaseSearch || swiftName.toLowerCase().includes(lowercaseSearch)) {
                            if (classes.indexOf(swiftName) === -1) {
                                classes.push(swiftName);
                            }
                        }
                    }
                }
            }
            return classes
        })
        return Promise.all([fetchObjcClassesPromise, fetchSwiftClassesPromise]).then(results => {
            return results.flat().sort();
        })
    },

    countinstances: function(className) {
        var count = 0;
        try {
            var clazz = ObjC.classes[className];
            if (clazz) {
                // Basic instance counting via choose
                ObjC.chooseSync(clazz).forEach(function(ins) {
                    count++;
                });
            }
        } catch (e) {}
        return count;
    }
};
