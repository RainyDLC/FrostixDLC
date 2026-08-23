package ru.white.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Песочница Lua для пользовательских скриптов.
 *
 * Принцип защиты: скрипт получает окружение-копию, в котором есть ТОЛЬКО
 * выверенный белый список стандартных библиотек и API клиента. Никаких
 * живых Java-объектов, никакого luajava/reflection/ClassLoader — скрипт
 * физически не может добраться до классов клиента и дампнуть байткод.
 *
 * Вырезано полностью:
 *  - io / os / package(require) / coroutine / debug
 *  - luajava (биндинги к произвольным классам JVM)
 *  - load / loadstring / dofile / loadfile (загрузка кода и байткода)
 *  - setfenv / getfenv (подмена окружений), rawset (обход прокси)
 *  - string.dump (сериализация функций)
 *
 * Стандартные таблицы (string/table/math/bit32) выдаются через прокси
 * «только чтение»: один скрипт не может подменить string.len другому.
 */
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

        // шаблон окружения: защищённые библиотеки + безопасные базовые функции
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

    /** Свежее изолированное окружение для одного скрипта. */
    static synchronized LuaTable newEnv() {
        ensureBuilt();
        LuaTable env = new LuaTable();
        for (LuaValue key : stdlibTemplate.keys()) {
            env.set(key, stdlibTemplate.get(key));
        }
        env.set("api", readonlyProxy(LuaApi.build()));
        return env;
    }

    /** Глобалсы только ради компилятора — для проверки синтаксиса. */
    static synchronized Globals compilerGlobals() {
        ensureBuilt();
        return sharedGlobals;
    }

    /** Прокси «только чтение» поверх реальной таблицы. */
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
