package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Hook;
import com.github.unidbg.arm.ArmHook;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.hook.HookListener;
import com.github.unidbg.memory.SvcMemory;
import com.sun.jna.Pointer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SystemPropertyHook implements HookListener {

    private static final Log log = LogFactory.getLog(SystemPropertyHook.class);

    private static final int PROP_VALUE_MAX = 92;

    private final Emulator<?> emulator;

    public SystemPropertyHook(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    @Override
    public long hook(SvcMemory svcMemory, String libraryName, String symbolName, final long old) {
        if ("libc.so".equals(libraryName)) {
            if ("__system_property_get".equals(symbolName)) {
                if (log.isDebugEnabled()) {
                    log.debug("Hook " + symbolName);
                }
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            int index = 0;
                            Pointer pointer = context.getPointerArg(index);
                            String key = pointer.getString(0);
                            return __system_property_get(old, key, index);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            int index = 0;
                            Pointer pointer = context.getPointerArg(index);
                            String key = pointer.getString(0);
                            return __system_property_get(old, key, index);
                        }
                    }).peer;
                }
            }
            if ("__system_property_read".equals(symbolName)) {
                if (log.isDebugEnabled()) {
                    log.debug("Hook " + symbolName);
                }
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer pi = context.getPointerArg(0);
                            String key = pi.share(PROP_VALUE_MAX + 4).getString(0);
                            return __system_property_get(old, key, 1);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer pi = context.getPointerArg(0);
                            String key = pi.share(PROP_VALUE_MAX + 4).getString(0);
                            return __system_property_get(old, key, 1);
                        }
                    }).peer;
                }
            }
            if ("__system_property_find".equals(symbolName)) {
                if (log.isDebugEnabled()) {
                    log.debug("Hook " + symbolName);
                }
                if (emulator.is64Bit()) {
                    return svcMemory.registerSvc(new Arm64Hook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer name = context.getPointerArg(0);
                            if (log.isDebugEnabled()) {
                                log.debug("__system_property_find key=" + name.getString(0));
                            }
                            return HookStatus.RET(emulator, old);
                        }
                    }).peer;
                } else {
                    return svcMemory.registerSvc(new ArmHook() {
                        @Override
                        protected HookStatus hook(Emulator<?> emulator) {
                            RegisterContext context = emulator.getContext();
                            Pointer name = context.getPointerArg(0);
                            if (log.isDebugEnabled()) {
                                log.debug("__system_property_find key=" + name.getString(0));
                            }
                            return HookStatus.RET(emulator, old);
                        }
                    }).peer;
                }
            }
        }
        return 0;
    }

    private HookStatus __system_property_get(long old, String key, int index) {
        RegisterContext context = emulator.getContext();
        if (propertyProvider != null) {
            String value = propertyProvider.getProperty(key);
            if (value != null) {
                if (log.isDebugEnabled()) {
                    log.debug("__system_property_get key=" + key + ", value=" + value);
                }

                byte[] data = value.getBytes(StandardCharsets.UTF_8);
                if (data.length >= PROP_VALUE_MAX) {
                    throw new BackendException("invalid property value length: key=" + key + ", value=" + value);
                }

                context.getPointerArg(index + 1).write(0, Arrays.copyOf(data, data.length + 1), 0, data.length + 1);
                return HookStatus.LR(emulator, data.length);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("__system_property_get key=" + key);
        }
        return HookStatus.RET(emulator, old);
    }

    private SystemPropertyProvider propertyProvider;

    public void setPropertyProvider(SystemPropertyProvider propertyProvider) {
        this.propertyProvider = propertyProvider;
    }

}
