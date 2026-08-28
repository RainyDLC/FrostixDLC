package ru.white.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

public final class LuaSandbox {
    private static final String[] BANNED_GLOBALS = {
            "load", "loadstring", "dofile", "loadfile", "require",
            "setfenv", "getfenv", "rawset", "newproxy"
    };
    private static final String[] BANNED_TABLES = {
            "io", "os", "package", "luajava", "debug", "coroutine"
    };

    private static Globals sharedGlobals;
    private static LuaTable stdlibTemplate;

    private LuaSandbox() {
    }

    private static synchronized void ensureBuilt() {
        if (sharedGlobals != null) return;

        Globals g = JsePlatform.standardGlobals();

        for (String name : BANNED_GLOBALS) g.set(name, LuaValue.NIL);
        for (String name : BANNED_TABLES) g.set(name, LuaValue.NIL);
        LuaValue stringTable = g.get("string");
        if (stringTable.istable()) stringTable.set("dump", LuaValue.NIL);

        LuaTable template = new LuaTable();
        for (String lib : new String[]{"string", "table", "math"}) {
            template.set(lib, readonlyProxy(g.get(lib).checktable()));
        }
        for (String fn : new String[]{
                "assert", "error", "getmetatable", "ipairs", "next", "pairs", "pcall",
                "rawequal", "rawget", "rawlen", "select", "setmetatable", "tonumber",
                "tostring", "type", "unpack", "xpcall"}) {
            template.set(fn, g.get(fn));
        }
        template.set("_VERSION", g.get("_VERSION"));

        sharedGlobals = g;
        stdlibTemplate = template;
    }

    static synchronized LuaTable newEnv() {
        ensureBuilt();
        LuaTable env = new LuaTable();
        for (LuaValue key : stdlibTemplate.keys()) {
            env.set(key, stdlibTemplate.get(key));
        }
        env.set("api", readonlyProxy(LuaApi.build()));
        return env;
    }

    static synchronized Globals compilerGlobals() {
        ensureBuilt();
        return sharedGlobals;
    }

    static LuaTable readonlyProxy(LuaTable real) {
        LuaTable mt = new LuaTable();
        mt.set(LuaValue.valueOf("__index"), real);
        mt.set(LuaValue.valueOf("__newindex"), new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key, LuaValue value) {
                return LuaValue.error("sandbox: изменение системной таблицы запрещено");
            }
        });
        LuaTable proxy = new LuaTable();
        proxy.setmetatable(mt);
        return proxy;
    }
}
