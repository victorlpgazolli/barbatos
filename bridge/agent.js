import Java from "frida-java-bridge";

var instanceCache = {};
var hookEvents = [];
var activeHookImplementations = {};
var fieldPollingInterval = null;
var monitoredFields = {};

function javaToString(obj) {
    if (obj === null || obj === undefined) return "null";
    try {
        if (obj.$className) {
            // 1. Handle Enum
            try {
                if (Java.use("java.lang.Enum").class.isInstance(obj)) {
                    return obj.name();
                }
            } catch(e) {}

            // 2. Handle Collection sizes
            try {
                if (Java.use("java.util.Collection").class.isInstance(obj)) {
                    return obj.getClass().getSimpleName() + "(size=" + obj.size() + ")";
                }
                if (Java.use("java.util.Map").class.isInstance(obj)) {
                    return obj.getClass().getSimpleName() + "(size=" + obj.size() + ")";
                }
            } catch(e) {}

            // 3. Handle Arrays
            if (obj.getClass().isArray()) {
                return obj.getClass().getSimpleName() + "[" + Java.use("java.lang.reflect.Array").getLength(obj) + "]";
            }

            var className = obj.getClass().getName();
            var str = "";
            try {
                str = obj.toString();
            } catch (e) {
                str = "[Object@" + obj.hashCode().toString(16) + "]";
            }

            // If it's the default Object.toString() which contains '@' and starts with class name
            if ((str.includes('@') && str.startsWith(className)) || str === "[object Object]") {
                try {
                    var currentClass = obj.getClass();
                    var parts = [];
                    var maxFields = 10;
                    
                    while (currentClass !== null && parts.length < maxFields) {
                        var fields = currentClass.getDeclaredFields();
                        for (var i = 0; i < fields.length && parts.length < maxFields; i++) {
                            var f = fields[i];
                            var modifiers = f.getModifiers();
                            // Skip static fields
                            if (!(modifiers & 0x00000008)) {
                                f.setAccessible(true);
                                var name = f.getName();
                                try {
                                    var val = f.get(obj);
                                    var valStr = "null";
                                    if (val !== null) {
                                        if (val.$className) {
                                            valStr = val.getClass().getSimpleName() + "@" + val.hashCode().toString(16);
                                        } else {
                                            valStr = String(val);
                                        }
                                    }
                                    parts.push(name + "=" + valStr);
                                } catch (e) {}
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                    return className + "{" + parts.join(", ") + (parts.length >= maxFields ? ", ..." : "") + "}";
                } catch (fe) {}
            }

            return str;
        }
        if (typeof obj === 'object') {
            return JSON.stringify(obj);
        }
        return String(obj);
    } catch (e) {
        try {
            return obj.toString();
        } catch (e2) {
            return "[Object]";
        }
    }
}

function getInstanceStatus(instance) {
    var status = "active";
    try {
        Java.perform(function() {
            var className = instance.getClass().getName();
            // Check Activity
            if (className.indexOf("Activity") !== -1 || className.indexOf("Fragment") !== -1) {
                try {
                    if (instance.isDestroyed()) status = "destroyed";
                    else if (instance.isFinishing()) status = "finishing";
                } catch(e) {}
            }
            // Check View
            if (className.indexOf("View") !== -1) {
                try {
                    if (!instance.isAttachedToWindow()) status = "detached";
                } catch(e) {}
            }
        });
    } catch(e) {}
    return status;
}

/**
 * Returns "active" instances of className.
 *
 * Strategy 1 — StateFlow: reads _state$volatile from each StateFlowImpl on the heap.
 *   Instances stuck in coroutine continuations (old values from suspended collectors)
 *   are ignored — they do not represent the current state of the app.
 *
 * Strategy 2 — LiveData: reads the mData field of each LiveData on the heap.
 *
 * Strategy 3 — Fallback: conventional Java.choose (returns all instances,
 *   including those stuck in continuations). Used when className is not exposed
 *   via StateFlow or LiveData.
 *
 * @param {string} className  Full name of the Java class to inspect.
 * @returns {{ instances: Array<{id:string, handle:string, instance:object}>, method: string }}
 */
function resolveActiveInstances(className) {
    var found = {};

    // ── Strategy 1: StateFlow ───────────────────────────────────────────────
    try {
        Java.choose('kotlinx.coroutines.flow.StateFlowImpl', {
            onMatch: function(sf) {
                try {
                    var fieldNames = ['_state$volatile', '_state'];
                    for (var i = 0; i < fieldNames.length; i++) {
                        try {
                            var f = sf.getClass().getDeclaredField(fieldNames[i]);
                            f.setAccessible(true);
                            var val = f.get(sf);
                            if (val !== null && val.getClass().getName() === className) {
                                var id = val.$handle ? val.$handle.toString() : val.hashCode().toString();
                                if (!found[id]) {
                                    found[id] = { id: id, handle: val.$handle ? val.$handle.toString() : "", instance: val };
                                }
                            }
                            break;
                        } catch(fieldErr) {}
                    }
                } catch(e) {}
            },
            onComplete: function() {}
        });
    } catch(e) {}

    if (Object.keys(found).length > 0) {
        return { instances: Object.values(found), method: 'stateflow' };
    }

    // ── Strategy 2: LiveData ────────────────────────────────────────────────
    try {
        Java.choose('androidx.lifecycle.LiveData', {
            onMatch: function(ld) {
                try {
                    var f = ld.getClass().getDeclaredField('mData');
                    f.setAccessible(true);
                    var val = f.get(ld);
                    if (val !== null && val.getClass().getName() === className) {
                        var id = val.$handle ? val.$handle.toString() : val.hashCode().toString();
                        if (!found[id]) {
                            found[id] = { id: id, handle: val.$handle ? val.$handle.toString() : "", instance: val };
                        }
                    }
                } catch(e) {}
            },
            onComplete: function() {}
        });
    } catch(e) {}

    if (Object.keys(found).length > 0) {
        return { instances: Object.values(found), method: 'livedata' };
    }

    // ── Strategy 3: Fallback heap scan ─────────────────────────────────────
    var seen = {};
    try {
        Java.choose(className, {
            onMatch: function(instance) {
                var id = instance.$handle ? instance.$handle.toString() : instance.hashCode().toString();
                if (!seen[id]) {
                    seen[id] = true;
                    found[id] = { id: id, handle: instance.$handle ? instance.$handle.toString() : "", instance: instance };
                }
            },
            onComplete: function() {}
        });
    } catch(e) {}

    return { instances: Object.values(found), method: 'heap_scan' };
}

rpc.exports = {
    listclasses: function(searchParam) {
        var classes = [];
        var lowercaseSearch = searchParam ? searchParam.toLowerCase() : "";
        Java.perform(function() {
            Java.enumerateLoadedClasses({
                onMatch: function(className) {
                    if (!lowercaseSearch || className.toLowerCase().includes(lowercaseSearch)) {
                        classes.push(className);
                    }
                },
                onComplete: function() {}
            });
        });
        return classes;
    },
    
    getpackagename: function() {
        var pkgName = "";
        try {
            Java.perform(function() {
                var ActivityThread = Java.use('android.app.ActivityThread');
                var app = ActivityThread.currentApplication();
                if (app != null) {
                    pkgName = app.getPackageName();
                }
            });
        } catch (e) {
            // graceful fallback
        }
        return pkgName;
    },
    
    inspectclass: function(className) {
        var staticAttributes = [];
        var instanceAttributes = [];
        var methods = [];
        try {
            Java.perform(function() {
                var clazz = Java.use(className);
                var classDef = clazz.class;
                
                var Modifier = Java.use("java.lang.reflect.Modifier");
                var fields = classDef.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    if (Modifier.isStatic(f.getModifiers())) {
                        staticAttributes.push(f.toString());
                    } else {
                        instanceAttributes.push(f.toString());
                    }
                }
                
                var funcs = classDef.getDeclaredMethods();
                for (var j = 0; j < funcs.length; j++) {
                    methods.push(funcs[j].toString());
                }
            });
            return { staticAttributes: staticAttributes, instanceAttributes: instanceAttributes, methods: methods };
        } catch (e) {
            // Using error field to communicate exceptions back to RPC
            return { error: e.toString(), staticAttributes: [], instanceAttributes: [], methods: [] };
        }
    },

    countinstances: function(className) {
        try {
            var result;
            Java.perform(function() {
                result = resolveActiveInstances(className);
            });
            return result ? result.instances.length : 0;
        } catch(e) {
            return -1;
        }
    },

    listinstances: function(className) {
        try {
            var instances = [];
            var method = 'heap_scan';
            Java.perform(function() {
                var result = resolveActiveInstances(className);
                method = result.method;
                result.instances.forEach(function(item) {
                    if (instances.length < 1000) {
                        instanceCache[item.id] = item.instance;
                        instances.push({
                            id: item.id,
                            handle: item.handle,
                            summary: item.instance.toString() + " (" + getInstanceStatus(item.instance) + ")",
                            detectionMethod: method
                        });
                    }
                });
            });
            return { instances: instances, totalCount: instances.length, detectionMethod: method };
        } catch(e) {
            return { error: e.toString(), instances: [], totalCount: 0, detectionMethod: 'error' };
        }
    },

    inspectinstance: function(className, id, offset, limit) {
        var attributes = [];
        offset = offset || 0;
        limit = limit || 50;
        try {
            Java.perform(function() {
                var instance = instanceCache[id];
                if (!instance) {
                    throw new Error("Instance not found in cache.");
                }
                
                // Use the actual runtime class of the instance instead of the passed className
                var actualClassName = instance.getClass().getName();
                var clazz = Java.use(actualClassName);
                var classDef = clazz.class;
                
                // 1. Detect if it's a Map, Collection, or Array
                var isMap = false;
                var isCollection = false;
                var isArray = actualClassName.startsWith('[');
                
                try {
                    var Map = Java.use("java.util.Map");
                    var Collection = Java.use("java.util.Collection");
                    if (Map.class.isInstance(instance)) isMap = true;
                    if (Collection.class.isInstance(instance)) isCollection = true;
                } catch(e) {}

                // 2. If it's a collection-like thing, show its items first
                if (isMap) {
                    var entrySet = Java.cast(instance, Java.use("java.util.Map")).entrySet().iterator();
                    var skipped = 0;
                    while (entrySet.hasNext() && skipped < offset) {
                        entrySet.next();
                        skipped++;
                    }
                    var count = 0;
                    while (entrySet.hasNext() && count < limit) {
                        var entry = Java.cast(entrySet.next(), Java.use("java.util.Map$Entry"));
                        var key = entry.getKey();
                        var val = entry.getValue();
                        var keyStr = key !== null ? key.toString() : "null";
                        var valStr = val !== null ? javaToString(val) : "null";
                        
                        var childId = null;
                        var childClassName = null;
                        if (val !== null && typeof val === 'object') {
                            childId = val.hashCode().toString();
                            instanceCache[childId] = val;
                            try { childClassName = val.getClass().getName(); } catch(e) {}
                        }
                        
                        attributes.push({ 
                            name: keyStr, 
                            type: childClassName || "MapEntry", 
                            value: valStr, 
                            childId: childId, 
                            childClassName: childClassName,
                            isPagination: false
                        });
                        count++;
                    }
                    if (entrySet.hasNext()) {
                         attributes.push({ 
                             name: "...", 
                             type: "Action", 
                             value: "Show more items", 
                             childId: id, // Pass the same instance ID to expand more
                             isPagination: true,
                             nextOffset: offset + limit
                         });
                    }
                } else if (isCollection) {
                    var iterator = Java.cast(instance, Java.use("java.util.Collection")).iterator();
                    var skipped = 0;
                    while (iterator.hasNext() && skipped < offset) {
                        iterator.next();
                        skipped++;
                    }
                    var count = 0;
                    while (iterator.hasNext() && count < limit) {
                        var val = iterator.next();
                        var name = "[" + (offset + count) + "]";
                        var valStr = val !== null ? javaToString(val) : "null";
                        
                        var childId = null;
                        var childClassName = null;
                        if (val !== null && typeof val === 'object') {
                            childId = val.hashCode().toString();
                            instanceCache[childId] = val;
                            try { childClassName = val.getClass().getName(); } catch(e) {}
                        }
                        
                        attributes.push({ 
                            name: name, 
                            type: childClassName || "Element", 
                            value: valStr, 
                            childId: childId, 
                            childClassName: childClassName,
                            isPagination: false
                        });
                        count++;
                    }
                    if (iterator.hasNext()) {
                        attributes.push({ 
                            name: "...", 
                            type: "Action", 
                            value: "Show more items", 
                            childId: id, 
                            isPagination: true,
                            nextOffset: offset + limit
                        });
                    }
                } else if (isArray) {
                    var len = Java.use("java.lang.reflect.Array").getLength(instance);
                    var end = Math.min(len, offset + limit);
                    for (var i = offset; i < end; i++) {
                        var val = Java.use("java.lang.reflect.Array").get(instance, i);
                        var name = "[" + i + "]";
                        var valStr = val !== null ? javaToString(val) : "null";
                        
                        var childId = null;
                        var childClassName = null;
                        if (val !== null && typeof val === 'object') {
                            childId = val.hashCode().toString();
                            instanceCache[childId] = val;
                            try { childClassName = val.getClass().getName(); } catch(e) {}
                        }
                        
                        attributes.push({ 
                            name: name, 
                            type: childClassName || "Element", 
                            value: valStr, 
                            childId: childId, 
                            childClassName: childClassName,
                            isPagination: false
                        });
                    }
                    if (len > end) {
                         attributes.push({ 
                             name: "...", 
                             type: "Action", 
                             value: "Show more items", 
                             childId: id, 
                             isPagination: true,
                             nextOffset: offset + limit
                         });
                    }
                }

                // 3. Only show regular fields on the FIRST page (offset 0)
                if (offset === 0) {
                    var fields = classDef.getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        var field = fields[i];
                        field.setAccessible(true);
                        var name = field.getName();
                        var type = field.getType().getSimpleName();
                        var valStr = "unknown";
                        var childId = null;
                        var childClassName = null;
                        try {
                            var fieldVal = field.get(instance);
                            if (fieldVal !== null) {
                                valStr = javaToString(fieldVal);
                                var isBasicType = ["int", "long", "boolean", "byte", "short", "float", "double", "string", "charsequence", "char"].indexOf(type.toLowerCase()) !== -1;
                                if (!isBasicType) {
                                    childId = fieldVal.hashCode().toString();
                                    instanceCache[childId] = fieldVal;
                                    try {
                                        childClassName = fieldVal.getClass().getName();
                                    } catch(e) {
                                      childClassName = field.getType().getName();
                                    }
                                }
                            } else {
                               valStr = "null";
                            }
                        } catch(fe) {
                            valStr = "error";
                        }
                        attributes.push({ 
                            name: name, 
                            type: childClassName || type, 
                            value: valStr, 
                            childId: childId, 
                            childClassName: childClassName,
                            isPagination: false
                        });
                    }
                }
            });
            return { attributes: attributes };
        } catch (e) {
            return { error: e.toString(), attributes: [] };
        }
    },

    hookmethod: function(className, methodSig) {
        Java.perform(function() {
            try {
                var targetClass = Java.use(className);
                var beforeArgs = methodSig.split('(')[0].trim();
                var parts = beforeArgs.split(' ');
                var fullMethodPath = parts[parts.length - 1];
                var dotParts = fullMethodPath.split('.');
                var methodName = dotParts[dotParts.length - 1];
                
                if (targetClass[methodName] && targetClass[methodName].overloads) {
                    if (activeHookImplementations[className + methodSig]) {
                        return;
                    }
                    var overload = targetClass[methodName].overloads[0]; 

                    activeHookImplementations[className + methodSig] = overload.implementation;
                    overload.implementation = function() {
                        var args = {};
                        for (var i = 0; i < arguments.length; i++) {
                            args["arg" + i] = javaToString(arguments[i]);
                        }
                        var ret = overload.apply(this, arguments);
                        var retStr = (ret === undefined) ? "void" : javaToString(ret);

                        hookEvents.push({
                            timestamp: Date.now(),
                            target: { className: className, memberSignature: methodSig, type: "METHOD" },
                            data: { args: JSON.stringify(args), "return": retStr }
                        });
                        return ret;
                    };
                } else {
                    // It's probably a field if it's not a method
                    if (monitoredFields[className + methodSig]) return;
                    
                    var isStaticField = false;
                    try {
                        var clazz = Java.use(className);
                        var currentClass = clazz.class;
                        var field = null;
                        var Modifier = Java.use("java.lang.reflect.Modifier");

                        // Iterate hierarchy to find the field and check if it's static
                        while (currentClass !== null) {
                            try {
                                field = currentClass.getDeclaredField(methodName);
                                break;
                            } catch (e) {
                                currentClass = currentClass.getSuperclass();
                            }
                        }

                        if (field !== null) {
                            field.setAccessible(true);
                            if (Modifier.isStatic(field.getModifiers())) {
                                isStaticField = true;
                            }
                        }
                    } catch (e) {
                        console.error("Error checking field " + methodName + " on " + className + ": " + e);
                    }

                    if (!isStaticField) {
                        console.warn("Field " + methodName + " is not static or not found. Instance field hooking is not supported yet.");
                        // Send an event to the UI so the user knows why it's not working
                        hookEvents.push({
                            timestamp: Date.now(),
                            target: { className: className, memberSignature: methodSig, type: "FIELD" },
                            data: { value: "[Error] Not a static field" }
                        });
                        return;
                    }

                    monitoredFields[className + methodSig] = {
                        className: className,
                        fieldName: methodName,
                        signature: methodSig,
                        lastValue: undefined
                    };

                    // Send an initial event to confirm hook is active
                    hookEvents.push({
                        timestamp: Date.now(),
                        target: { className: className, memberSignature: methodSig, type: "FIELD" },
                        data: { value: "Watching for changes..." }
                    });
                    
                    if (!fieldPollingInterval) {
                        fieldPollingInterval = setInterval(function() {
                            Java.perform(function() {
                                for (var key in monitoredFields) {
                                    var info = monitoredFields[key];
                                    try {
                                        var clazz = Java.use(info.className);
                                        var currentClass = clazz.class;
                                        var field = null;
                                        while (currentClass !== null) {
                                            try {
                                                field = currentClass.getDeclaredField(info.fieldName);
                                                break;
                                            } catch (e) {
                                                currentClass = currentClass.getSuperclass();
                                            }
                                        }
                                        if (field) {
                                            field.setAccessible(true);
                                            var val = field.get(null);
                                            var valStr = javaToString(val);
                                            
                                            if (info.lastValue === undefined) {
                                                info.lastValue = valStr;
                                            } else if (valStr !== info.lastValue) {
                                                var oldVal = info.lastValue;
                                                info.lastValue = valStr;
                                                hookEvents.push({
                                                    timestamp: Date.now(),
                                                    target: { className: info.className, memberSignature: info.signature, type: "FIELD" },
                                                    data: { value: oldVal + " | " + valStr }
                                                });
                                            }
                                        }
                                    } catch (e) {}
                                }
                            });
                        }, 250);
                    }
                }
            } catch (e) {
                console.error("Hook failed: " + e);
            }
        });
        return true;
    },
    unhookmethod: function(className, methodSig) {
        Java.perform(function() {
            try {
                var targetClass = Java.use(className);
                var beforeArgs = methodSig.split('(')[0].trim();
                var parts = beforeArgs.split(' ');
                var fullMethodPath = parts[parts.length - 1];
                var dotParts = fullMethodPath.split('.');
                var methodName = dotParts[dotParts.length - 1];
                
                if (targetClass[methodName].overloads) {
                    if (activeHookImplementations.hasOwnProperty(className + methodSig)) {
                        targetClass[methodName].overloads[0].implementation = null;
                        delete activeHookImplementations[className + methodSig];
                    }
                } else {
                    delete monitoredFields[className + methodSig];
                    if (Object.keys(monitoredFields).length === 0 && fieldPollingInterval) {
                        clearInterval(fieldPollingInterval);
                        fieldPollingInterval = null;
                    }
                }
            } catch (e) {
                console.error("Unhook failed: " + e);
            }
        });
        return true;
    },
    gethookevents: function() {
        var events = hookEvents;
        hookEvents = [];
        return events;
    },

    setmethodimplementation: function(className, methodSig, code) {
        Java.perform(function() {
            try {
                var targetClass = Java.use(className);
                var beforeArgs = methodSig.split('(')[0].trim();
                var parts = beforeArgs.split(' ');
                var fullMethodPath = parts[parts.length - 1];
                var dotParts = fullMethodPath.split('.');
                var methodName = dotParts[dotParts.length - 1];
                
                if (targetClass[methodName] && targetClass[methodName].overloads) {
                    var overload = targetClass[methodName].overloads[0]; 

                    // Store original implementation if not already stored
                    if (!activeHookImplementations[className + methodSig]) {
                        activeHookImplementations[className + methodSig] = overload.implementation;
                    }

                    // Create the user function from the provided code
                    // The code should be a function body that takes (context)
                    var userFn = new Function('context', code);

                    overload.implementation = function() {
                        var self = this;
                        var args = Array.from(arguments);
                        var context = {
                            Java: Java,
                            args: args,
                            original: function() {
                                return overload.apply(self, args);
                            }
                        };

                        try {
                            return userFn(context);
                        } catch (e) {
                            console.error("Error in custom implementation for " + methodSig + ": " + e);
                            // Fallback to original implementation on error
                            return overload.apply(self, args);
                        }
                    };
                }
            } catch (e) {
                console.error("Set custom implementation failed: " + e);
            }
        });
        return true;
    },

    setfieldvalue: function(className, id, fieldName, type, newValue) {
        try {
            return Java.perform(function() {
                var instance = instanceCache[id];
                if (!instance) {
                    throw new Error("Instance not found in cache.");
                }
                
                var actualClassName = instance.getClass().getName();
                var clazz = Java.use(actualClassName);
                var classDef = clazz.class;
                
                var field = classDef.getDeclaredField(fieldName);
                field.setAccessible(true);
                
                var t = type.toLowerCase();
                var val = null;
                if (t === "boolean" || t === "bool") val = Java.use("java.lang.Boolean").valueOf(newValue === "true");
                else if (t === "int") val = Java.use("java.lang.Integer").valueOf(parseInt(newValue, 10));
                else if (t === "long") val = Java.use("java.lang.Long").valueOf(parseInt(newValue, 10));
                else if (t === "float") val = Java.use("java.lang.Float").valueOf(parseFloat(newValue));
                else if (t === "double") val = Java.use("java.lang.Double").valueOf(parseFloat(newValue));
                else if (t === "short") val = Java.use("java.lang.Short").valueOf(parseInt(newValue, 10));
                else if (t === "byte") val = Java.use("java.lang.Byte").valueOf(parseInt(newValue, 10));
                else if (t === "char") val = Java.use("java.lang.Character").valueOf(newValue.charAt(0));
                else if (t === "string" || t === "charsequence") val = Java.use("java.lang.String").$new(newValue);
                else throw new Error("Unsupported type for editing");
                
                field.set(instance, val);
                return true;
            });
        } catch (e) {
            return { error: e.toString() };
        }
    }
};
