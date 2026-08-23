package ru.white.script;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.ChatUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Динамический модуль, управляемый Lua-скриптом.
 *
 * Скрипт исполняется один раз при загрузке в изолированном окружении
 * {@link LuaSandbox} и объявляет колбэки:
 * <pre>
 *   module.name = "Имя"
 *   module.desc = "Описание"
 *   local boost = module.setting_slider("Буст", 2, 0, 10, 0.5)
 *
 *   function on_enable()  end
 *   function on_disable() end
 *   function on_tick()    end
 *   function on_render(delta) end
 * </pre>
 * Ошибка внутри скрипта автоматически выключает модуль и пишет в чат.
 */
@ModuleInfo(name = "Lua", desc = "Пользовательский скрипт", category = Category.OTHER)
public class LuaModule extends Module {

    private final Path file;
    private final Category scriptCategory;
    private LuaTable env;
    private boolean broken;

    public LuaModule(Path file, Category category) {
        super(true);
        this.file = file;
        this.scriptCategory = category;
        setName("Lua");
        setBigName("Lua");
        setDesc("Скрипт не выполнен");
        setAllowDisable(true);
    }

    /** Файл, из которого загружен скрипт. */
    public Path getFile() {
        return file;
    }

    /** Компилирует и выполняет скрипт; бросает LuaError при ошибке синтаксиса. */
    public void initialize(String code) throws LuaError {
        LuaTable scriptEnv = LuaSandbox.newEnv();
        scriptEnv.set("module", buildModuleTable());

        // окружение скрипта передаётся напрямую в компилятор (_ENV):
        // глобалы скрипта живут только в его собственной таблице
        LuaValue compiled = LuaSandbox.compilerGlobals().load(code, "=" + safeChunkName(), scriptEnv);
        compiled.call();

        this.env = scriptEnv;

        String n = str(scriptEnv.get("module").get("name"), "");
        if (!n.isBlank()) {
            setName(n.trim().replaceAll(" ", ""));
            setBigName(n.trim());
        }
        String d = str(scriptEnv.get("module").get("desc"), "");
        if (!d.isBlank()) setDesc(d);

        setCategory(scriptCategory);
    }

    private LuaTable buildModuleTable() {
        LuaTable mod = new LuaTable();
        mod.set("name", LuaValue.valueOf(""));
        mod.set("desc", LuaValue.valueOf(""));

        mod.set("setting_toggle", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue def) {
                BooleanSetting s = new BooleanSetting(LuaModule.this, name.tojstring(), def.optboolean(false));
                return new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        return LuaValue.valueOf(s.getValue());
                    }
                };
            }
        });

        mod.set("setting_slider", new VarArgFunction() {
            @Override
            public LuaValue invoke(Varargs args) {
                SliderSetting s = new SliderSetting(LuaModule.this,
                        args.arg(1).tojstring(),
                        (float) args.arg(2).optdouble(0),
                        (float) args.arg(3).optdouble(0),
                        (float) args.arg(4).optdouble(10),
                        Math.max(0.01F, (float) args.arg(5).optdouble(1)));
                return new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        return LuaValue.valueOf(s.getValue());
                    }
                };
            }
        });

        // module.setting_mode("Режим", "Обычный", {"Обычный", "Агрессивный"})
        mod.set("setting_mode", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue def, LuaValue variants) {
                List<String> vals = new ArrayList<>();
                if (variants.istable()) {
                    for (int i = 1; i <= variants.length(); i++) {
                        LuaValue v = variants.get(i);
                        if (v.isstring()) vals.add(v.tojstring());
                    }
                }
                String d = def.isstring() ? def.tojstring() : "Значение";
                if (vals.isEmpty()) vals.add(d);
                // первый вариант в ModeSetting — значение по умолчанию
                List<String> ordered = new ArrayList<>();
                ordered.add(vals.contains(d) ? d : vals.get(0));
                for (String v : vals) if (!ordered.contains(v)) ordered.add(v);
                ModeSetting s = new ModeSetting(LuaModule.this, name.tojstring(),
                        ordered.toArray(new String[0]));
                return new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        return LuaValue.valueOf(s.getValue());
                    }
                };
            }
        });

        // module.setting_color("Цвет", 0xFF00FFFF)
        mod.set("setting_color", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue def) {
                ColorSetting s = new ColorSetting(LuaModule.this, name.tojstring(),
                        (int) def.optlong(0xFFFFFFFFL));
                return new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        return LuaValue.valueOf((double) (s.getValue() & 0xFFFFFFFFL));
                    }
                };
            }
        });

        return mod;
    }

    // ── события клиента → колбэки скрипта ──

    @EventHandler
    private void onClientTick(EventTick event) {
        invoke("on_tick");
    }

    @EventHandler
    private void onWorldRender(EventRender3D event) {
        invoke("on_render", LuaValue.valueOf(event.getTickDelta()));
    }

    @Override
    protected void onEnable() {
        broken = false;
        invoke("on_enable");
    }

    @Override
    protected void onDisable() {
        invoke("on_disable");
    }

    private void invoke(String fn, LuaValue... args) {
        if (broken || env == null) return;
        // колбэки тикают только у включённого модуля (кроме финального on_disable)
        if (!"on_disable".equals(fn) && !isEnabled()) return;
        LuaValue f = env.get(fn);
        if (!f.isfunction()) return;
        try {
            if (args != null && args.length > 0) f.call(args[0]);
            else f.call();
        } catch (LuaError e) {
            fail(e);
        } catch (Exception e) {
            fail(new LuaError(e));
        }
    }

    /** Ошибка скрипта: модуль выключается, причина — в чат. */
    private void fail(LuaError e) {
        if (broken) return;
        broken = true;
        super.setEnabled(false, false);
        String msg = String.valueOf(e.getMessage());
        ChatUtils.addChatMessage("§cLua «" + getBigName() + "» отключён: "
                + msg.substring(0, Math.min(120, msg.length())));
    }

    private static String str(LuaValue v, String def) {
        return v.isstring() ? v.tojstring() : def;
    }

    private String safeChunkName() {
        String n = file != null && file.getFileName() != null ? file.getFileName().toString() : "script";
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '_' ? c : '_');
        return sb.toString();
    }
}
